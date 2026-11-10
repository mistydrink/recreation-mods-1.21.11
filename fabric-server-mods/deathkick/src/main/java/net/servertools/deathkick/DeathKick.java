package net.servertools.deathkick;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
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
	private static final Map<UUID, PendingDeath> PENDING = new HashMap<>();
	/** True while this mod broadcasts its own "left the game" line, so the leave-message filter lets it through. */
	private static boolean sendingDeathLeave;

	/** What to do with a player once the death delay is up, plus where they died. */
	private static final class PendingDeath {
		int ticksLeft;
		final Text kickMessage;
		final ServerWorld world;
		final double x, y, z;
		final float yaw, pitch;

		PendingDeath(int ticksLeft, Text kickMessage, ServerPlayerEntity player) {
			this.ticksLeft = ticksLeft;
			this.kickMessage = kickMessage;
			this.world = player.getCommandSource().getWorld();
			this.x = player.getX();
			this.y = player.getY();
			this.z = player.getZ();
			this.yaw = player.getYaw();
			this.pitch = player.getPitch();
		}
	}

	@Override
	public void onInitialize() {
		config = DeathKickConfig.load();

		// 1. When a player dies (and isn't bypassed), queue the kick or the switch to spectator.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayerEntity player)) return;
			if (!config.enabled || isBypassed(player)) return;

			if (config.spectatorMode()) {
				// Set right away, so leaving and rejoining before the delay still respawns them as a spectator.
				player.changeGameMode(GameMode.SPECTATOR);
			}

			Text deathMessage = player.getDamageTracker().getDeathMessage();
			String raw = config.kickMessage.replace("{death}", deathMessage.getString());
			PENDING.put(player.getUuid(), new PendingDeath(Math.max(0, config.kickDelayTicks), Text.literal(colorize(raw)), player));
		});

		// 2. Act on queued deaths at the end of each tick (never in the middle of death handling).
		ServerTickEvents.END_SERVER_TICK.register(DeathKick::processDeaths);

		// 3. Swallow vanilla join/leave broadcasts.
		ServerMessageEvents.ALLOW_GAME_MESSAGE.register((server, message, overlay) -> {
			if (sendingDeathLeave) return true;
			if (message.getContent() instanceof TranslatableTextContent translatable) {
				String key = translatable.getKey();
				if (config.hideJoinMessages && JOIN_KEYS.contains(key)) return false;
				if (config.hideLeaveMessages && LEAVE_KEY.equals(key)) return false;
			}
			return true;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				DeathKickCommands.register(dispatcher));

		LOGGER.info("DeathKick loaded (on death: {}, {} players on bypass list)", config.deathAction, config.bypass.size());
	}

	private static void processDeaths(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		Iterator<Map.Entry<UUID, PendingDeath>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, PendingDeath> entry = it.next();
			PendingDeath pending = entry.getValue();
			if (pending.ticksLeft-- > 0) continue;
			it.remove();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) continue; // already left; spectator mode was set at death, so they rejoin as one

			if (config.spectatorMode()) {
				makeSpectator(server, player, pending);
			} else {
				player.networkHandler.disconnect(pending.kickMessage);
			}
		}
	}

	/** Respawns the player (same as clicking Respawn), switches them to spectator and puts them back where they died. */
	private static void makeSpectator(MinecraftServer server, ServerPlayerEntity player, PendingDeath pending) {
		ServerPlayNetworkHandler handler = player.networkHandler;
		if (player.isDead()) {
			handler.onClientStatus(new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
		}
		ServerPlayerEntity spectator = handler.player; // respawning replaces the player object
		spectator.changeGameMode(GameMode.SPECTATOR);
		spectator.teleport(pending.world, pending.x, pending.y, pending.z, Set.of(), pending.yaw, pending.pitch, false);

		if (config.announceDeathAsLeave) {
			Text left = Text.translatable(LEAVE_KEY, spectator.getDisplayName()).formatted(Formatting.YELLOW);
			sendingDeathLeave = true;
			try {
				server.getPlayerManager().broadcast(left, false);
			} finally {
				sendingDeathLeave = false;
			}
		}
	}

	public static boolean isBypassed(ServerPlayerEntity player) {
		return config.bypass.containsKey(player.getUuidAsString())
				|| config.bypass.containsKey(nameKey(player.getGameProfile().name()))
				|| Permissions.check(player, "deathkick.bypass", false);
	}

	static String nameKey(String name) {
		return "name:" + name.toLowerCase(java.util.Locale.ROOT);
	}

	/** Turns &c style codes into section-sign codes the client understands. */
	static String colorize(String s) {
		return s.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
	}

	public static DeathKickConfig config() {
		return config;
	}

	static void reload() {
		config = DeathKickConfig.load();
	}
}
