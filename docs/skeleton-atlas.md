# Skeleton Atlas

Fusionmon builds every fusion by taking the **body** model and attaching the **head** of the other species, plus
its tail, wings, arms and legs where it makes sense. To do that it has to know which bone of each model is the head,
which ones are legs, and so on.

Bone names don't follow any convention. Cobblemon uses `head`, `head_ai` or `head_angle`, packs like AllTheMons use
`leftleg` or `humanarm`, and Blockbench leaves `bone12`. So besides the names, Fusionmon also reads the **animations**
of every model. This is the skeleton atlas:

| Part | How it is found |
|---|---|
| Head | The bone the model turns to **look at the player** (`q.look(...)` in the poser). If one of those bones holds `locator_head`, that one. |
| Legs | Bones that **swing when walking** and reach the ground, pointing down from the hip. They come in left/right pairs. |
| Arms | Bones that swing when walking on the sides, from the upper half of the body, in pairs. |
| Wings | Bones that **flap when flying**, coming out of the back half of the body, in pairs. |
| Tail | A bone in the middle, behind the body, that **sways** and points backwards. |

Posers that animate limbs in code count as declared and win over motion: `q.biped_walk`, `q.quadruped_walk`,
`q.bimanual_swing` and `q.sine_wing_flap` (with their default bone names if none are given).

The animations are run with Cobblemon's own animation engine, so any model from Cobblemon, an addon or a resource pack
works the same way, and renamed bones in a future Cobblemon version don't break anything.

## How it is used

By default (`fill`) the bone names come first and the atlas fills in what they don't find: the head of models with no
`head`/`locator_head` bone (Hydrapple's `head_ai` in Cobblemon 1.8.2, Minior...), legs and wings. Arms and tails found
**only** by motion are not used by default: run over all Cobblemon models, swinging "arms" were often fins or scarves and
swaying "tails" were often hair or leaves (see `docs/species/skeleton-atlas.md`).

Development commands (`runClient`):

- `/fusionvisual atlas off|fill|prefer`: names only; names first and the atlas for the rest (default); the atlas first.
- `/fusioninspect`: the species inspector shows a "Skeleton atlas" line with what it found and where it came from
  (`look`, `declared`, `motion`, `pack`).
- `/fusionatlas export`: writes the atlas of every base model to `run/fusionmon/skeleton-atlas.json`, in the same format
  as the corrections below.

## Correcting a model in a resource pack

When neither the names nor the animations get a model right, a resource pack can say what each bone is. Add a JSON file
under `assets/<any namespace>/fusionmon/skeletons/`:

```json
{
  "models": ["cobblemon:delibird.geo"],
  "head": "head",
  "legs": ["leg_left", "leg_right"],
  "arms": [],
  "wings": ["arm_left", "arm_right"],
  "tail": ["tail"]
}
```

- `models`: the `.geo` model ids it applies to (the `model` of the resolver variation).
- Every key is optional. A key you leave out is worked out as usual; an empty list means "this model has none"; `"head":
  ""` means the model has no head (it is fused as a whole-body Pokémon, like Voltorb).
- List the top bone of each part (the hip, the shoulder, the wing root): everything hanging from it goes with it.
- Corrections win over names and animations, except with `/fusionvisual atlas off`. They are reloaded with the
  resource packs (F3+T).

A full example is in `examples/skeleton-override-example` (Delibird's arms count as wings). `/fusionatlas export` is a
good starting point: copy a model's entry and fix what is wrong.

## For developers

- `client/model/atlas/SkeletonClassifier`: the rules, pure Java (no Minecraft or Cobblemon classes).
- `client/model/atlas/SkeletonAtlas`: feeds it from a live `PosableModel` (bones at rest, look bones, declared limbs and
  24 samples of the stand/walk/fly animations) and caches the result per model.
- `client/model/atlas/SkeletonOverrides`: the resource pack corrections.
- `FusionGraft.findHeads`: where the atlas is used (`atlasParts`, `atlasHead`, `atlasTail`, `atlasWings`).
- `tools/skeleton-atlas/SkeletonAtlasCheck.java`: runs the same classifier over every model outside the game and writes
  `docs/species/skeleton-atlas.{md,csv}`. It only needs a JDK 21+:

```bash
javac -d build/skeleton-atlas tools/skeleton-atlas/SkeletonAtlasCheck.java src/client/java/com/arnau/fusionmon/client/model/atlas/SkeletonClassifier.java
```

```bash
java -cp build/skeleton-atlas SkeletonAtlasCheck docs/species <Cobblemon jar or resource pack folders/zips>
```

  The Cobblemon jar is in the Gradle cache (`~/.gradle/caches/modules-2/files-2.1/com.cobblemon/neoforge/`). It
  evaluates Molang with a small built-in evaluator, so the numbers are close to, but not exactly, what the game sees.
  `ATLAS_DEBUG=<geometry name>` prints every bone of one model with how much it moves.
