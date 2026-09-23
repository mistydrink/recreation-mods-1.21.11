package net.servertools.smptools;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VaultBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Removal happens in two ways:
 *  - live: anything matching an active rule is queued the moment it loads (chunk load, spawn, breeding,
 *    curing, shulker duplication, new chunks generating) and removed at the end of that tick;
 *  - sweep: when a rule is switched on, everything already loaded is cleaned up immediately.
 * Removing things mid-load can corrupt the entity/chunk bookkeeping, hence the one-tick queue.
 */
public final class Remover {
	private record VaultRef(ServerWorld world, BlockPos pos) {}

	private static final Queue<Entity> ENTITY_QUEUE = new ConcurrentLinkedQueue<>();
	private static final Queue<VaultRef> VAULT_QUEUE = new ConcurrentLinkedQueue<>();

	private Remover() {}

	// ------------------------------------------------------------------ matching

	/** The active rule this entity falls under, or null. */
	static @Nullable Rule ruleFor(Entity entity) {
		SmpConfig c = SmpTools.config();
		if (c.removeVillagers && (entity instanceof VillagerEntity
				|| (c.alsoRemoveWanderingTraders && entity instanceof WanderingTraderEntity))) {
			return Rule.VILLAGERS;
		}
		if (c.removeShulkers && entity instanceof ShulkerEntity) return Rule.SHULKERS;
		if (c.removeElytras && isEndElytraFrame(entity)) return Rule.ELYTRAS;
		return null;
	}

	/** Only frames in the End, which is where End ships put their elytra. Frames in bases elsewhere are left alone. */
	private static boolean isEndElytraFrame(Entity entity) {
		return entity instanceof ItemFrameEntity frame
				&& frame.getWorld().getRegistryKey() == World.END
				&& frame.getHeldItemStack().isOf(Items.ELYTRA);
	}

	static boolean isOminousVault(BlockState state) {
		return state.isOf(Blocks.VAULT) && state.get(VaultBlock.OMINOUS);
	}

	// ------------------------------------------------------------------ live removal

	static void onEntityLoad(Entity entity) {
		if (ruleFor(entity) != null) ENTITY_QUEUE.add(entity);
	}

	static void onBlockEntityLoad(BlockEntity blockEntity, ServerWorld world) {
		if (SmpTools.config().removeOminousVaults && isOminousVault(blockEntity.getCachedState())) {
			VAULT_QUEUE.add(new VaultRef(world, blockEntity.getPos().toImmutable()));
		}
	}

	static void processQueues() {
		Entity entity;
		while ((entity = ENTITY_QUEUE.poll()) != null) {
			if (entity.isRemoved()) continue; // unloaded again already; caught next time it loads
			Rule rule = ruleFor(entity);
			if (rule != null) apply(rule, entity);
		}

		VaultRef vault;
		while ((vault = VAULT_QUEUE.poll()) != null) {
			// Never force-load a chunk here; if it unloaded, the vault is queued again on the next load.
			WorldChunk chunk = vault.world().getChunkManager().getWorldChunk(vault.pos().getX() >> 4, vault.pos().getZ() >> 4);
			if (chunk != null && isOminousVault(chunk.getBlockState(vault.pos()))) {
				removeVault(vault.world(), vault.pos());
			}
		}
	}

	private static void apply(Rule rule, Entity entity) {
		if (rule == Rule.ELYTRAS && entity instanceof ItemFrameEntity frame) {
			frame.setHeldItemStack(ItemStack.EMPTY); // keep the frame, take the elytra
		} else {
			entity.discard(); // no drops
		}
	}

	private static void removeVault(ServerWorld world, BlockPos pos) {
		world.setBlockState(pos, Blocks.AIR.getDefaultState());
	}

	// ------------------------------------------------------------------ sweeps when a rule is turned on

	/** Cleans up everything currently loaded for one rule. Returns how many were removed. */
	static int sweep(MinecraftServer server, Rule rule) {
		return rule == Rule.OMINOUS_VAULTS ? sweepVaults(server) : sweepEntities(server, rule);
	}

	private static int sweepEntities(MinecraftServer server, Rule rule) {
		List<Entity> hits = new ArrayList<>();
		for (ServerWorld world : server.getWorlds()) {
			for (Entity entity : world.iterateEntities()) {
				if (ruleFor(entity) == rule) hits.add(entity);
			}
		}
		for (Entity entity : hits) apply(rule, entity); // outside the iteration on purpose
		return hits.size();
	}

	/** Checks every loaded chunk within view distance of any player. */
	private static int sweepVaults(MinecraftServer server) {
		int radius = server.getPlayerManager().getViewDistance();
		int removed = 0;
		for (ServerWorld world : server.getWorlds()) {
			Set<Long> seen = new HashSet<>();
			for (ServerPlayerEntity player : world.getPlayers()) {
				ChunkPos center = player.getChunkPos();
				for (int x = center.x - radius; x <= center.x + radius; x++) {
					for (int z = center.z - radius; z <= center.z + radius; z++) {
						if (!seen.add(ChunkPos.toLong(x, z))) continue;
						WorldChunk chunk = world.getChunkManager().getWorldChunk(x, z);
						if (chunk == null) continue;
						List<BlockPos> vaults = new ArrayList<>();
						for (BlockEntity be : chunk.getBlockEntities().values()) {
							if (isOminousVault(be.getCachedState())) vaults.add(be.getPos().toImmutable());
						}
						for (BlockPos pos : vaults) {
							removeVault(world, pos);
							removed++;
						}
					}
				}
			}
		}
		return removed;
	}
}
