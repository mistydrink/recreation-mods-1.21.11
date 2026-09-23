# Fabric server mods for Minecraft 1.21.1

Three separate **server-side** mods. Players don't need to install anything; vanilla clients work.

| Mod | What it does |
|---|---|
| `deathkick` | Kicks players when they die (with a per-player bypass list) and hides join/leave messages |
| `nickskins` | Mass nicknames (above heads, tab list, chat), saved groups, nickname reset, random skins from a pool |
| `smptools` | `/smp` rules that remove villagers, shulkers, End-ship elytras and ominous vaults |

## Building

You need **JDK 21**. From this folder, run:

```
./build-all.sh           (Windows: build-all.bat)
```

This builds all three and puts the jars in `jars/`. To build just one, run `./gradlew build` inside its
folder; the jar lands in `build/libs/`. Copy the jars you want plus
[Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1 into the server's `mods/` folder.
LuckPerms support (fabric-permissions-api) is bundled inside each jar.

The projects use Yarn mappings and target 1.21.1 exactly. Other 1.21.x patches change some of the
networking code, so they need version bumps and a few fixes.

---

## DeathKick

| Command | Effect |
|---|---|
| `/deathkick on` / `off` | Turn kick-on-death on or off |
| `/deathkick status` | Show current settings |
| `/deathkick bypass add <players>` | Never kick these players (names work for offline players too) |
| `/deathkick bypass remove <name>` | Take someone off the bypass list |
| `/deathkick bypass list` | Show the bypass list |
| `/deathkick reload` | Reload `config/deathkick.json` |

`config/deathkick.json`:

```json
{
  "enabled": true,
  "hideJoinMessages": true,
  "hideLeaveMessages": true,
  "kickDelayTicks": 20,
  "kickMessage": "&cYou died!&r\n\n&7{death}",
  "bypass": {}
}
```

- `{death}` is replaced with the death message.
- `&` color codes work.
- `kickDelayTicks: 20` is one second, so the death message shows in chat before the kick.

When a kicked player rejoins, they land on the respawn screen as normal.

---

## NickSkins

### Nicknames

The nickname **is** the player's name as far as every client knows. There is no armor stand or text
display floating above anyone. The server rewrites the name inside the player's profile before sending
it, so the vanilla name tag, the tab list and chat all show the nickname.

| Command | Effect |
|---|---|
| `/nick set <targets> <name>` | Give everyone matched the same nickname, e.g. `/nick set @a[team=red] Red Guard` |
| `/nick numbered <targets> <prefix>` | Numbered nicknames: `Guard 1`, `Guard 2`, … |
| `/nick reset <targets>` | Restore real names |
| `/nick resetall` | Restore every real name, including offline players |
| `/nick list` | Show who is nicknamed or skinned |

`<targets>` is any vanilla selector: `@a`, `@a[tag=hunters]`, `@a[team=blue]`, or a single name.

### Saved groups

Groups are saved by UUID, so they can include offline players. Anything applied to a group reaches
offline members the next time they join.

| Command | Effect |
|---|---|
| `/nickgroup create <group>` / `delete <group>` | Create or delete a group |
| `/nickgroup add <group> <players>` | Add players (online or offline names) |
| `/nickgroup remove <group> <player>` | Remove a player |
| `/nickgroup list [group]` | List groups, or the members of one |
| `/nick group <group> set <name>` | Nickname the whole group |
| `/nick group <group> numbered <prefix>` | Numbered nicknames for the group |
| `/nick group <group> reset` | Restore the group's real names |
| `/skin group <group> random` / `set <skin>` / `reset` | Skins for the whole group |

### Skins

| Command | Effect |
|---|---|
| `/skin random <targets>` | Deal random skins from the pool. Nobody repeats until the pool runs out |
| `/skin set <targets> <skin>` | Give a specific pool skin |
| `/skin reset <targets>` / `/skin resetall` | Restore real skins |
| `/skin pool list` | List skins in the pool |
| `/skin pool add <username> [name]` | Copy a real account's current skin into the pool |
| `/skin pool remove <skin>` | Remove a skin from the pool |
| `/nickskins reload` | Reload `config.json` and `skins.json` |

#### Filling the pool with the default skins (read this)

Since 1.20.2, vanilla clients only show another player's skin if **Mojang has signed it**. A
server can't simply say "use the Kai skin"; it needs signed texture data for each skin. So the pool
starts empty, and you fill it once.

**Quick start (Steve and Alex).** The long-standing `MHF_Steve` and `MHF_Alex` accounts wear the
classic Steve and Alex skins:

```
/skin pool add MHF_Steve steve
/skin pool add MHF_Alex alex
```

**All nine default characters (18 skins, wide and slim arms).**

1. Open `.minecraft/versions/1.21.1/1.21.1.jar` as a zip.
2. Find the PNGs in `assets/minecraft/textures/entity/player/wide/` and `.../slim/`. They are Alex, Ari, Efe, Kai, Makena, Noor, Steve, Sunny and Zuri.
3. Upload each PNG to a skin-signing service such as MineSkin (mineskin.org). Pick the *slim* model for files from the `slim` folder.
4. Copy the **value** and **signature** it gives you into `config/nickskins/skins.json`:

```json
[
  { "name": "kai_wide",  "value": "ewogICJ0aW1lc3RhbXAiIDog...", "signature": "Kx9f2...==" },
  { "name": "noor_slim", "value": "ewogICJ0aW1lc3RhbXAiIDog...", "signature": "aQ3pL...==" }
]
```

5. Run `/nickskins reload`.

You only do this once; the file is reused forever. `/skin pool add <username>` also works for any
account whose current skin you want in the pool.

### Config: `config/nickskins/config.json`

```json
{ "refreshOwnSkin": true }
```

When a player's skin changes, everyone else sees it instantly. For the player's *own* client to show
it (F5, inventory preview), the mod quietly respawns them in place. Their inventory, health, XP,
effects and position are re-sent, and any open container is closed. Set this to `false` if it
conflicts with another mod; players will then see their own new skin after relogging.

### Known limitations

- **Nickname length and color.** Nicknames are capped at **16 characters**, Minecraft's hard limit for names above heads. They also can't be colored above heads.
- **Scoreboard teams above heads.** Team colors and team `nametagVisibility` don't apply above a nicked player's head, because the client matches teams by name. The tab list and chat keep team colors.
- **Commands still use real names.** Commands that take a player *name* still need the real one, or a selector like `@p`.
- **Server logs.** The console and logs always show real names.

---

## SMP Tools

Each rule stays on through restarts until you turn it off. **Back up the world before turning one on**:
anything removed is deleted for good, and turning a rule off only stops future removals.

| Command | What it removes |
|---|---|
| `/smp remove_villagers [on\|off]` | Every villager, including ones from breeding, curing and spawn eggs |
| `/smp remove_shulkers [on\|off]` | Every shulker, including End city spawns and shulkers created by duplication |
| `/smp remove_elytras [on\|off]` | The elytra from item frames **in the End**. The frame stays; frames in other dimensions are untouched |
| `/smp remove_ominousvaults [on\|off]` | Ominous vaults in trial chambers become air. Regular vaults are untouched |
| `/smp status` | Show which rules are on |

Running a command with no `on`/`off` turns the rule on. When a rule is turned on:

- Everything already loaded is cleaned up right away. For ominous vaults, this means chunks near players.
- The rest disappears as its chunks load, including newly generated terrain.
- Anything new is removed the moment it appears.

What's left in players' inventories and chests is untouched. That existing supply is what becomes rare.

`config/smptools.json` also has `alsoRemoveWanderingTraders` (default `false`), which removes wandering
traders while `remove_villagers` is on.

While a rule is on, it also removes things admins spawn, such as a `/summon villager`.

---

### Permissions

These work with LuckPerms. Without it, the fallback is op level 2.

| Node | Controls |
|---|---|
| `deathkick.command` | `/deathkick` |
| `deathkick.bypass` | Never kicked on death (default: nobody) |
| `nickskins.nick` | `/nick` |
| `nickskins.group` | `/nickgroup` |
| `nickskins.skin` | `/skin` |
| `nickskins.admin` | `/nickskins reload` |
| `smptools.command` | `/smp` |
