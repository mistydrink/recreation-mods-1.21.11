package net.servertools.deathkick;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /deathkick on|off|status|reload
 * /deathkick bypass add <players>      (online or offline, by name)
 * /deathkick bypass remove <name>
 * /deathkick bypass list
 */
final class DeathKickCommands {
	private DeathKickCommands() {}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("deathkick")
				.requires(Permissions.require("deathkick.command", 2))
				.then(literal("on").executes(ctx -> setEnabled(ctx, true)))
				.then(literal("off").executes(ctx -> setEnabled(ctx, false)))
				.then(literal("status").executes(DeathKickCommands::status))
				.then(literal("reload").executes(ctx -> {
					DeathKick.reload();
					ctx.getSource().sendFeedback(() -> Text.literal("DeathKick config reloaded."), true);
					return 1;
				}))
				.then(literal("bypass")
						.then(literal("add")
								.then(argument("players", GameProfileArgumentType.gameProfile())
										.executes(DeathKickCommands::bypassAdd)))
						.then(literal("remove")
								.then(argument("name", StringArgumentType.word())
										.suggests((ctx, builder) -> CommandSource.suggestMatching(DeathKick.config().bypass.values(), builder))
										.executes(DeathKickCommands::bypassRemove)))
						.then(literal("list").executes(DeathKickCommands::bypassList))));
	}

	private static int setEnabled(CommandContext<ServerCommandSource> ctx, boolean enabled) {
		DeathKickConfig config = DeathKick.config();
		config.enabled = enabled;
		config.save();
		ctx.getSource().sendFeedback(() -> Text.literal("Kick on death is now " + (enabled ? "ON" : "OFF") + "."), true);
		return 1;
	}

	private static int status(CommandContext<ServerCommandSource> ctx) {
		DeathKickConfig c = DeathKick.config();
		ctx.getSource().sendFeedback(() -> Text.literal(
				"Kick on death: " + (c.enabled ? "ON" : "OFF")
						+ " | delay: " + c.kickDelayTicks + " ticks"
						+ " | hide joins: " + c.hideJoinMessages
						+ " | hide leaves: " + c.hideLeaveMessages
						+ " | bypassed players: " + c.bypass.size()), false);
		return 1;
	}

	private static int bypassAdd(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "players");
		DeathKickConfig config = DeathKick.config();
		int added = 0;
		for (GameProfile profile : profiles) {
			if (config.bypass.put(profile.getId().toString(), profile.getName()) == null) added++;
		}
		config.save();
		int count = added;
		ctx.getSource().sendFeedback(() -> Text.literal("Added " + count + " player(s) to the death-kick bypass list."), true);
		return count;
	}

	private static int bypassRemove(CommandContext<ServerCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		DeathKickConfig config = DeathKick.config();
		boolean removed = config.bypass.entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(name));
		if (!removed) {
			ctx.getSource().sendError(Text.literal(name + " is not on the bypass list."));
			return 0;
		}
		config.save();
		ctx.getSource().sendFeedback(() -> Text.literal("Removed " + name + " from the bypass list."), true);
		return 1;
	}

	private static int bypassList(CommandContext<ServerCommandSource> ctx) {
		Map<String, String> bypass = DeathKick.config().bypass;
		String list = bypass.isEmpty() ? "(nobody)" : String.join(", ", bypass.values());
		ctx.getSource().sendFeedback(() -> Text.literal("Bypass list (" + bypass.size() + "): " + list), false);
		return bypass.size();
	}
}
