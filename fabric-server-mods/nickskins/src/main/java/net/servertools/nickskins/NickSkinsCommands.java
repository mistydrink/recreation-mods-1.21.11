package net.servertools.nickskins;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /nick set|numbered|reset <targets> ...     /nick group <group> set|numbered|reset ...
 * /nick resetall | list
 * /nickgroup create|delete|add|remove|list
 * /skin random|set|reset <targets> ...      /skin group <group> random|set|reset
 * /skin resetall | pool list|add|remove
 * /nickskins reload
 */
final class NickSkinsCommands {
	/** Minecraft's protocol hard limit for player names. Longer nicks would disconnect clients. */
	private static final int MAX_NICK = 16;

	private record Target(UUID uuid, String name) {}

	private NickSkinsCommands() {}

	private static Storage storage() {
		return NickSkins.storage();
	}

	private static final SuggestionProvider<ServerCommandSource> GROUPS =
			(ctx, builder) -> CommandSource.suggestMatching(storage().groups().keySet(), builder);
	private static final SuggestionProvider<ServerCommandSource> SKINS =
			(ctx, builder) -> CommandSource.suggestMatching(storage().skinPool().stream().map(s -> s.name).toList(), builder);
	private static final SuggestionProvider<ServerCommandSource> GROUP_MEMBERS = (ctx, builder) -> {
		Map<UUID, String> members = storage().group(StringArgumentType.getString(ctx, "group"));
		Collection<String> names = members == null ? List.<String>of() : members.values();
		return CommandSource.suggestMatching(names, builder);
	};

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		// ------------------------------------------------------------ /nick
		dispatcher.register(literal("nick")
				.requires(Permissions.require("nickskins.nick", 2))
				.then(literal("set")
						.then(argument("targets", EntityArgumentType.players())
								.then(argument("name", greedyString())
										.executes(ctx -> setNick(ctx.getSource(), players(ctx), StringArgumentType.getString(ctx, "name"))))))
				.then(literal("numbered")
						.then(argument("targets", EntityArgumentType.players())
								.then(argument("prefix", greedyString())
										.executes(ctx -> numberedNick(ctx.getSource(), players(ctx), StringArgumentType.getString(ctx, "prefix"))))))
				.then(literal("reset")
						.then(argument("targets", EntityArgumentType.players())
								.executes(ctx -> resetNick(ctx.getSource(), players(ctx)))))
				.then(literal("resetall")
						.executes(ctx -> resetNick(ctx.getSource(), everyoneWith(true))))
				.then(literal("list")
						.executes(ctx -> list(ctx.getSource())))
				.then(literal("group")
						.then(argument("group", word()).suggests(GROUPS)
								.then(literal("set")
										.then(argument("name", greedyString())
												.executes(ctx -> setNick(ctx.getSource(), group(ctx), StringArgumentType.getString(ctx, "name")))))
								.then(literal("numbered")
										.then(argument("prefix", greedyString())
												.executes(ctx -> numberedNick(ctx.getSource(), group(ctx), StringArgumentType.getString(ctx, "prefix")))))
								.then(literal("reset")
										.executes(ctx -> resetNick(ctx.getSource(), group(ctx)))))));

		// ------------------------------------------------------------ /nickgroup
		dispatcher.register(literal("nickgroup")
				.requires(Permissions.require("nickskins.group", 2))
				.then(literal("create")
						.then(argument("group", word())
								.executes(ctx -> createGroup(ctx.getSource(), StringArgumentType.getString(ctx, "group")))))
				.then(literal("delete")
						.then(argument("group", word()).suggests(GROUPS)
								.executes(ctx -> deleteGroup(ctx.getSource(), StringArgumentType.getString(ctx, "group")))))
				.then(literal("add")
						.then(argument("group", word()).suggests(GROUPS)
								.then(argument("players", GameProfileArgumentType.gameProfile())
										.executes(NickSkinsCommands::groupAdd))))
				.then(literal("remove")
						.then(argument("group", word()).suggests(GROUPS)
								.then(argument("player", word()).suggests(GROUP_MEMBERS)
										.executes(NickSkinsCommands::groupRemove))))
				.then(literal("list")
						.executes(ctx -> listGroups(ctx.getSource()))
						.then(argument("group", word()).suggests(GROUPS)
								.executes(ctx -> showGroup(ctx.getSource(), StringArgumentType.getString(ctx, "group"))))));

		// ------------------------------------------------------------ /skin
		dispatcher.register(literal("skin")
				.requires(Permissions.require("nickskins.skin", 2))
				.then(literal("random")
						.then(argument("targets", EntityArgumentType.players())
								.executes(ctx -> randomSkins(ctx.getSource(), players(ctx)))))
				.then(literal("set")
						.then(argument("targets", EntityArgumentType.players())
								.then(argument("skin", word()).suggests(SKINS)
										.executes(ctx -> setSkin(ctx.getSource(), players(ctx), StringArgumentType.getString(ctx, "skin"))))))
				.then(literal("reset")
						.then(argument("targets", EntityArgumentType.players())
								.executes(ctx -> resetSkin(ctx.getSource(), players(ctx)))))
				.then(literal("resetall")
						.executes(ctx -> resetSkin(ctx.getSource(), everyoneWith(false))))
				.then(literal("group")
						.then(argument("group", word()).suggests(GROUPS)
								.then(literal("random")
										.executes(ctx -> randomSkins(ctx.getSource(), group(ctx))))
								.then(literal("set")
										.then(argument("skin", word()).suggests(SKINS)
												.executes(ctx -> setSkin(ctx.getSource(), group(ctx), StringArgumentType.getString(ctx, "skin")))))
								.then(literal("reset")
										.executes(ctx -> resetSkin(ctx.getSource(), group(ctx))))))
				.then(literal("pool")
						.then(literal("list")
								.executes(ctx -> listPool(ctx.getSource())))
						.then(literal("add")
								.then(argument("username", word())
										.executes(ctx -> addFromMojang(ctx.getSource(),
												StringArgumentType.getString(ctx, "username"),
												StringArgumentType.getString(ctx, "username")))
										.then(argument("name", word())
												.executes(ctx -> addFromMojang(ctx.getSource(),
														StringArgumentType.getString(ctx, "username"),
														StringArgumentType.getString(ctx, "name"))))))
						.then(literal("remove")
								.then(argument("skin", word()).suggests(SKINS)
										.executes(ctx -> removeFromPool(ctx.getSource(), StringArgumentType.getString(ctx, "skin")))))));

		// ------------------------------------------------------------ /nickskins
		dispatcher.register(literal("nickskins")
				.requires(Permissions.require("nickskins.admin", 2))
				.then(literal("reload").executes(ctx -> {
					storage().loadConfigAndSkins();
					ok(ctx.getSource(), "Reloaded config.json and skins.json (" + storage().skinPool().size() + " skins).");
					return 1;
				})));
	}

	// ================================================================ target resolution

	private static List<Target> players(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "targets");
		List<Target> targets = new ArrayList<>();
		for (ServerPlayerEntity p : players) targets.add(new Target(p.getUuid(), p.getGameProfile().getName()));
		return targets;
	}

	/** Group members, online or not. Offline members get their nick/skin when they next join. */
	private static List<Target> group(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "group");
		Map<UUID, String> members = storage().group(name);
		if (members == null) throw error("No group named \"" + name + "\". Create it with /nickgroup create " + name);
		if (members.isEmpty()) throw error("Group \"" + name + "\" is empty. Add people with /nickgroup add " + name + " <players>");
		List<Target> targets = new ArrayList<>();
		members.forEach((uuid, n) -> targets.add(new Target(uuid, n)));
		return targets;
	}

	private static List<Target> everyoneWith(boolean nick) {
		List<Target> targets = new ArrayList<>();
		storage().overrides().forEach((uuid, o) -> {
			if (nick ? o.nick != null : o.skin != null) targets.add(new Target(uuid, o.realName));
		});
		return targets;
	}

	// ================================================================ nicknames

	private static int setNick(ServerCommandSource src, List<Target> targets, String raw) throws CommandSyntaxException {
		String nick = raw.trim();
		checkNick(nick);
		for (Target t : targets) storage().setNick(t.uuid(), t.name(), nick);
		storage().saveData();
		int online = refreshAll(src.getServer(), targets, false);
		ok(src, "Nicknamed " + targets.size() + " player(s) \"" + nick + "\"" + offlineNote(targets.size(), online) + ".");
		return targets.size();
	}

	private static int numberedNick(ServerCommandSource src, List<Target> targets, String raw) throws CommandSyntaxException {
		String prefix = raw.trim();
		List<Target> sorted = new ArrayList<>(targets);
		sorted.sort(Comparator.comparing(t -> t.name().toLowerCase(Locale.ROOT)));
		checkNick(prefix + " " + sorted.size()); // longest one
		for (int i = 0; i < sorted.size(); i++) {
			Target t = sorted.get(i);
			storage().setNick(t.uuid(), t.name(), prefix + " " + (i + 1));
		}
		storage().saveData();
		int online = refreshAll(src.getServer(), sorted, false);
		ok(src, "Nicknamed " + sorted.size() + " player(s) \"" + prefix + " 1\" to \"" + prefix + " " + sorted.size() + "\""
				+ offlineNote(sorted.size(), online) + ".");
		return sorted.size();
	}

	private static int resetNick(ServerCommandSource src, List<Target> targets) {
		for (Target t : targets) storage().setNick(t.uuid(), t.name(), null);
		storage().saveData();
		refreshAll(src.getServer(), targets, false);
		ok(src, "Reset the nickname of " + targets.size() + " player(s).");
		return targets.size();
	}

	private static void checkNick(String nick) throws CommandSyntaxException {
		if (nick.isEmpty()) throw error("Nicknames can't be empty.");
		if (nick.length() > MAX_NICK) {
			throw error("\"" + nick + "\" is " + nick.length() + " characters. Minecraft allows at most " + MAX_NICK + " above players' heads.");
		}
		if (nick.indexOf('\u00a7') >= 0 || nick.indexOf('&') >= 0) {
			throw error("Color codes aren't supported in nicknames (the name above heads can't be colored). Use teams for color.");
		}
	}

	private static int list(ServerCommandSource src) {
		Map<UUID, PlayerOverride> overrides = storage().overrides();
		if (overrides.isEmpty()) {
			ok(src, "Nobody has a nickname or custom skin.");
			return 0;
		}
		String lines = overrides.values().stream()
				.sorted(Comparator.comparing(o -> o.realName.toLowerCase(Locale.ROOT)))
				.map(o -> o.realName + " -> "
						+ (o.nick != null ? "\"" + o.nick + "\"" : "(real name)")
						+ (o.skin != null ? ", skin: " + o.skin.name : ""))
				.collect(Collectors.joining("\n"));
		src.sendFeedback(() -> Text.literal(overrides.size() + " player(s) changed:\n" + lines), false);
		return overrides.size();
	}

	// ================================================================ groups

	private static int createGroup(ServerCommandSource src, String name) throws CommandSyntaxException {
		if (!storage().createGroup(name)) throw error("Group \"" + name + "\" already exists.");
		ok(src, "Created group \"" + name.toLowerCase(Locale.ROOT) + "\". Add people with /nickgroup add " + name + " <players>");
		return 1;
	}

	private static int deleteGroup(ServerCommandSource src, String name) throws CommandSyntaxException {
		if (!storage().deleteGroup(name)) throw error("No group named \"" + name + "\".");
		ok(src, "Deleted group \"" + name + "\". (Nicknames/skins already given are kept.)");
		return 1;
	}

	private static int groupAdd(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "group");
		Map<UUID, String> members = storage().group(name);
		if (members == null) throw error("No group named \"" + name + "\".");
		int added = 0;
		for (GameProfile profile : GameProfileArgumentType.getProfileArgument(ctx, "players")) {
			if (members.put(profile.getId(), profile.getName()) == null) added++;
		}
		storage().saveData();
		ok(ctx.getSource(), "Added " + added + " player(s) to \"" + name + "\" (" + members.size() + " members).");
		return added;
	}

	private static int groupRemove(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "group");
		String player = StringArgumentType.getString(ctx, "player");
		Map<UUID, String> members = storage().group(name);
		if (members == null) throw error("No group named \"" + name + "\".");
		if (!members.entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(player))) {
			throw error(player + " isn't in \"" + name + "\".");
		}
		storage().saveData();
		ok(ctx.getSource(), "Removed " + player + " from \"" + name + "\".");
		return 1;
	}

	private static int listGroups(ServerCommandSource src) {
		Map<String, Map<UUID, String>> groups = storage().groups();
		String text = groups.isEmpty() ? "No groups yet. Make one with /nickgroup create <name>"
				: "Groups: " + groups.entrySet().stream()
				.map(e -> e.getKey() + " (" + e.getValue().size() + ")")
				.collect(Collectors.joining(", "));
		src.sendFeedback(() -> Text.literal(text), false);
		return groups.size();
	}

	private static int showGroup(ServerCommandSource src, String name) throws CommandSyntaxException {
		Map<UUID, String> members = storage().group(name);
		if (members == null) throw error("No group named \"" + name + "\".");
		String text = name + " (" + members.size() + "): " + (members.isEmpty() ? "(empty)" : String.join(", ", members.values()));
		src.sendFeedback(() -> Text.literal(text), false);
		return members.size();
	}

	// ================================================================ skins

	private static int randomSkins(ServerCommandSource src, List<Target> targets) throws CommandSyntaxException {
		List<SkinEntry> pool = new ArrayList<>(storage().skinPool());
		if (pool.isEmpty()) {
			throw error("The skin pool is empty. Add skins with /skin pool add <username>, or paste signed skins into "
					+ "config/nickskins/skins.json and run /nickskins reload.");
		}
		// Shuffle both lists and deal skins round-robin, so nobody shares a skin until the pool runs out.
		List<Target> order = new ArrayList<>(targets);
		Collections.shuffle(pool);
		Collections.shuffle(order);
		for (int i = 0; i < order.size(); i++) {
			Target t = order.get(i);
			storage().setSkin(t.uuid(), t.name(), pool.get(i % pool.size()));
		}
		storage().saveData();
		int online = refreshAll(src.getServer(), order, true);
		ok(src, "Gave " + order.size() + " player(s) random skins from a pool of " + pool.size()
				+ offlineNote(order.size(), online) + ".");
		return order.size();
	}

	private static int setSkin(ServerCommandSource src, List<Target> targets, String skinName) throws CommandSyntaxException {
		SkinEntry skin = storage().findSkin(skinName);
		if (skin == null) throw error("No skin named \"" + skinName + "\" in the pool. See /skin pool list");
		for (Target t : targets) storage().setSkin(t.uuid(), t.name(), skin);
		storage().saveData();
		int online = refreshAll(src.getServer(), targets, true);
		ok(src, "Gave " + targets.size() + " player(s) the \"" + skin.name + "\" skin" + offlineNote(targets.size(), online) + ".");
		return targets.size();
	}

	private static int resetSkin(ServerCommandSource src, List<Target> targets) {
		for (Target t : targets) storage().setSkin(t.uuid(), t.name(), null);
		storage().saveData();
		refreshAll(src.getServer(), targets, true);
		ok(src, "Restored the real skin of " + targets.size() + " player(s).");
		return targets.size();
	}

	private static int listPool(ServerCommandSource src) {
		List<SkinEntry> pool = storage().skinPool();
		String text = pool.isEmpty() ? "The skin pool is empty."
				: "Skin pool (" + pool.size() + "): " + pool.stream().map(s -> s.name).collect(Collectors.joining(", "));
		src.sendFeedback(() -> Text.literal(text), false);
		return pool.size();
	}

	private static int addFromMojang(ServerCommandSource src, String username, String label) {
		MinecraftServer server = src.getServer();
		src.sendFeedback(() -> Text.literal("Fetching " + username + "'s skin from Mojang..."), false);
		MojangSkins.fetch(username, label).whenComplete((skin, err) -> server.execute(() -> {
			if (err != null) {
				Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
				src.sendError(Text.literal("Couldn't add skin: " + cause.getMessage()));
				return;
			}
			storage().putSkin(skin);
			ok(src, "Added \"" + skin.name + "\" to the skin pool (" + storage().skinPool().size() + " skins).");
		}));
		return 1;
	}

	private static int removeFromPool(ServerCommandSource src, String name) throws CommandSyntaxException {
		if (!storage().removeSkin(name)) throw error("No skin named \"" + name + "\" in the pool.");
		ok(src, "Removed \"" + name + "\" from the pool. (Players already wearing it keep it until reset.)");
		return 1;
	}

	// ================================================================ helpers

	/** Pushes changes to online targets; returns how many were online. */
	private static int refreshAll(MinecraftServer server, List<Target> targets, boolean skinChanged) {
		int online = 0;
		for (Target t : targets) {
			if (server.getPlayerManager().getPlayer(t.uuid()) != null) {
				Refresher.refresh(server, t.uuid(), skinChanged);
				online++;
			}
		}
		return online;
	}

	private static String offlineNote(int total, int online) {
		int offline = total - online;
		return offline > 0 ? " (" + offline + " offline, applied when they join)" : "";
	}

	private static void ok(ServerCommandSource src, String message) {
		src.sendFeedback(() -> Text.literal(message), true);
	}

	private static CommandSyntaxException error(String message) {
		return new SimpleCommandExceptionType(Text.literal(message)).create();
	}
}
