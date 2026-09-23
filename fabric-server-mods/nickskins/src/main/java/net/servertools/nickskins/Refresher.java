package net.servertools.nickskins;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.servertools.nickskins.mixin.EntityTrackerAccessor;
import net.servertools.nickskins.mixin.ServerChunkLoadingManagerAccessor;
import net.servertools.nickskins.mixin.ServerPlayerEntityAccessor;

import java.util.List;
import java.util.UUID;

/** Pushes a changed nick/skin to every client without anyone relogging. */
public final class Refresher {
	private Refresher() {}

	/** Offline players are skipped; their override is applied automatically when they join. */
	public static void refresh(MinecraftServer server, UUID uuid, boolean skinChanged) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
		if (player != null) refresh(player, skinChanged);
	}

	public static void refresh(ServerPlayerEntity target, boolean skinChanged) {
		MinecraftServer server = target.getServer();
		if (server == null) return;
		PlayerManager playerManager = server.getPlayerManager();

		// 1. Re-add the tab-list entry. The packet constructor goes through PlayerListEntryMixin,
		//    so clients receive the new name/skin.
		playerManager.sendToAll(new PlayerRemoveS2CPacket(List.of(target.getUuid())));
		playerManager.sendToAll(PlayerListS2CPacket.entryFromPlayer(List.of(target)));

		// 2. Other clients cache the profile inside the player entity, so despawn/respawn it for them.
		respawnForViewers(target);

		// 3. The player's own client only picks up its new skin after a respawn.
		if (skinChanged && NickSkins.storage().config().refreshOwnSkin
				&& !target.isDead() && !target.isSleeping()) {
			refreshSelf(target, playerManager);
		}
	}

	private static void respawnForViewers(ServerPlayerEntity target) {
		ServerWorld world = target.getServerWorld();
		Object tracker = ((ServerChunkLoadingManagerAccessor) world.getChunkManager().chunkLoadingManager)
				.nickskins$getEntityTrackers()
				.get(target.getId());
		if (tracker == null) return;

		EntityTrackerAccessor accessor = (EntityTrackerAccessor) tracker;
		for (ServerPlayerEntity viewer : world.getPlayers()) {
			if (viewer == target) continue;
			accessor.nickskins$stopTracking(viewer);
			accessor.nickskins$updateTrackedStatus(viewer);
		}
	}

	private static void refreshSelf(ServerPlayerEntity player, PlayerManager playerManager) {
		ServerWorld world = player.getServerWorld();
		ServerPlayNetworkHandler net = player.networkHandler;

		player.closeHandledScreen();
		net.sendPacket(new PlayerRespawnS2CPacket(player.createCommonPlayerSpawnInfo(world), PlayerRespawnS2CPacket.KEEP_ALL));
		net.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());

		playerManager.sendCommandTree(player);           // command tree + op level
		playerManager.sendWorldInfo(player, world);      // time, weather, world border, spawn
		playerManager.sendPlayerStatus(player);          // inventory, selected slot, marks health dirty
		player.sendAbilitiesUpdate();                    // flying, creative abilities
		((ServerPlayerEntityAccessor) player).nickskins$setSyncedExperience(-1); // XP bar re-sent next tick
		net.sendPacket(new HealthUpdateS2CPacket(player.getHealth(),
				player.getHungerManager().getFoodLevel(), player.getHungerManager().getSaturationLevel()));

		for (StatusEffectInstance effect : player.getStatusEffects()) {
			net.sendPacket(new EntityStatusEffectS2CPacket(player.getId(), effect, false));
		}
		if (player.hasVehicle()) {
			net.sendPacket(new EntityPassengersSetS2CPacket(player.getVehicle()));
		}
	}
}
