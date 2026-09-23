package net.servertools.smptools;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmpTools implements ModInitializer {
	public static final String MOD_ID = "smptools";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static SmpConfig config;

	public static SmpConfig config() {
		return config;
	}

	@Override
	public void onInitialize() {
		config = SmpConfig.load();

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> Remover.onEntityLoad(entity));
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(Remover::onBlockEntityLoad);
		ServerTickEvents.END_SERVER_TICK.register(server -> Remover.processQueues());

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				SmpCommands.register(dispatcher));

		for (Rule rule : Rule.values()) {
			if (config.isOn(rule)) LOGGER.info("[smp] {} is ON: removing {}", rule.command, rule.label);
		}
	}
}
