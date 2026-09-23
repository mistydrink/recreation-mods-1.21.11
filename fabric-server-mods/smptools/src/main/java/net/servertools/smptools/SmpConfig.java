package net.servertools.smptools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/smptools.json. Rules stay on across restarts until turned off. */
public class SmpConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("smptools.json");

	public boolean removeVillagers = false;
	/** Also remove wandering traders while remove_villagers is on. */
	public boolean alsoRemoveWanderingTraders = false;
	public boolean removeShulkers = false;
	public boolean removeElytras = false;
	public boolean removeOminousVaults = false;

	public boolean isOn(Rule rule) {
		return switch (rule) {
			case VILLAGERS -> removeVillagers;
			case SHULKERS -> removeShulkers;
			case ELYTRAS -> removeElytras;
			case OMINOUS_VAULTS -> removeOminousVaults;
		};
	}

	public void set(Rule rule, boolean on) {
		switch (rule) {
			case VILLAGERS -> removeVillagers = on;
			case SHULKERS -> removeShulkers = on;
			case ELYTRAS -> removeElytras = on;
			case OMINOUS_VAULTS -> removeOminousVaults = on;
		}
		save();
	}

	public static SmpConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				SmpConfig loaded = GSON.fromJson(reader, SmpConfig.class);
				if (loaded != null) {
					loaded.save();
					return loaded;
				}
			} catch (Exception e) {
				SmpTools.LOGGER.error("Could not read {}, using defaults", PATH, e);
			}
		}
		SmpConfig fresh = new SmpConfig();
		fresh.save();
		return fresh;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception e) {
			SmpTools.LOGGER.error("Could not save {}", PATH, e);
		}
	}
}
