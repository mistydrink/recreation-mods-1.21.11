package net.servertools.nickskins.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.servertools.nickskins.NickSkins;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every tab-list entry the server sends is built from a ServerPlayerEntity through this constructor.
 * Swapping the GameProfile here changes the name above the head, the tab-list name and the skin
 * for every client, without touching the real profile the server uses for saves/commands.
 */
@Mixin(PlayerListS2CPacket.Entry.class)
public abstract class PlayerListEntryMixin {
	@Shadow @Final @Mutable
	private GameProfile profile;

	@Inject(method = "<init>(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("RETURN"))
	private void nickskins$rewriteProfile(ServerPlayerEntity player, CallbackInfo ci) {
		this.profile = NickSkins.storage().rewriteProfile(this.profile);
	}
}
