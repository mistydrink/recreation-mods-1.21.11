package net.servertools.nickskins.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public interface EntityTrackerAccessor {
	/** Sends a despawn packet for the tracked entity to this viewer. */
	@Invoker("stopTracking")
	void nickskins$stopTracking(ServerPlayerEntity viewer);

	/** Re-sends spawn packets (equipment, metadata, passengers…) if the viewer should see the entity. */
	@Invoker("updateTrackedStatus")
	void nickskins$updateTrackedStatus(ServerPlayerEntity viewer);
}
