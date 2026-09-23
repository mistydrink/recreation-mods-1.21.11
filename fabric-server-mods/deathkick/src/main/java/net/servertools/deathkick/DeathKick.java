package net.servertools.deathkick;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DeathKick implements ModInitializer {
	public static final String MOD_ID = "deathkick";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Set<String> JOIN_KEYS = Set.of("multiplayer.player.joined", "multiplayer.player.joined.renamed");
	private static final String LEAVE_KEY = "multiplayer.player.left";

	private static DeathKickConfig config;
	private static final Map<UUID, PendingKick> PENDING = new HashMap<>();

	private static final class PendingKick {
		int ticksLeft;
		final Text message;

		PendingKick(int ticksLeft, Text message) {
			this.ticksLeft = ticksLeft;
			this.message = message;
		}
	}

	@Override
	public void onInitialize() {
		config = DeathKickConfig.load();

		// 1. Queue a kick when a player dies (unless bypassed).
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayerEntity player)) return;
			if (!config.enabled || isBypassed(player)) return;

			Text deathMessage = player.getDamageTracker().getDeathMessage();
			String raw = config.kickMessage.replace("{death}", deathMessage.getString());
			PENDING.put(player.getUuid(), new PendingKick(Math.max(0, config.kickDelayTicks), Text.literal(colorize(raw))));
		});

		// 2. Process queued kicks at the end of each tick (never kick mid-death-handling).
		ServerTickEvents.END_SERVER_TICK.register(DeathKick::processKicks);

		// 3. Swallow vanilla join/leave broadcasts.
		ServerMessageEvents.ALLOW_GAME_MESSAGE.register((server, message, overlay) -> {
			if (message.getContent() instanceof TranslatableTextContent translatable) {
				String key = translatable.getKey();
				if (config.hideJoinMessages && JOIN_KEYS.contains(key)) return false;
				if (config.hideLeaveMessages && LEAVE_KEY.equals(key)) return false;
			}
			return true;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				DeathKickCommands.register(dispatcher));

		LOGGER.info("DeathKick loaded ({} players on bypass list)", config.bypass.size());
	}

	private static void processKicks(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		Iterator<Map.Entry<UUID, PendingKick>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, PendingKick> entry = it.next();
			PendingKick kick = entry.getValue();
			if (kick.ticksLeft-- > 0) continue;
			it.remove();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player != null) {
				player.networkHandler.disconnect(kick.message);
			}
		}
	}

	public static boolean isBypassed(ServerPlayerEntity player) {
		return config.bypass.containsKey(player.getUuidAsString())
				|| Permissions.check(player, "deathkick.bypass", false);
	}

	/** Turns &c style codes into section-sign codes the client understands. */
	static String colorize(String s) {
		return s.replaceAll("&([0-9a-fk-orA-FK-OR])", "\u00a7$1");
	}

	public static DeathKickConfig config() {
		return config;
	}

	static void reload() {
		config = DeathKickConfig.load();
	}
}
