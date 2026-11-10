package net.servertools.deathkick;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /deathkick on|off|status|reload
 * /deathkick mode spectator|kick
 * /deathkick joinmessages|leavemessages on|off
 * /deathkick bypass add <name>         (online or offline, no lookup needed)
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
				.then(literal("mode")
						.then(literal("spectator").executes(ctx -> setMode(ctx, "spectator")))
						.then(literal("kick").executes(ctx -> setMode(ctx, "kick"))))
				.then(literal("joinmessages")
						.then(literal("on").executes(ctx -> setMessages(ctx, true, true)))
						.then(literal("off").executes(ctx -> setMessages(ctx, true, false))))
				.then(literal("leavemessages")
						.then(literal("on").executes(ctx -> setMessages(ctx, false, true)))
						.then(literal("off").executes(ctx -> setMessages(ctx, false, false))))
				.then(literal("bypass")
						.then(literal("add")
								.then(argument("player", StringArgumentType.word())
										.suggests((ctx, builder) -> CommandSource.suggestMatching(ctx.getSource().getServer().getPlayerNames(), builder))
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
		ctx.getSource().sendFeedback(() -> Text.literal("DeathKick is now " + (enabled ? "ON" : "OFF") + "."), true);
		return 1;
	}

	private static int setMode(CommandContext<ServerCommandSource> ctx, String mode) {
		DeathKickConfig config = DeathKick.config();
		config.deathAction = mode;
		config.save();
		ctx.getSource().sendFeedback(() -> Text.literal("Players who die will now " + ("kick".equals(mode)
				? "be kicked." : "become spectators where they died.")), true);
		return 1;
	}

	private static int status(CommandContext<ServerCommandSource> ctx) {
		DeathKickConfig c = DeathKick.config();
		ctx.getSource().sendFeedback(() -> Text.literal(
				"DeathKick: " + (c.enabled ? "ON" : "OFF")
						+ " | on death: " + (c.spectatorMode() ? "spectator" : "kick")
						+ " | delay: " + c.kickDelayTicks + " ticks"
						+ " | hide joins: " + c.hideJoinMessages
						+ " | hide leaves: " + c.hideLeaveMessages
						+ " | bypassed players: " + c.bypass.size()), false);
		return 1;
	}

	/**
	 * Works for anyone: online players are stored by UUID; names of players who aren't online
	 * (or have never joined) are stored by name, so no Mojang lookup is needed.
	 */
	private static int bypassAdd(CommandContext<ServerCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "player");
		DeathKickConfig config = DeathKick.config();
		ServerPlayerEntity online = ctx.getSource().getServer().getPlayerManager().getPlayer(name);

		String key = online != null ? online.getUuidAsString() : DeathKick.nameKey(name);
		String display = online != null ? online.getGameProfile().name() : name;
		if (config.bypass.containsKey(key)) {
			ctx.getSource().sendError(Text.literal(display + " is already on the bypass list."));
			return 0;
		}
		config.bypass.put(key, display);
		config.save();
		ctx.getSource().sendFeedback(() -> Text.literal("Added " + display + " to the bypass list (they respawn normally when they die)."), true);
		return 1;
	}

	private static int setMessages(CommandContext<ServerCommandSource> ctx, boolean join, boolean show) {
		DeathKickConfig config = DeathKick.config();
		if (join) config.hideJoinMessages = !show;
		else config.hideLeaveMessages = !show;
		config.save();
		ctx.getSource().sendFeedback(() -> Text.literal((join ? "Join" : "Leave") + " messages are now "
				+ (show ? "shown" : "hidden") + "."), true);
		return 1;
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
