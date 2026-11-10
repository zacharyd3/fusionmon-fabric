package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.advancement.FusionTrigger;
import com.arnau.fusionmon.api.FusionmonEvent;
import com.arnau.fusionmon.worldfirst.WorldFirst;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Lo que pasa "alrededor" de fusionar, separar o conseguir una fusión: el Fusion Album, World First, los eventos
 * públicos (api/FusionmonEvent: otros mods y KubeJS) y los logros (FusionTrigger, que usan las misiones de FTB Quests).
 * Un solo sitio para que todas las formas de fusionar avisen igual.
 *
 * Nada de esto puede deshacer una fusión ya hecha: los escuchadores son código de otros (scripts de modpacks), así que
 * cada aviso va en try/catch. Lo peor que pasa es que un logro o un script no se entere.
 */
public final class FusionProgress {

    private FusionProgress() {
    }

    /**
     * Pregunta a los demás mods y scripts si se puede fusionar (o invertir). Devuelve null si sí, o el mensaje para el
     * jugador si alguno lo ha cancelado. Llamar después de comprobarlo todo y ANTES de gastar el inyector.
     */
    public static Component beforeFuse(ServerPlayer player, Pokemon head, Pokemon body, boolean reversed) {
        return ask(new FusionmonEvent.BeforeFuse(player, head, body, reversed));
    }

    /** Igual, para separar. */
    public static Component beforeUnfuse(ServerPlayer player, Pokemon fusion) {
        return ask(new FusionmonEvent.BeforeUnfuse(player, fusion));
    }

    private static Component ask(FusionmonEvent.Cancellable event) {
        try {
            NeoForge.EVENT_BUS.post(event);
        } catch (RuntimeException e) {
            // Un script roto no debería impedir fusionar para siempre
            FusionSafety.failed("un mod o script que escucha antes de fusionar", e);
            return null;
        }
        if (!event.isCanceled()) {
            return null;
        }
        return event.getCancelMessage() != null ? event.getCancelMessage()
                : Component.translatable("message.fusionmon.blocked");
    }

    /** Se ha creado una fusión (fusionar o invertir). headForm/bodyForm, los de antes de fusionar. */
    public static void fused(ServerPlayer player, Pokemon fusion, FormData headForm, FormData bodyForm,
                             boolean reversed) {
        discover(player, headForm, bodyForm);
        post(new FusionmonEvent.Fused(player, fusion, headForm, bodyForm, reversed));
        trigger(player, reversed ? FusionTrigger.Action.REVERSE : FusionTrigger.Action.FUSE, headForm, bodyForm);
    }

    public static void unfused(ServerPlayer player, Pokemon head, Pokemon body) {
        post(new FusionmonEvent.Unfused(player, head, body));
        trigger(player, FusionTrigger.Action.UNFUSE, head.getForm(), body.getForm());
    }

    /**
     * El jugador ha conseguido una fusión que no ha creado él (capturada, premio de incursión, regalada): cuenta como
     * descubierta, como si la hubiera hecho.
     */
    public static void obtained(ServerPlayer player, Pokemon fusion) {
        FormData headForm = FusionData.headForm(fusion);
        FormData bodyForm = FusionData.bodyForm(fusion);
        if (headForm == null || bodyForm == null) {
            return;
        }
        discover(player, headForm, bodyForm);
        post(new FusionmonEvent.Obtained(player, fusion));
        trigger(player, FusionTrigger.Action.OBTAIN, headForm, bodyForm);
    }

    /** Apunta la pareja en el Fusion Album; si es nueva para el jugador, World First, evento y logro. */
    private static void discover(ServerPlayer player, FormData headForm, FormData bodyForm) {
        if (!FusionDiscovery.register(player, FusionDiscovery.key(headForm, bodyForm))) {
            return;
        }
        // Primera vez para este jugador: ¿es la primera del mundo? (si el servidor participa)
        WorldFirst.discovered(player, headForm, bodyForm);
        post(new FusionmonEvent.Discovered(player, headForm, bodyForm, FusionDiscovery.count(player)));
        trigger(player, FusionTrigger.Action.DISCOVER, headForm, bodyForm);
    }

    private static void post(FusionmonEvent event) {
        try {
            NeoForge.EVENT_BUS.post(event);
        } catch (RuntimeException e) {
            FusionSafety.failed("un mod o script que escucha las fusiones", e);
        }
    }

    private static void trigger(ServerPlayer player, FusionTrigger.Action action, FormData head, FormData body) {
        try {
            FusionTrigger.trigger(player, action, head, body, FusionDiscovery.count(player));
        } catch (RuntimeException e) {
            FusionSafety.failed("los logros de Fusionmon", e);
        }
    }
}
