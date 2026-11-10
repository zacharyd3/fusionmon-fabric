# Fusionmon

A NeoForge mod for **Minecraft 1.21.1** that adds Pokémon fusions to **Cobblemon 1.7.3 – 1.8.x**, inspired by Pokémon Infinite Fusion.

Every pair is built on the fly, so any two species can be fused: their own model, name, types and stats.

## Features

- **Fusion Injector**: right-click it to pick two Pokémon from your party and fuse them, or pick a fusion to split it back apart or swap head and body. Each fusion, split or swap consumes one injector.
- **Fused model**: the body's model with the head grafted on, recolored with the head's palette. Tails, wings, horns, arms and legs are mixed too. Shiny if either part is shiny.
- **Infinite Fusion stats**: name, types and base stats follow Infinite Fusion's formulas. Level, IVs and EVs are averaged; you choose which nature and ability to keep.
- **Discovery**: a fusion you have never made appears as a silhouette with hidden stats; confirming it plays a fusion animation that reveals it.
- **Fusion Album**: sneak + right-click with the injector (or `/fusionalbum`) to browse the fusions you have discovered.
- Fusions battle with their fused types and stats, have the size and ride of their body, evolve through either part (level, items and trade with the Link Cable) and learn level-up moves from both.
- Splitting gives both Pokémon all the experience earned as a fusion.
- **Fusion cries**: a fusion cries with both voices, head and body blended. The grafted head opens its mouth, attacks,
  faints and sleeps along with the body.
- **Skeleton atlas**: besides bone names, Fusionmon reads each model's animations to tell heads, legs and wings apart,
  so models from addons and resource packs fuse better. Resource packs can correct any model (`docs/skeleton-atlas.md`).
- **World First** (opt-in for servers): the first trainer anywhere to discover a fusion gets a server-wide shout-out and
  a place on a shared leaderboard (`worldfirst/README.md`).

- **Advancements**: a Fusionmon advancement tab (first fusion, reverse, unfuse, catch a fusion, discover 10 / 25 / 50 /
  100 fusions), with a `fusionmon:fusion` criterion datapacks can use.
- **Fusions from anywhere**: `fusion_body=<species>` turns any Pokémon Cobblemon creates into a fusion:
  `/pokespawn pikachu fusion_body=charizard`, raid bosses, trainer teams…
- **Integrations** (all optional, see `docs/integrations.md`): fusion raid bosses and injector rewards for Cobblemon Raid
  Dens, fusions in Radical Cobblemon Trainers teams, `FusionmonEvents` for KubeJS, a ready-made FTB Quests chapter, and a
  "Fusion: head + body" line in Jade, and four leveling Cobblemon relics for Relics (fusion EXP, wild fusions, shinies, catch rate).

## Crafting

Fusion Injector (crafting table): redstone in the bottom-left corner, lapis lazuli in the center and one of copper ingot, iron ingot, gold ingot, diamond or netherite ingot in the top-right corner. The accepted materials are the item tag `fusionmon:fusion_injector_materials`, so datapacks can change them.

## Commands

- `/fusionalbum`: open your Fusion Album.
- `/fusionvisual graft|colors`: how fusions look on your screen (fused model, or just the head recolored).
- `/fusionvisual cries blend|plain`: fusions cry with both voices, or just one.
- `/worldfirst`: whether this server takes part in World First, and the global numbers. `/worldfirst name show|hide`:
  whether your discoveries carry your name.
- `/fusionmon info|unfuse <slot>` (operators): inspect or split the fusion in one of your party slots.

## Requirements

- Minecraft 1.21.1 with NeoForge 21.1
- Cobblemon 1.7.3 to 1.8.x (NeoForge version), with its dependency Kotlin for Forge

Install it on both the client and the server.

## Building

`./gradlew build` (the jar ends up in `build/libs/`). Gradle builds with a Java 21 toolchain; `./gradlew runClient` and
`./gradlew runServer` start a development game.

## License

Fusionmon is licensed under the [Mozilla Public License 2.0](LICENSE).
Pokémon is © Nintendo, Game Freak and The Pokémon Company. This is an unofficial fan project, not affiliated with or endorsed by them.
