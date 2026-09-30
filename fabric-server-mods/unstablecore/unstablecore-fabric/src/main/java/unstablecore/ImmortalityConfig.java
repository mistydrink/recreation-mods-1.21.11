package unstablecore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Settings from the plugin's "immortality" section. Stored in config/unstablecore/immortality.json. */
public class ImmortalityConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Health an immortal player is held at instead of dying (0.5 = a quarter of a heart). */
	public float minHealth = 0.5f;

	/** If true, an immortal player holding a totem in either hand dies normally, so the totem pops. */
	public boolean smartTotem = true;

	public static ImmortalityConfig load(Path file) {
		ImmortalityConfig config = null;
		try {
			if (Files.exists(file)) {
				config = GSON.fromJson(Files.readString(file), ImmortalityConfig.class);
			}
		} catch (Exception e) {
			UnstableCore.LOGGER.error("Could not read {}, using defaults", file, e);
		}
		if (config == null) {
			config = new ImmortalityConfig();
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(config));
		} catch (IOException e) {
			UnstableCore.LOGGER.error("Could not write {}", file, e);
		}
		return config;
	}
}
