package net.servertools.nickskins;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NickSkins implements ModInitializer {
	public static final String MOD_ID = "nickskins";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Storage STORAGE = new Storage();

	public static Storage storage() {
		return STORAGE;
	}

	@Override
	public void onInitialize() {
		STORAGE.loadAll();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				STORAGE.updateRealName(handler.player.getUuid(), handler.player.getGameProfile().getName()));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				NickSkinsCommands.register(dispatcher));

		LOGGER.info("NickSkins loaded: {} skins in pool, {} active overrides, {} groups",
				STORAGE.skinPool().size(), STORAGE.overrides().size(), STORAGE.groups().size());
	}
}
