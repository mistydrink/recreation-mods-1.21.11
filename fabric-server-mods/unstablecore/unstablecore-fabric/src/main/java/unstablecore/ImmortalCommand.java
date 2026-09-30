package unstablecore;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** /immortal [player] (alias /god): toggles immortality. Ops only. */
public final class ImmortalCommand {
	private ImmortalCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, ImmortalityManager manager) {
		for (String name : List.of("immortal", "god")) {
			dispatcher.register(CommandManager.literal(name)
					.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
					.executes(ctx -> toggle(ctx.getSource(), ctx.getSource().getPlayerOrThrow(), manager))
					.then(CommandManager.argument("player", EntityArgumentType.player())
							.executes(ctx -> toggle(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), manager))));
		}
	}

	private static int toggle(ServerCommandSource source, ServerPlayerEntity target, ImmortalityManager manager) {
		boolean nowImmortal = !manager.isImmortal(target.getUuid());
		manager.setImmortal(target.getUuid(), nowImmortal);

		target.sendMessage(UnstableCore.msg(nowImmortal ? "You are now immortal." : "You are no longer immortal."));
		if (source.getPlayer() != target) {
			String name = target.getName().getString();
			source.sendFeedback(() -> UnstableCore.msg(name + (nowImmortal ? " is now immortal." : " is no longer immortal.")), false);
		}
		return 1;
	}
}
