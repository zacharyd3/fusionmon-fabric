package com.arnau.fusionmon.compat.kubejs;

import com.arnau.fusionmon.api.FusionmonEvent;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.latvian.mods.kubejs.player.KubePlayerEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Lo que ven los scripts de KubeJS en cada FusionmonEvents.*. Rhino convierte getX() en la propiedad x
 * (event.headSpecies, event.fusion...). Las especies van como texto "cobblemon:charizard": es lo fácil de comparar en
 * un script. event.player viene de KubePlayerEvent (con tell, give, etc. de KubeJS).
 */
public final class FusionmonKubeEvents {

    private FusionmonKubeEvents() {
    }

    static String species(FormData form) {
        return form == null ? "" : form.getSpecies().getResourceIdentifier().toString();
    }

    static String form(FormData form) {
        return form == null ? "" : form.getName();
    }

    /** Base: el jugador del evento de Fusionmon. */
    abstract static class Base<E extends FusionmonEvent> implements KubePlayerEvent {
        protected final E event;

        Base(E event) {
            this.event = event;
        }

        @Override
        public Player getEntity() {
            return event.getPlayer();
        }
    }

    /** FusionmonEvents.beforeFuse: event.cancel() lo impide (antes de gastar el inyector). */
    public static class BeforeFuse extends Base<FusionmonEvent.BeforeFuse> {
        BeforeFuse(FusionmonEvent.BeforeFuse event) {
            super(event);
        }

        public Pokemon getHead() {
            return event.getHead();
        }

        public Pokemon getBody() {
            return event.getBody();
        }

        public String getHeadSpecies() {
            return species(event.getHead().getForm());
        }

        public String getBodySpecies() {
            return species(event.getBody().getForm());
        }

        public boolean isReversed() {
            return event.isReversed();
        }

        /** El mensaje que verá el jugador si se cancela: event.setCancelMessage(Text.red('...')). */
        public void setCancelMessage(Component message) {
            event.setCancelMessage(message);
        }
    }

    /** FusionmonEvents.beforeUnfuse: event.cancel() lo impide. */
    public static class BeforeUnfuse extends Base<FusionmonEvent.BeforeUnfuse> {
        BeforeUnfuse(FusionmonEvent.BeforeUnfuse event) {
            super(event);
        }

        public Pokemon getFusion() {
            return event.getFusion();
        }

        public void setCancelMessage(Component message) {
            event.setCancelMessage(message);
        }
    }

    /** FusionmonEvents.fused: ya fusionado (o invertido, event.reversed). */
    public static class Fused extends Base<FusionmonEvent.Fused> {
        Fused(FusionmonEvent.Fused event) {
            super(event);
        }

        public Pokemon getFusion() {
            return event.getFusion();
        }

        public String getFusionName() {
            return event.getFusion().getDisplayName(false).getString();
        }

        public String getHeadSpecies() {
            return species(event.getHeadForm());
        }

        public String getHeadForm() {
            return form(event.getHeadForm());
        }

        public String getBodySpecies() {
            return species(event.getBodyForm());
        }

        public String getBodyForm() {
            return form(event.getBodyForm());
        }

        public boolean isReversed() {
            return event.isReversed();
        }
    }

    /** FusionmonEvents.unfused. */
    public static class Unfused extends Base<FusionmonEvent.Unfused> {
        Unfused(FusionmonEvent.Unfused event) {
            super(event);
        }

        public Pokemon getHead() {
            return event.getHead();
        }

        public Pokemon getBody() {
            return event.getBody();
        }
    }

    /** FusionmonEvents.discovered: pareja nueva en el Fusion Album del jugador (event.total = cuántas lleva). */
    public static class Discovered extends Base<FusionmonEvent.Discovered> {
        Discovered(FusionmonEvent.Discovered event) {
            super(event);
        }

        public String getHeadSpecies() {
            return species(event.getHeadForm());
        }

        public String getBodySpecies() {
            return species(event.getBodyForm());
        }

        public int getTotal() {
            return event.getTotal();
        }
    }

    /** FusionmonEvents.obtained: una fusión que el jugador no ha creado (capturada, premio, regalo). */
    public static class Obtained extends Base<FusionmonEvent.Obtained> {
        Obtained(FusionmonEvent.Obtained event) {
            super(event);
        }

        public Pokemon getFusion() {
            return event.getFusion();
        }
    }
}
