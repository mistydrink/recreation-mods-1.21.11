package net.servertools.smptools;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * /smp status
 * /smp remove_villagers [on|off]
 * /smp remove_shulkers [on|off]
 * /smp remove_elytras [on|off]
 * /smp remove_ominousvaults [on|off]
 */
final class SmpCommands {
	private SmpCommands() {}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		LiteralArgumentBuilder<ServerCommandSource> root = literal("smp")
				.requires(Permissions.require("smptools.command", 2))
				.then(literal("status").executes(ctx -> status(ctx.getSource())));

		for (Rule rule : Rule.values()) {
			root.then(literal(rule.command)
					.executes(ctx -> turnOn(ctx.getSource(), rule))
					.then(literal("on").executes(ctx -> turnOn(ctx.getSource(), rule)))
					.then(literal("off").executes(ctx -> turnOff(ctx.getSource(), rule))));
		}
		dispatcher.register(root);
	}

	private static int turnOn(ServerCommandSource src, Rule rule) {
		boolean wasOn = SmpTools.config().isOn(rule);
		SmpTools.config().set(rule, true);
		int removed = Remover.sweep(src.getServer(), rule);
		String where = rule == Rule.OMINOUS_VAULTS ? "near players" : "in loaded chunks";
		src.sendFeedback(() -> Text.literal((wasOn ? "Already on. " : "Now removing " + rule.label + ". ")
				+ "Removed " + removed + " " + where + " just now; the rest go as their chunks load, "
				+ "and new ones are removed as they appear. Undo with /smp " + rule.command + " off"), true);
		return Math.max(removed, 1);
	}

	private static int turnOff(ServerCommandSource src, Rule rule) {
		SmpTools.config().set(rule, false);
		src.sendFeedback(() -> Text.literal("Stopped removing " + rule.label
				+ ". Anything already removed stays gone; new ones can appear again."), true);
		return 1;
	}

	private static int status(ServerCommandSource src) {
		StringBuilder sb = new StringBuilder("SMP rules:");
		for (Rule rule : Rule.values()) {
			sb.append("\n  ").append(rule.command).append(": ").append(SmpTools.config().isOn(rule) ? "ON" : "off");
			if (rule == Rule.VILLAGERS && SmpTools.config().alsoRemoveWanderingTraders) {
				sb.append(" (wandering traders too)");
			}
		}
		String text = sb.toString();
		src.sendFeedback(() -> Text.literal(text), false);
		return 1;
	}
}
