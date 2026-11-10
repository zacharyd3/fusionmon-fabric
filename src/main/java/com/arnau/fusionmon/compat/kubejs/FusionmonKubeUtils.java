package com.arnau.fusionmon.compat.kubejs;

import com.arnau.fusionmon.fusion.FusionData;
import com.arnau.fusionmon.fusion.FusionDiscovery;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

/**
 * El objeto "Fusionmon" de los scripts de KubeJS (métodos estáticos):
 *   Fusionmon.isFusion(pokemon), Fusionmon.headSpecies(pokemon), Fusionmon.bodySpecies(pokemon),
 *   Fusionmon.discoveredCount(player)
 */
public final class FusionmonKubeUtils {

    private FusionmonKubeUtils() {
    }

    public static boolean isFusion(Pokemon pokemon) {
        return pokemon != null && FusionData.isFusion(pokemon);
    }

    /** "cobblemon:pikachu", o "" si no es una fusión. */
    public static String headSpecies(Pokemon pokemon) {
        return pokemon == null ? "" : FusionmonKubeEvents.species(FusionData.headForm(pokemon));
    }

    public static String bodySpecies(Pokemon pokemon) {
        return pokemon == null ? "" : FusionmonKubeEvents.species(FusionData.bodyForm(pokemon));
    }

    /** Cuántas parejas tiene el jugador en su Fusion Album. */
    public static int discoveredCount(ServerPlayer player) {
        return FusionDiscovery.count(player);
    }
}
