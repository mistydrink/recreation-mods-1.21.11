package unstablecore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProperties;

/**
 * Port of the plugin's ImmortalityListener:
 * - an immortal player can't die; they're held at minHealth instead
 * - with smartTotem on, holding a totem turns this off so the totem works normally
 * - falling into the void sends them to world spawn
 * - their food can't drop any lower once it's at 6 (3 drumsticks) or below
 */
public final class ImmortalityListener {
	private static final int HUNGER_FLOOR = 6;

	private static final Map<UUID, Integer> lastFoodLevel = new HashMap<>();
	private static final Set<UUID> pendingSpawnTeleports = new HashSet<>();

	private ImmortalityListener() {
	}

	public static void register(ImmortalityManager manager, ImmortalityConfig config) {
		// Fires on fatal damage, before a totem would be used. Returning false cancels the death.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity player) || !isProtected(player, manager, config)) {
				return true;
			}
			player.setHealth(config.minHealth);
			if (source.isOf(DamageTypes.OUT_OF_WORLD)) {
				// Teleport at the end of the tick rather than in the middle of the damage code.
				pendingSpawnTeleports.add(player.getUuid());
			}
			return false;
		});

		// The plugin also stopped non-fatal hits from taking them below minHealth.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayerEntity player
					&& isProtected(player, manager, config)
					&& player.getHealth() < config.minHealth) {
				player.setHealth(config.minHealth);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				UUID id = player.getUuid();

				if (pendingSpawnTeleports.remove(id)) {
					sendToSpawn(server, player);
				}

				if (!manager.isImmortal(id)) {
					lastFoodLevel.remove(id);
					continue;
				}
				HungerManager hunger = player.getHungerManager();
				int food = hunger.getFoodLevel();
				Integer previous = lastFoodLevel.get(id);
				if (previous != null && previous <= HUNGER_FLOOR && food < previous) {
					hunger.setFoodLevel(previous);
					food = previous;
				}
				lastFoodLevel.put(id, food);
			}
			pendingSpawnTeleports.clear(); // drop any for players who logged off
		});
	}

	private static boolean isProtected(ServerPlayerEntity player, ImmortalityManager manager, ImmortalityConfig config) {
		if (!manager.isImmortal(player.getUuid())) {
			return false;
		}
		return !(config.smartTotem && hasTotem(player));
	}

	private static boolean hasTotem(ServerPlayerEntity player) {
		return player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)
				|| player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
	}

	private static void sendToSpawn(MinecraftServer server, ServerPlayerEntity player) {
		WorldProperties.SpawnPoint spawn = server.getSpawnPoint();
		ServerWorld world = server.getWorld(spawn.getDimension());
		if (world == null) {
			world = server.getOverworld();
		}
		BlockPos pos = spawn.getPos();
		player.teleport(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch(), false);
		player.setVelocity(Vec3d.ZERO);
		player.onLanding();
	}
}
