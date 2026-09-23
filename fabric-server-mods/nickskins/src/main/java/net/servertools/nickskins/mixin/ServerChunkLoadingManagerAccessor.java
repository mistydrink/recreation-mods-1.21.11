package net.servertools.nickskins.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkLoadingManager.class)
public interface ServerChunkLoadingManagerAccessor {
	/** entity id -> EntityTracker (package-private inner class, so typed as wildcard). */
	@Accessor("entityTrackers")
	Int2ObjectMap<?> nickskins$getEntityTrackers();
}
