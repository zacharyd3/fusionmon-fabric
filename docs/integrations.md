# Fusionmon integrations (for modpack makers and server owners)

Everything here is optional: Fusionmon works the same without these mods. Each part is only loaded when
the other mod is installed. Tested against the versions in **All the Mons** (Cobblemon 1.7.3, NeoForge
1.21.1); the jar also works with Cobblemon 1.8.x.

| Mod | What you get |
|---|---|
| Cobblemon Raid Dens | 14 fusion raid bosses (tiers 1–6) and Fusion Injectors in raid rewards |
| Radical Cobblemon Trainers | Trainers can use fusions in their teams |
| KubeJS | `FusionmonEvents.*` script events and a `Fusionmon` helper object |
| FTB Quests | Fusionmon advancements as quest tasks + a ready-made chapter |
| Jade | "Fusion: Pikachu + Charizard" when you look at a fusion |
| Relics | Four Cobblemon relics that level up: EXP for fusions, wild fusions, shinies, catch rate |

## `fusion_body`: making a fusion from anywhere

Any Pokémon created through Cobblemon can be turned into a fusion by asking for a body. The Pokémon you
describe is the **head**; `fusion_body` is the **body**. Value: a species (`charizard`,
`cobblemon:charizard`), optionally with a form (`raichu/alolan`), or `random`.

The body gets the same level, IVs, EVs and shiny as the head, so the fusion keeps exactly what you asked
for (level, IVs and EVs of a fusion are the average of both parts). Nature, ability and moves are the
head's; the body's moves become relearnable.

**Commands** (Cobblemon property):
```
/pokespawn pikachu level=30 fusion_body=charizard
/pokegive charizard fusion_body=gengar shiny=yes
```

**Raid Dens boss** (`data/<namespace>/raid/boss/<name>.json`), in `custom_properties`:
```json
{
  "pokemon": {
    "species": "charizard",
    "moves": ["flamethrower", "shadowball", "airslash", "sludgebomb"],
    "custom_properties": [{ "name": "fusion_body", "value": "gengar" }]
  },
  "raid_tier": "TIER_FIVE",
  "raid_type": "FIRE",
  "weight": 20.0
}
```
Both the boss and the Pokémon you catch at the end are fusions.

**Radical Cobblemon Trainers** (`data/<namespace>/trainers/<id>.json`), in the Pokémon's `aspects`:
```json
{
  "name": "Fusion Fanatic Rin",
  "team": [
    {
      "species": "pikachu",
      "level": 35,
      "nature": "timid",
      "ability": "static",
      "moveset": ["thunderbolt", "flamethrower", "quickattack", "irontail"],
      "aspects": ["fusion_body=charizard"],
      "ivs": {},
      "evs": {}
    }
  ]
}
```
(New RCT trainers also need a `mobs/trainers` entry to spawn; see the RCT docs.)

As a condition (`matches`), `fusion_body=charizard` means "is a fusion whose body is Charizard".

## Cobblemon Raid Dens

When Raid Dens is installed, Fusionmon adds a built-in datapack, `fusionmon:datapacks/raid_dens`. It's
on by default, in new and existing worlds:

- **14 fusion bosses**: Pikachu + Charmander and Bulbasaur + Squirtle (tier 1) up to Charizard + Gengar,
  Garchomp + Scizor, Dragonite + Tyranitar… (tier 5) and Mewtwo + Lucario (tier 6). Same weight as a
  normal boss.
- **Raid rewards**: every raid reward roll has a 60% chance of 1–3 Fusion Injectors.

Turn it off with `/datapack disable "fusionmon:datapacks/raid_dens"`. To change it, copy the files from
the Fusionmon jar (`datapacks/raid_dens/`) into your own datapack.

Catching a fusion (from a raid, the wild, a trainer gift or `/pokegive`) adds it to the player's Fusion
Album and counts for the "Found in the Wild" advancement.

## KubeJS

Server scripts (`kubejs/server_scripts/*.js`):

```js
// Block legendary fusions on this server
FusionmonEvents.beforeFuse(event => {
  if (event.headSpecies == 'cobblemon:mewtwo' || event.bodySpecies == 'cobblemon:mewtwo') {
    event.setCancelMessage(Text.red('Mewtwo refuses to fuse!'))
    event.cancel()
  }
})

// Reward every new discovery
FusionmonEvents.discovered(event => {
  event.player.give('cobblemon:rare_candy')
  if (event.total % 10 == 0) event.server.tell(`${event.player.username} has discovered ${event.total} fusions!`)
})

FusionmonEvents.fused(event => {
  event.player.tell(`You made ${event.fusionName} (${event.headSpecies} + ${event.bodySpecies})`)
})
```

| Event | Fields | Cancel? |
|---|---|---|
| `beforeFuse` | `player`, `head`, `body`, `headSpecies`, `bodySpecies`, `reversed`, `setCancelMessage(text)` | yes, before the injector is spent |
| `beforeUnfuse` | `player`, `fusion`, `setCancelMessage(text)` | yes |
| `fused` | `player`, `fusion`, `fusionName`, `headSpecies`, `headForm`, `bodySpecies`, `bodyForm`, `reversed` | no |
| `unfused` | `player`, `head`, `body` | no |
| `discovered` | `player`, `headSpecies`, `bodySpecies`, `total` | no |
| `obtained` | `player`, `fusion` (caught, raid reward, gift…) | no |

Species are strings like `"cobblemon:charizard"`. Helper object: `Fusionmon.isFusion(pokemon)`,
`Fusionmon.headSpecies(pokemon)`, `Fusionmon.bodySpecies(pokemon)`,
`Fusionmon.discoveredCount(player)`.

Java mods can listen to the same events on `NeoForge.EVENT_BUS`: `com.arnau.fusionmon.api.FusionmonEvent`
(`BeforeFuse`, `BeforeUnfuse`, `Fused`, `Unfused`, `Discovered`, `Obtained`).

## Advancements and FTB Quests

Fusionmon adds an advancement tab: first fusion, reverse, unfuse, get a fusion you didn't make, and
discover 10 / 25 / 50 / 100 fusions. They all use the criterion `fusionmon:fusion`, which datapacks can
use too:

```json
"criteria": {
  "charizard_body": {
    "trigger": "fusionmon:fusion",
    "conditions": { "action": "fuse", "body": "charizard" }
  }
}
```
Conditions (all optional): `action` (`fuse`, `reverse`, `unfuse`, `discover`, `obtain`),
`min_discovered` (pairs in the Fusion Album), `head`, `body` (species).

FTB Quests has "Advancement" tasks, so quests can use these directly. A complete chapter is in
[`examples/ftbquests-chapter`](../examples/ftbquests-chapter) (install steps in its README).

## Relics

With Relics installed, Fusionmon adds four relics. Like Relics' own, they're found in chests, worn in Curios slots,
unlocked and researched in the relic's description screen, and level up as you play:

| Relic | Slot | Ability | Rank 3 bonus | Levels up from |
|---|---|---|---|---|
| Twin Heart Amulet | necklace | Fusions in your party gain +10–20% EXP (up to +75%) | Non-fusions get half the bonus | Fusions gaining EXP |
| Fusion Compass | charm | Wild Pokémon near you have 1–2% (up to 8%) chance to spawn as a fusion with a random non-legendary species | The body shares a type with the spawn | Spawns near you, wild fusions created |
| Prism Monocle | head | Wild Pokémon near you are 1.5–2× (up to 5×) as likely to be shiny | Wild fusions twice as likely again | Spawns near you, shinies revealed |
| Snare Glove | hands | Your Poké Balls get +10–15% (up to +50%) catch rate | Doubled against fusions | Captures (more for fusions) |

"Near you" means within 64 blocks. Values can be tuned in `config/fusionmon/relics/`, like any Relics config.

## Jade

Looking at a fusion shows a "Fusion: Pikachu + Charizard" line under its name. It's client side only
(works on any server) and can be turned off in Jade's settings (Plugins → Fusionmon → Fusion parts).
