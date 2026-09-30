# UnstableCore (Fabric) — Immortality

Fabric port of the immortality feature from the UnstableCore Paper plugin, for Minecraft 1.21.11.

## Commands
- `/immortal` or `/god` — toggle immortality for yourself
- `/immortal <player>` — toggle it for someone else

Both are ops-only (permission level 2).

## Behaviour
- Immortal players can't die: fatal damage leaves them at `minHealth` (default 0.5 health = a quarter heart), and other hits can't take them below it.
- With `smartTotem` on (default), holding a Totem of Undying in either hand switches this off so the totem pops normally.
- Falling into the void sends them to world spawn.
- Their food bar stops going down once it's at 6 (3 drumsticks) or lower.
- Who is immortal is saved in `config/unstablecore/immortal-players.json`, so it survives restarts.

## Config
`config/unstablecore/immortality.json` (created on first start; restart the server after editing):

    { "minHealth": 0.5, "smartTotem": true }

## Building
Push to GitHub; the `build` workflow produces the jar under the run's **Artifacts** download.
Use `unstablecore-fabric-1.0.0.jar`, not the `-sources` jar.
