package net.servertools.nickskins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything lives in config/nickskins/:
 *   config.json - settings
 *   skins.json  - the skin pool (signed textures)
 *   data.json   - who currently has which nick/skin, plus saved groups
 */
public final class Storage {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Type SKIN_LIST = new TypeToken<List<SkinEntry>>() {}.getType();

	private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("nickskins");
	private final Path configPath = dir.resolve("config.json");
	private final Path skinsPath = dir.resolve("skins.json");
	private final Path dataPath = dir.resolve("data.json");

	private final Map<UUID, PlayerOverride> overrides = new ConcurrentHashMap<>();
	/** group name (lowercase) -> member UUID -> last known name */
	private final Map<String, Map<UUID, String>> groups = new ConcurrentHashMap<>();
	private volatile List<SkinEntry> skinPool = List.of();
	private NickConfig config = new NickConfig();

	static final class DataFile {
		Map<String, PlayerOverride> players = new LinkedHashMap<>();
		Map<String, Map<String, String>> groups = new LinkedHashMap<>();
	}

	// ------------------------------------------------------------------ loading / saving

	public void loadAll() {
		loadConfigAndSkins();
		DataFile data = read(dataPath, DataFile.class);
		overrides.clear();
		groups.clear();
		if (data != null) {
			if (data.players != null) {
				data.players.forEach((id, o) -> {
					if (o == null || o.isEmpty()) return;
					if (o.realName == null) o.realName = id;
					overrides.put(UUID.fromString(id), o);
				});
			}
			if (data.groups != null) {
				data.groups.forEach((name, members) -> {
					Map<UUID, String> m = new LinkedHashMap<>();
					if (members != null) members.forEach((id, n) -> m.put(UUID.fromString(id), n));
					groups.put(name.toLowerCase(Locale.ROOT), m);
				});
			}
		}
		saveData();
	}

	/** Safe to call at any time (/nickskins reload). Does not touch active nicks/skins. */
	public void loadConfigAndSkins() {
		NickConfig loadedConfig = read(configPath, NickConfig.class);
		config = loadedConfig != null ? loadedConfig : new NickConfig();
		write(configPath, config);

		List<SkinEntry> loadedSkins = read(skinsPath, SKIN_LIST);
		List<SkinEntry> valid = new ArrayList<>();
		if (loadedSkins != null) {
			for (SkinEntry s : loadedSkins) {
				if (s != null && s.isValid()) valid.add(s);
				else NickSkins.LOGGER.warn("Skipping a skins.json entry without name/value/signature");
			}
		}
		skinPool = List.copyOf(valid);
		if (!Files.exists(skinsPath)) saveSkins();
	}

	public void saveData() {
		DataFile data = new DataFile();
		overrides.forEach((id, o) -> data.players.put(id.toString(), o));
		groups.forEach((name, members) -> {
			Map<String, String> m = new LinkedHashMap<>();
			members.forEach((id, n) -> m.put(id.toString(), n));
			data.groups.put(name, m);
		});
		write(dataPath, data);
	}

	public void saveSkins() {
		write(skinsPath, new ArrayList<>(skinPool));
	}

	private <T> @Nullable T read(Path path, Type type) {
		if (!Files.exists(path)) return null;
		try (Reader reader = Files.newBufferedReader(path)) {
			return GSON.fromJson(reader, type);
		} catch (Exception e) {
			NickSkins.LOGGER.error("Could not read {}", path, e);
			return null;
		}
	}

	private void write(Path path, Object value) {
		try {
			Files.createDirectories(dir);
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(value, writer);
			}
		} catch (Exception e) {
			NickSkins.LOGGER.error("Could not write {}", path, e);
		}
	}

	public NickConfig config() {
		return config;
	}

	// ------------------------------------------------------------------ nicknames & skins

	public @Nullable String nickOf(UUID uuid) {
		PlayerOverride o = overrides.get(uuid);
		return o == null ? null : o.nick;
	}

	public Map<UUID, PlayerOverride> overrides() {
		return overrides;
	}

	public void setNick(UUID uuid, String realName, @Nullable String nick) {
		PlayerOverride o = overrides.computeIfAbsent(uuid, id -> new PlayerOverride(realName));
		o.realName = realName;
		o.nick = nick;
		if (o.isEmpty()) overrides.remove(uuid);
	}

	public void setSkin(UUID uuid, String realName, @Nullable SkinEntry skin) {
		PlayerOverride o = overrides.computeIfAbsent(uuid, id -> new PlayerOverride(realName));
		o.realName = realName;
		o.skin = skin;
		if (o.isEmpty()) overrides.remove(uuid);
	}

	/** Keeps stored real names current when players rename their account. */
	public void updateRealName(UUID uuid, String realName) {
		PlayerOverride o = overrides.get(uuid);
		if (o != null && !realName.equals(o.realName)) {
			o.realName = realName;
			saveData();
		}
	}

	/**
	 * Called for every player-list entry the server builds. Returns the profile other clients
	 * should see: nick as the name, pool skin as the textures. Untouched players pass through.
	 */
	public GameProfile rewriteProfile(GameProfile original) {
		PlayerOverride o = overrides.get(original.getId());
		if (o == null || o.isEmpty()) return original;

		GameProfile copy = new GameProfile(original.getId(), o.nick != null ? o.nick : original.getName());
		copy.getProperties().putAll(original.getProperties());
		if (o.skin != null) {
			copy.getProperties().removeAll("textures");
			copy.getProperties().put("textures", new Property("textures", o.skin.value, o.skin.signature));
		}
		return copy;
	}

	// ------------------------------------------------------------------ skin pool

	public List<SkinEntry> skinPool() {
		return skinPool;
	}

	public @Nullable SkinEntry findSkin(String name) {
		for (SkinEntry s : skinPool) {
			if (s.name.equalsIgnoreCase(name)) return s;
		}
		return null;
	}

	/** Adds or replaces (by name) a skin in the pool and saves skins.json. */
	public void putSkin(SkinEntry skin) {
		List<SkinEntry> next = new ArrayList<>(skinPool);
		next.removeIf(s -> s.name.equalsIgnoreCase(skin.name));
		next.add(skin);
		skinPool = List.copyOf(next);
		saveSkins();
	}

	public boolean removeSkin(String name) {
		List<SkinEntry> next = new ArrayList<>(skinPool);
		boolean removed = next.removeIf(s -> s.name.equalsIgnoreCase(name));
		if (removed) {
			skinPool = List.copyOf(next);
			saveSkins();
		}
		return removed;
	}

	// ------------------------------------------------------------------ groups

	public Map<String, Map<UUID, String>> groups() {
		return groups;
	}

	public @Nullable Map<UUID, String> group(String name) {
		return groups.get(name.toLowerCase(Locale.ROOT));
	}

	public boolean createGroup(String name) {
		boolean created = groups.putIfAbsent(name.toLowerCase(Locale.ROOT), new LinkedHashMap<>()) == null;
		if (created) saveData();
		return created;
	}

	public boolean deleteGroup(String name) {
		boolean removed = groups.remove(name.toLowerCase(Locale.ROOT)) != null;
		if (removed) saveData();
		return removed;
	}
}
