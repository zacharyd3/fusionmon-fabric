package com.arnau.fusionmon.api;

import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Eventos públicos de Fusionmon, en el bus normal de NeoForge (NeoForge.EVENT_BUS), solo en el servidor. Son la API
 * para otros mods y para los scripts de modpacks: el plugin de KubeJS (compat/kubejs) los reenvía como
 * FusionmonEvents.*, y las misiones (FTB Quests) usan los logros que se dan con ellos (FusionTrigger).
 *
 * Los "Pre" (BeforeFuse, BeforeUnfuse) se pueden cancelar: se lanzan después de comprobarlo todo y ANTES de gastar el
 * inyector, así que cancelar no cuesta nada al jugador. Con setCancelMessage se le dice por qué.
 *
 * Ojo, para quien escuche: no cambiar el equipo del jugador dentro de estos eventos (se está fusionando).
 */
public abstract class FusionmonEvent extends Event {

    private final ServerPlayer player;

    protected FusionmonEvent(ServerPlayer player) {
        this.player = player;
    }

    /** El jugador que fusiona, separa o descubre. */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** Base de los eventos que se pueden cancelar, con un mensaje opcional para el jugador. */
    public abstract static class Cancellable extends FusionmonEvent implements ICancellableEvent {
        private Component cancelMessage;

        protected Cancellable(ServerPlayer player) {
            super(player);
        }

        /** Lo que verá el jugador si se cancela (si no se pone, un mensaje genérico). */
        public Component getCancelMessage() {
            return cancelMessage;
        }

        public void setCancelMessage(Component cancelMessage) {
            this.cancelMessage = cancelMessage;
        }
    }

    /**
     * Antes de fusionar (o invertir una fusión, reversed = true): cabeza y cuerpo tal como van a quedar. Cancelable.
     * Al invertir, head y body son copias de las partes guardadas: leerlas, no cambiarlas.
     */
    public static class BeforeFuse extends Cancellable {
        private final Pokemon head;
        private final Pokemon body;
        private final boolean reversed;

        public BeforeFuse(ServerPlayer player, Pokemon head, Pokemon body, boolean reversed) {
            super(player);
            this.head = head;
            this.body = body;
            this.reversed = reversed;
        }

        public Pokemon getHead() {
            return head;
        }

        public Pokemon getBody() {
            return body;
        }

        public boolean isReversed() {
            return reversed;
        }
    }

    /** Antes de separar una fusión. Cancelable. */
    public static class BeforeUnfuse extends Cancellable {
        private final Pokemon fusion;

        public BeforeUnfuse(ServerPlayer player, Pokemon fusion) {
            super(player);
            this.fusion = fusion;
        }

        public Pokemon getFusion() {
            return fusion;
        }
    }

    /** Se ha creado una fusión (fusionando o invirtiendo). fusion es el Pokémon que queda en el equipo. */
    public static class Fused extends FusionmonEvent {
        private final Pokemon fusion;
        private final FormData headForm;
        private final FormData bodyForm;
        private final boolean reversed;

        public Fused(ServerPlayer player, Pokemon fusion, FormData headForm, FormData bodyForm, boolean reversed) {
            super(player);
            this.fusion = fusion;
            this.headForm = headForm;
            this.bodyForm = bodyForm;
            this.reversed = reversed;
        }

        public Pokemon getFusion() {
            return fusion;
        }

        public FormData getHeadForm() {
            return headForm;
        }

        public FormData getBodyForm() {
            return bodyForm;
        }

        public boolean isReversed() {
            return reversed;
        }
    }

    /** Se ha separado una fusión: head y body ya están en el equipo (o el cuerpo en el PC). */
    public static class Unfused extends FusionmonEvent {
        private final Pokemon head;
        private final Pokemon body;

        public Unfused(ServerPlayer player, Pokemon head, Pokemon body) {
            super(player);
            this.head = head;
            this.body = body;
        }

        public Pokemon getHead() {
            return head;
        }

        public Pokemon getBody() {
            return body;
        }
    }

    /**
     * Una pareja cabeza + cuerpo nueva en el Fusion Album del jugador (creándola, o consiguiendo una fusión de otra
     * forma: capturada, premio de una incursión, regalada). total = cuántas lleva descubiertas, esta incluida.
     */
    public static class Discovered extends FusionmonEvent {
        private final FormData headForm;
        private final FormData bodyForm;
        private final int total;

        public Discovered(ServerPlayer player, FormData headForm, FormData bodyForm, int total) {
            super(player);
            this.headForm = headForm;
            this.bodyForm = bodyForm;
            this.total = total;
        }

        public FormData getHeadForm() {
            return headForm;
        }

        public FormData getBodyForm() {
            return bodyForm;
        }

        /** Cuántas parejas tiene ya el jugador en su Fusion Album. */
        public int getTotal() {
            return total;
        }
    }

    /**
     * El jugador ha conseguido una fusión sin crearla él: capturada (fusiones salvajes, jefes de incursión), premio
     * de una incursión, regalada por un comando o un NPC...
     */
    public static class Obtained extends FusionmonEvent {
        private final Pokemon fusion;

        public Obtained(ServerPlayer player, Pokemon fusion) {
            super(player);
            this.fusion = fusion;
        }

        public Pokemon getFusion() {
            return fusion;
        }
    }
}
