package net.servertools.nickskins.mixin;

import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.servertools.nickskins.NickSkins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tab-list display name, so the nick keeps the player's team color there. */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
	@Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
	private void nickskins$nickListName(CallbackInfoReturnable<Text> cir) {
		ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
		String nick = NickSkins.storage().nickOf(self.getUuid());
		if (nick != null) {
			cir.setReturnValue(Team.decorateName(self.getScoreboardTeam(), Text.literal(nick)));
		}
	}
}
