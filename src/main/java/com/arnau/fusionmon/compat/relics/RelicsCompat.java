package com.arnau.fusionmon.compat.relics;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.fusion.FusionData;
import com.arnau.fusionmon.fusion.FusionRequest;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import kotlin.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Reliquias de Fusionmon para el mod Relics (opcional): solo existen si Relics está instalado. Esta clase y las de las
 * reliquias heredan de clases de Relics: Fusionmon solo llega aquí después de comprobar ModList (ver Fusionmon), así
 * que sin Relics nunca se cargan.
 *
 * Las cuatro (se llevan puestas en Curios, suben de nivel y se investigan como las de Relics; se encuentran en cofres,
 * como ellas):
 *  - Amuleto de Corazones Gemelos (collar): más experiencia para las fusiones del equipo
 *  - Brújula de Fusión (amuleto): Pokémon salvajes que aparecen fusionados cerca de ti
 *  - Monóculo Prisma (cabeza): más shinies cerca de ti
 *  - Guante Trampa (manos): más probabilidad de captura
 */
public final class RelicsCompat {

    public static final String MOD_ID = "relics";
    /** Hasta dónde llega el efecto de la brújula y del monóculo (Cobblemon hace aparecer Pokémon a 24-64 bloques). */
    private static final double SPAWN_RADIUS = 64;
    private static final double EXPERIENCE_FOR_FUSION = 10;
    private static final double EXPERIENCE_FOR_SHINY = 25;

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Fusionmon.MOD_ID);

    public static final DeferredItem<TwinHeartAmuletItem> TWIN_HEART_AMULET =
            ITEMS.register("twin_heart_amulet", TwinHeartAmuletItem::new);
    public static final DeferredItem<FusionCompassItem> FUSION_COMPASS =
            ITEMS.register("fusion_compass", FusionCompassItem::new);
    public static final DeferredItem<PrismMonocleItem> PRISM_MONOCLE =
            ITEMS.register("prism_monocle", PrismMonocleItem::new);
    public static final DeferredItem<SnareGloveItem> SNARE_GLOVE =
            ITEMS.register("snare_glove", SnareGloveItem::new);

    private static final List<Supplier<? extends Item>> ALL = List.of(TWIN_HEART_AMULET, FUSION_COMPASS,
            PRISM_MONOCLE, SNARE_GLOVE);

    private RelicsCompat() {
    }

    /** Desde el constructor de Fusionmon, solo con Relics instalado: registra los objetos. */
    public static void initialize(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                for (Supplier<? extends Item> item : ALL) {
                    event.accept(item.get());
                }
            }
        });
    }

    /** Desde Fusionmon.hookCobblemon (hilo principal), solo con Relics instalado: los efectos. */
    public static void hookCobblemon() {
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.NORMAL, event -> {
            safely("el Amuleto de Corazones Gemelos", () -> {
                int boosted = twinHeart(event.getPokemon(), event.getExperience());
                if (boosted != event.getExperience()) {
                    event.setExperience(boosted);
                }
            });
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_CATCH_RATE.subscribe(Priority.NORMAL, event -> {
            safely("el Guante Trampa", () -> {
                if (event.getThrower() instanceof ServerPlayer player) {
                    event.setCatchRate(snare(player, event.getPokemonEntity(), event.getCatchRate()));
                }
            });
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
            safely("el Guante Trampa", () -> snareCaptured(event.getPlayer(), event.getPokemon()));
            return Unit.INSTANCE;
        });
        // Antes de añadir al mundo cada Pokémon que hace aparecer Cobblemon (apariciones naturales)
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.LOW, event -> {
            if (event.getEntity() instanceof PokemonEntity entity) {
                safely("la Brújula de Fusión o el Monóculo Prisma", () -> wildSpawn(entity));
            }
            return Unit.INSTANCE;
        });
    }

    private static void safely(String where, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError e) {
            // Una versión de Relics distinta (su API cambia mucho) no debe tumbar Cobblemon: se pierde la reliquia
            FusionSafety.failed(where, e);
        }
    }

    // ---- Amuleto de Corazones Gemelos ----

    private static int twinHeart(Pokemon pokemon, int experience) {
        ServerPlayer owner = pokemon.getOwnerPlayer();
        if (owner == null || experience <= 0) {
            return experience;
        }
        TwinHeartAmuletItem amulet = TWIN_HEART_AMULET.get();
        PokeRelicItem.Worn worn = amulet.best(owner, "bonus");
        if (worn == null) {
            return experience;
        }
        boolean fusion = FusionData.isFusion(pokemon);
        double bonus = fusion ? worn.value() : worn.rank("kinship") ? worn.value() / 2 : 0;
        if (bonus <= 0) {
            return experience;
        }
        if (fusion) {
            amulet.reward(owner, worn, "fusion_experience", 1, "boosted_gains");
        }
        return (int) Math.round(experience * (1 + bonus));
    }

    // ---- Guante Trampa ----

    private static float snare(ServerPlayer player, PokemonEntity target, float catchRate) {
        PokeRelicItem.Worn worn = SNARE_GLOVE.get().best(player, "bonus");
        if (worn == null) {
            return catchRate;
        }
        double bonus = worn.value();
        if (worn.rank("fusion_snare") && target != null && FusionData.isFusion(target.getPokemon())) {
            bonus *= 2;
        }
        return (float) (catchRate * (1 + bonus));
    }

    private static void snareCaptured(ServerPlayer player, Pokemon pokemon) {
        SnareGloveItem glove = SNARE_GLOVE.get();
        PokeRelicItem.Worn worn = glove.best(player, "bonus");
        if (worn != null) {
            boolean fusion = FusionData.isFusion(pokemon);
            glove.reward(player, worn, fusion ? "fusion_capture" : "capture", fusion ? 3 : 1, "assisted_captures");
        }
    }

    // ---- Brújula de Fusión y Monóculo Prisma: Pokémon salvajes que aparecen cerca ----

    private static void wildSpawn(PokemonEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Pokemon pokemon = entity.getPokemon();
        if (pokemon == null || !pokemon.isWild() || FusionData.isFusion(pokemon)) {
            return;
        }
        FusionCompassItem compass = FUSION_COMPASS.get();
        PrismMonocleItem monocle = PRISM_MONOCLE.get();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // El mejor de cada reliquia entre los jugadores cercanos (y quién la lleva, para darle la experiencia)
        PokeRelicItem.Worn compassWorn = null;
        ServerPlayer compassOwner = null;
        PokeRelicItem.Worn monocleWorn = null;
        ServerPlayer monocleOwner = null;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity) > SPAWN_RADIUS * SPAWN_RADIUS) {
                continue;
            }
            PokeRelicItem.Worn worn = compass.best(player, "chance");
            if (worn != null) {
                compass.reward(player, worn, "nearby_spawn", 1, null);
                if (compassWorn == null || worn.value() > compassWorn.value()) {
                    compassWorn = worn;
                    compassOwner = player;
                }
            }
            worn = monocle.best(player, "multiplier");
            if (worn != null) {
                monocle.reward(player, worn, "nearby_spawn", 1, null);
                if (monocleWorn == null || worn.value() > monocleWorn.value()) {
                    monocleWorn = worn;
                    monocleOwner = player;
                }
            }
        }
        boolean changed = false;

        if (compassWorn != null && random.nextDouble() < compassWorn.value()) {
            Species body = randomBody(pokemon, compassWorn.rank("kindred"));
            if (body != null && FusionRequest.fuseWith(pokemon, body.getStandardForm())) {
                compass.reward(compassOwner, compassWorn, "wild_fusion", EXPERIENCE_FOR_FUSION, "wild_fusions");
                changed = true;
            }
        }

        if (monocleWorn != null && !pokemon.getShiny()) {
            // El monóculo multiplica la probabilidad: Cobblemon ya ha tirado una vez (1 entre shinyRate), aquí se da
            // la diferencia hasta "multiplicador entre shinyRate"
            double multiplier = monocleWorn.value();
            if (monocleWorn.rank("fusion_shine") && FusionData.isFusion(pokemon)) {
                multiplier *= 2;
            }
            float shinyRate = Math.max(1F, Cobblemon.INSTANCE.getConfig().getShinyRate());
            if (random.nextDouble() < (multiplier - 1) / shinyRate) {
                pokemon.setShiny(true);
                monocle.reward(monocleOwner, monocleWorn, "shiny", EXPERIENCE_FOR_SHINY, "shinies_revealed");
                changed = true;
            }
        }

        if (changed) {
            // La entidad copió los aspects del Pokémon al crearse: se le dan los nuevos (fusión, shiny) antes de que
            // entre en el mundo; así también se recalcula su tamaño (PokemonEntityMixin)
            entity.getEntityData().set(PokemonEntity.Companion.getASPECTS(), new HashSet<>(pokemon.getAspects()));
            entity.refreshDimensions();
        }
    }

    /**
     * Un cuerpo al azar para una fusión salvaje: ni legendarios ni míticos ni ultraentes (serían una forma barata de
     * conseguirlos), y con "kindred", que comparta algún tipo con la cabeza.
     */
    private static Species randomBody(Pokemon head, boolean sameType) {
        Set<ElementalType> headTypes = new HashSet<>();
        head.getSpecies().getTypes().forEach(headTypes::add);
        List<Species> candidates = new ArrayList<>();
        for (Species species : PokemonSpecies.getImplemented()) {
            if (species == head.getSpecies() || species.getLabels().contains("legendary")
                    || species.getLabels().contains("mythical") || species.getLabels().contains("ultra_beast")) {
                continue;
            }
            if (sameType) {
                boolean shares = false;
                for (ElementalType type : species.getTypes()) {
                    shares |= headTypes.contains(type);
                }
                if (!shares) {
                    continue;
                }
            }
            candidates.add(species);
        }
        return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
