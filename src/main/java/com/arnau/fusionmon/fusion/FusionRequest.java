package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fusiones que no hace un jugador: las pide quien crea el Pokémon (comandos, jefes de incursión de Cobblemon Raid Dens,
 * equipos de entrenadores de Radical Cobblemon Trainers, apariciones...) con "fusion_body=<especie>". Todos esos mods
 * crean sus Pokémon con las PokemonProperties de Cobblemon, así que hay tres formas de escribirlo y valen todas:
 *
 *  - propiedad de Cobblemon (comandos, apariciones): "/pokespawn pikachu fusion_body=charizard"
 *  - aspect (Radical Cobblemon Trainers "aspects", Raid Dens "aspects"): "fusion_body=charizard"
 *  - característica de especie (Raid Dens "custom_properties"): {"name": "fusion_body", "value": "charizard"}
 *
 * El valor es la especie del cuerpo ("charizard", "cobblemon:charizard", "random"), con forma opcional detrás de una
 * barra: "raichu/alolan".
 *
 * Cobblemon crea un Pokémon así: new Pokemon() → apply(propiedades) → initialize() (movimientos de su especie) →
 * tiradas (shiny...). En apply todavía no está completo (sin movimientos; las propiedades que van detrás aún no se han
 * puesto), así que ahí solo se apunta la petición (PENDING en persistentData) y se fusiona al terminar initialize
 * (PokemonPropertiesMixin). Si se aplican propiedades a un Pokémon que ya existe (que ya tiene movimientos), se
 * fusiona al terminar apply.
 */
public final class FusionRequest {

    public static final String KEY = "fusion_body";
    /** Prefijo del aspect que pide una fusión. */
    public static final String ASPECT_PREFIX = KEY + "=";
    /** Petición pendiente, en persistentData. Se quita al fusionar (si no, las partes guardadas la llevarían dentro). */
    private static final String PENDING = "fusionmon_pending_body";
    private static final String RANDOM = "random";

    private FusionRequest() {
    }

    /** Registra la propiedad fusion_body en Cobblemon (comandos, apariciones, datapacks). En el hilo principal. */
    public static void registerProperty() {
        CustomPokemonProperty.Companion.register(new FusionBodyProperty.Type());
    }

    /** Apunta en el Pokémon que tiene que fusionarse con ese cuerpo cuando esté completo. */
    static void mark(Pokemon pokemon, String body) {
        CompoundTag data = pokemon.getPersistentData();
        if (data != null && body != null && !body.isBlank()) {
            data.putString(PENDING, body.trim());
        }
    }

    /**
     * Al terminar PokemonProperties.apply: busca la petición entre los aspects y las características de especie de las
     * propiedades (la propiedad fusion_body ya la ha apuntado en su apply). Si el Pokémon ya estaba completo, fusiona.
     */
    public static void afterApply(PokemonProperties properties, Pokemon pokemon) {
        try {
            String body = fromProperties(properties);
            if (body != null) {
                mark(pokemon, body);
            }
            // new Pokemon() aún no tiene movimientos: ese lo termina initialize (y se fusiona entonces)
            if (!pokemon.getMoveSet().getMoves().isEmpty()) {
                complete(pokemon);
            }
        } catch (RuntimeException e) {
            FusionSafety.failed("pedir una fusión con fusion_body", e);
        }
    }

    /**
     * Al poner los aspects forzados de un Pokémon (Radical Cobblemon Trainers: "aspects" de cada Pokémon del equipo).
     * El aspect de la petición se quita (es una orden, no un aspecto que pintar) y se fusiona ya: RCT pone los aspects
     * después de especie, nivel, IVs y EVs, y los movimientos que añade después se quedan en la fusión.
     */
    public static void afterForcedAspects(Pokemon pokemon, Set<String> aspects) {
        try {
            String body = null;
            for (String aspect : aspects) {
                if (aspect.toLowerCase(Locale.ROOT).startsWith(ASPECT_PREFIX)) {
                    body = aspect.substring(ASPECT_PREFIX.length());
                }
            }
            if (body == null) {
                return;
            }
            Set<String> rest = new HashSet<>();
            for (String aspect : aspects) {
                if (!aspect.toLowerCase(Locale.ROOT).startsWith(ASPECT_PREFIX)) {
                    rest.add(aspect);
                }
            }
            // Vuelve a entrar aquí, pero ya sin la petición
            pokemon.setForcedAspects(rest);
            mark(pokemon, body);
            complete(pokemon);
        } catch (RuntimeException e) {
            FusionSafety.failed("pedir una fusión con el aspect fusion_body", e);
        }
    }

    /** La petición escrita como aspect o característica en unas propiedades, o null. */
    static String fromProperties(PokemonProperties properties) {
        for (String aspect : properties.getAspects()) {
            if (aspect.toLowerCase(Locale.ROOT).startsWith(ASPECT_PREFIX)) {
                return aspect.substring(ASPECT_PREFIX.length());
            }
        }
        for (CustomPokemonProperty property : properties.getCustomProperties()) {
            if (property instanceof FusionBodyProperty fusionBody) {
                return fusionBody.body();
            }
            // Raid Dens convierte "custom_properties" en características de especie de texto
            if (property instanceof StringSpeciesFeature feature && KEY.equals(feature.getName())) {
                return feature.getValue();
            }
        }
        return null;
    }

    /**
     * Si el Pokémon tiene una petición pendiente, lo convierte en la fusión (él es la cabeza). Solo en el hilo del
     * servidor: en el cliente también se crean Pokémon con propiedades (para pintarlos), y esos no son de verdad.
     */
    public static void complete(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        if (data == null || !data.contains(PENDING)) {
            return;
        }
        String request = data.getString(PENDING);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            return;
        }
        // Se quita antes de fusionar: las copias de las partes se guardan con su persistentData
        data.remove(PENDING);
        if (FusionData.isFusion(pokemon)) {
            return;
        }

        try {
            FormData bodyForm = parse(request);
            if (bodyForm == null) {
                Fusionmon.LOGGER.warn("Fusionmon: fusion_body={} no es ninguna especie; {} se queda sin fusionar",
                        request, pokemon.getSpecies().getName());
                return;
            }
            Pokemon body = bodyFor(pokemon, bodyForm);
            // Naturaleza y habilidad de la cabeza: las que haya elegido quien la pide
            FusionService.makeFusion(server.registryAccess(), pokemon, body, false, false);
        } catch (RuntimeException e) {
            FusionSafety.failed("crear una fusión pedida con fusion_body", e);
        }
    }

    /**
     * El cuerpo de una fusión pedida: mismo nivel, IVs, EVs y shiny que la cabeza. Así la fusión sale con lo que se
     * pidió (un jefe de incursión de nivel 70 con 6 IVs perfectos sigue siéndolo), porque nivel, IVs y EVs son la media.
     */
    private static Pokemon bodyFor(Pokemon head, FormData form) {
        Pokemon body = form.getSpecies().create(head.getLevel());
        if (form != form.getSpecies().getStandardForm()) {
            body.setForm(form);
        }
        for (Stat stat : FusionService.PERMANENT_STATS) {
            body.getIvs().set(stat, head.getIvs().getOrDefault(stat));
            body.getEvs().set(stat, head.getEvs().getOrDefault(stat));
        }
        body.setShiny(head.getShiny());
        return body;
    }

    /** "charizard", "cobblemon:charizard", "raichu/alolan" o "random" → forma, o null si no existe. */
    static FormData parse(String request) {
        String value = request.trim().toLowerCase(Locale.ROOT);
        String formName = null;
        int slash = value.indexOf('/');
        if (slash >= 0) {
            formName = value.substring(slash + 1);
            value = value.substring(0, slash);
        }

        Species species;
        if (RANDOM.equals(value)) {
            List<Species> implemented = PokemonSpecies.getImplemented();
            species = implemented.get(ThreadLocalRandom.current().nextInt(implemented.size()));
        } else {
            ResourceLocation id = value.contains(":") ? ResourceLocation.tryParse(value)
                    : ResourceLocation.tryBuild("cobblemon", value);
            species = id == null ? null : PokemonSpecies.getByIdentifier(id);
        }
        if (species == null) {
            return null;
        }
        if (formName == null || formName.isBlank()) {
            return species.getStandardForm();
        }
        for (FormData form : species.getForms()) {
            if (form.getName().equalsIgnoreCase(formName) || formName.equalsIgnoreCase(form.formOnlyShowdownId())) {
                return form;
            }
        }
        return species.getStandardForm();
    }

    /** Si unas propiedades piden una fusión: para los aspects de lo que se pinta a partir de ellas (FusionAspects). */
    public static Species requestedBody(PokemonProperties properties) {
        String body = fromProperties(properties);
        if (body == null || body.equalsIgnoreCase(RANDOM)) {
            return null;
        }
        FormData form = parse(body);
        return form == null ? null : form.getSpecies();
    }
}
