# Fusionmon quest chapter for FTB Quests

A ready-made FTB Quests chapter (9 quests) for Fusionmon: get an injector, first fusion, reverse,
unfuse, get a fusion from a raid or the wild, and discover 10 / 25 / 50 / 100 fusions. The tasks are
Fusionmon's own advancements (`fusionmon:first_fusion`, `fusionmon:discover_10`, …), so they complete
on their own. Made for FTB Quests 2101.1.x (Minecraft 1.21.1).

## Install (modpack makers)

1. Copy `chapters/fusionmon.snbt` to `config/ftbquests/quests/chapters/`.
2. Open `config/ftbquests/quests/lang/en_us.snbt` and paste the lines of `lang-en_us-lines.snbt` just
   before its last `}`. **Don't replace that file**: it holds the text of every other quest in the pack.
   (If the pack has no `lang` folder yet, create `lang/en_us.snbt` containing `{`, the lines, and `}`.)
3. In game, as an op: `/ftbquests reload`.

The chapter shows up as "Fusionmon"; move it, change rewards or add quests with the normal in-game
editor. The ids are fixed, so pasting it twice into the same pack overwrites the first copy instead of
duplicating it.

## Rewards

Poké Balls, Fusion Injectors, Rare Candy, Exp. Candy L, an Ability Capsule and a Master Ball (for 100
fusions). Change them freely.
