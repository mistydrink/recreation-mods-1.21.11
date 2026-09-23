package net.servertools.deathkick;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stored at config/deathkick.json. Edit by hand and run /deathkick reload, or use the commands. */
public class DeathKickConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("deathkick.json");

	public boolean enabled = true;
	public boolean hideJoinMessages = true;
	public boolean hideLeaveMessages = true;

	/** Ticks to wait after death before kicking (20 ticks = 1 second). 0 = same tick. */
	public int kickDelayTicks = 20;

	/** Shown on the disconnect screen. Supports &-color codes and the {death} placeholder. */
	public String kickMessage = "&cYou died!&r\n\n&7{death}";

	/** UUID -> last known name. Players here are never kicked for dying. */
	public Map<String, String> bypass = new LinkedHashMap<>();

	public static DeathKickConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				DeathKickConfig loaded = GSON.fromJson(reader, DeathKickConfig.class);
				if (loaded != null) {
					if (loaded.bypass == null) loaded.bypass = new LinkedHashMap<>();
					loaded.save(); // writes any newly added fields back to disk
					return loaded;
				}
			} catch (Exception e) {
				DeathKick.LOGGER.error("Could not read {}, using defaults", PATH, e);
			}
		}
		DeathKickConfig fresh = new DeathKickConfig();
		fresh.save();
		return fresh;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			DeathKick.LOGGER.error("Could not save {}", PATH, e);
		}
	}
}
