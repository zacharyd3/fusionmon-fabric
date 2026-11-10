# Atlas de esqueletos (generado)

Generado por `tools/skeleton-atlas/SkeletonAtlasCheck.java` (no editar a mano). Lo que
`SkeletonClassifier` deduce de las **animaciones** de cada modelo: la cabeza es lo que mira al
jugador (`q.look`), las patas lo que se balancea al andar y llega al suelo, los brazos lo que se
balancea a los lados, las alas lo que aletea al volar, la cola lo que se mece detrás. Las
animaciones de código (`q.biped_walk`, `q.quadruped_walk`, `q.bimanual_swing`, `q.sine_wing_flap`)
cuentan como declaradas. Datos por modelo en `skeleton-atlas.csv`.

En el juego `FusionGraft` sigue usando primero los nombres (`docs/species/`) y el atlas rellena lo
que los nombres no encuentran (`/fusionvisual atlas fill`, por defecto). **Rellena** = el atlas lo
encuentra y una regla simple de nombres no.

Modelos analizados: **748** (modelos 1083, posers 758, grupos de animaciones 1009) · con error: 0

Sin analizar: 346 parejas poser + modelo cuyo poser no es un JSON (Cobblemon aún tiene algunos escritos en Kotlin) o cuyo modelo no aparece con ese nombre. En el juego (`SkeletonAtlas`) se analizan todos.

| Pieza | Modelos | Rellena |
|---|---:|---:|
| cabeza (mira) | 575 | 21 |
| patas | 335 | 4 |
| brazos | 197 | 24 |
| alas | 28 | 2 |
| cola | 165 | 9 |

Piezas por fuente: declared 588 motion 1109

## Cabezas que los nombres no encuentran (21)

aegislash (body), binacle (head_ai_left), bonsly (torso), brambleghast (eyes), bramblin (eyes), bronzor (body), doublade (body), drifblim (waist_rotation), dugtrio (body3_2), dugtrio_alolan (body3_2), honedge (body), hydrapple (head_ai), scovillain (head_ai_left), shroomish (torso), spritzee (torso), starmie (body), staryu (body), swirlix (torso_only), togedemaru (torso), vanilluxe (body), zweilous (head_left)

## Patas que los nombres no encuentran (4)

drampa (arm_fur1_left arm_fur2_left arm_fur1_right arm_fur2_right), octillery_male (tentacle_front_left tentacle_front_right tentacle_outer_back_left tentacle_outer_back_right tentacle_middle_back_left tentacle_middle_back_right tentacle_inner_back_left tentacle_inner_back_right), seaking_male (fin_right1 fin_left1), varoom (wheel_left wheel_right)

## Brazos que los nombres no encuentran (24)

arcanine (tail_fluff_left tail_fluff_right leg_front_left leg_front_right), ariados (leg_top_left leg_top_right leg_front2_left leg_front2_right leg_back2_left leg_back2_right), basculin_bluestripe (fin_right fin_left), basculin_redstripe (fin_right fin_left), basculin_whitestripe (fin_right fin_left), bouffalant (leg_front_left leg_front_right), cherrim_overcast (leaf_front_right leaf_front_left leaf_back_left leaf_back_right), dondozo (barbel_bottom_back_right barbel_bottom_back_left barbel_bottom_front_right barbel_bottom_front_left barbel_top_right3 barbel_top_left3 fin_right fin_left), dunsparce (wing_left wing_right), gogoat (right_scarf_top right_scarf_mid right_scarf_bottom left_scarf_top left_scarf_mid left_scarf_bottom), ivysaur_male (leaf_right leaf_left), joltik (body_fluff_back_right body_fluff_back_left), lumineon (fin_right fin_left fin_right2 fin_left2), mantine (wing_right3 wing_left3), mantyke (antenna_right2 antenna_left2 wing_right wing_left), revavroom (wheel_left wheel_right wheel_left2 wheel_right2), scolipede (leg_front_left leg_back_left leg_front_right leg_back_right), sharpedo (fin_right fin_left), skiddo (scarf_left1 scarf_left2 scarf_right1 scarf_right2 skirt_left2 skirt_right2 skirt_left1 skirt_right1), spinarak (legfront_left legfront_right legmiddle_left legmiddle_right legback_left legback_right), thievul (tail_flap_middle_right tail_flap_middle_left tail_flap_top_right tail_flap_top_left tail_flap_bottom_right tail_flap_bottom_left), torkoal (leg_front_left leg_front_right leg_back_left leg_back_right), wailmer (fin_right fin_left), yungoos (leg_front_left leg_front_right)

## Alas que los nombres no encuentran (2)

delibird (arm_right3 arm_right), haxorus (foot_left foot_right)

## Colas que los nombres no encuentran (9)

eelektross (segment7), gholdengo (belt_chest), ivysaur_male (leaf_back), magnezone (magnet_back_rotation), munchlax (fur_back), onix (segment5), orthworm (segment5), steelix_male (boulder3 boulder5 boulder6 segment7), swadloon (body_leaf_back)
