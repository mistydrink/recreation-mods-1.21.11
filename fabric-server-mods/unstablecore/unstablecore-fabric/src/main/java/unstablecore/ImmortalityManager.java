package unstablecore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/** Keeps track of who is immortal and saves it so it survives restarts. */
public class ImmortalityManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Set<UUID> immortalPlayers = new HashSet<>();
	private final Path file;

	public ImmortalityManager(Path file) {
		this.file = file;
	}

	public boolean isImmortal(UUID uuid) {
		return immortalPlayers.contains(uuid);
	}

	public void setImmortal(UUID uuid, boolean value) {
		if (value) {
			immortalPlayers.add(uuid);
		} else {
			immortalPlayers.remove(uuid);
		}
		save();
	}

	public void load() {
		immortalPlayers.clear();
		if (!Files.exists(file)) {
			return;
		}
		try {
			List<String> ids = GSON.fromJson(Files.readString(file), new TypeToken<List<String>>() {}.getType());
			if (ids != null) {
				for (String id : ids) {
					try {
						immortalPlayers.add(UUID.fromString(id));
					} catch (IllegalArgumentException ignored) {
						// skip malformed entries, like the plugin did
					}
				}
			}
		} catch (Exception e) {
			UnstableCore.LOGGER.error("Could not read {}", file, e);
		}
	}

	private void save() {
		List<String> ids = new ArrayList<>();
		for (UUID uuid : immortalPlayers) {
			ids.add(uuid.toString());
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(ids));
		} catch (IOException e) {
			UnstableCore.LOGGER.error("Could not write {}", file, e);
		}
	}
}
