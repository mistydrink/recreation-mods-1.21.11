package net.servertools.nickskins.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayerEntity.class)
public interface ServerPlayerEntityAccessor {
	/** Setting this to -1 makes vanilla re-send the XP bar on the next tick. */
	@Accessor("syncedExperience")
	void nickskins$setSyncedExperience(int value);
}
