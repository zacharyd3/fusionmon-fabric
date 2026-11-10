package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.FusionSafety;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * Fusiones que llegan a un jugador sin que las haya creado él: capturadas (fusiones salvajes, jefes de incursión de Raid
 * Dens), el premio de una incursión, el Pokémon de un comando o de un NPC... Cobblemon avisa con POKEMON_GAINED cada
 * vez que un Pokémon entra en el equipo o el PC de un jugador; si es una fusión, cuenta como descubierta (Fusion Album)
 * y se avisa a los eventos y logros (FusionProgress.obtained).
 *
 * Fusionar, separar e invertir también mueven Pokémon del equipo: mientras FusionService está en ello (busy) no cuenta.
 */
public final class FusionObtained {

    /** Solo en el hilo del servidor: no hace falta nada más que un contador. */
    private static int busy;

    private FusionObtained() {
    }

    public static void register() {
        CobblemonEvents.POKEMON_GAINED.subscribe(Priority.LOW, event -> {
            gained(event.getPlayerId(), event.getPokemon());
            return Unit.INSTANCE;
        });
    }

    /** Para FusionService: lo que pase dentro de action no cuenta como conseguir una fusión. */
    static void whileFusing(Runnable action) {
        busy++;
        try {
            action.run();
        } finally {
            busy--;
        }
    }

    private static void gained(UUID playerId, Pokemon pokemon) {
        if (busy > 0) {
            return;
        }
        try {
            if (!FusionData.isFusion(pokemon)) {
                return;
            }
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                FusionProgress.obtained(player, pokemon);
            }
        } catch (RuntimeException e) {
            FusionSafety.failed("apuntar una fusión conseguida", e);
        }
    }
}
