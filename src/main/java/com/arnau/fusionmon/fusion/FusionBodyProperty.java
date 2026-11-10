package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.api.properties.CustomPokemonPropertyType;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * La propiedad "fusion_body=<especie>" de Cobblemon: vale en todo lo que lee propiedades de Pokémon (/pokespawn,
 * /pokegive, /spawnpokemon, apariciones de datapacks, mods que crean Pokémon con propiedades). Ver FusionRequest.
 *
 * Como condición ("matches", p. ej. en filtros de apariciones o de misiones): el Pokémon es una fusión con ese cuerpo.
 */
public record FusionBodyProperty(String body) implements CustomPokemonProperty {

    @Override
    public String asString() {
        return FusionRequest.ASPECT_PREFIX + body;
    }

    @Override
    public void apply(Pokemon pokemon) {
        // Aquí el Pokémon aún puede estar a medio crear: solo se apunta (FusionRequest)
        FusionRequest.mark(pokemon, body);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        FormData bodyForm = FusionData.bodyForm(pokemon);
        if (bodyForm == null) {
            return false;
        }
        FormData wanted = FusionRequest.parse(body);
        return wanted != null && wanted.getSpecies() == bodyForm.getSpecies();
    }

    /** Lo que Cobblemon registra: las claves y cómo leer el valor. */
    public static final class Type implements CustomPokemonPropertyType<FusionBodyProperty> {

        @Override
        public Iterable<String> getKeys() {
            return List.of(FusionRequest.KEY);
        }

        @Override
        public boolean getNeedsKey() {
            return true;
        }

        @Override
        public FusionBodyProperty fromString(String value) {
            return value == null || value.isBlank() ? null : new FusionBodyProperty(value);
        }

        @Override
        public Collection<String> examples() {
            // Para el autocompletado de los comandos de Cobblemon
            List<String> names = new ArrayList<>();
            names.add("random");
            for (Species species : PokemonSpecies.getImplemented()) {
                names.add(species.getResourceIdentifier().getPath());
            }
            return names;
        }
    }
}
