package net.servertools.nickskins.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.servertools.nickskins.NickSkins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat, death messages, advancements and /list all use getDisplayName(). The nicked version
 * deliberately drops the vanilla hover/click events, which would reveal the real name.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
	private void nickskins$nickDisplayName(CallbackInfoReturnable<Text> cir) {
		if ((Object) this instanceof ServerPlayerEntity player) {
			String nick = NickSkins.storage().nickOf(player.getUuid());
			if (nick != null) {
				cir.setReturnValue(Team.decorateName(player.getScoreboardTeam(), Text.literal(nick)));
			}
		}
	}
}
