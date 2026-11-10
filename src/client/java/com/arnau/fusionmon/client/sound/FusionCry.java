package com.arnau.fusionmon.client.sound;

import com.arnau.fusionmon.client.model.FusionGraft;
import com.arnau.fusionmon.client.texture.FusionBody;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * El grito de una fusión: el de la cabeza y el del cuerpo a la vez, fundidos. Empieza sobre todo la cabeza (la cara
 * que ves) y, unos ticks después, entra el cuerpo y va ganando mientras la cabeza baja: suena a los dos Pokémon, como
 * las fusiones de Infinite Fusion.
 *
 * Cobblemon toca los gritos desde las animaciones (fotogramas de sonido "pokemon.charizard.cry" de la animación
 * "cry"), así que se interceptan ahí (BedrockSoundKeyframeMixin): con cabeza sobre cuerpo el modelo que anima es el del
 * cuerpo y suena su grito; en modo colores, el de la cabeza. Sea cual sea, se busca el de la otra parte con el mismo
 * nombre ("pokemon.&lt;especie&gt;.cry") y se tocan los dos. Si el de la otra no existe (especie de un mod sin grito, o
 * con otro nombre), suena el original, como siempre.
 *
 * Solo sonidos de Minecraft (un SoundInstance que cambia de volumen cada tick): nada de mezclar audio a mano.
 */
public final class FusionCry {

    /** Ticks hasta que entra el grito del cuerpo. */
    private static final int BODY_DELAY = 3;
    /** Ticks que dura el fundido de los dos volúmenes. */
    private static final int FADE = 20;
    /** La cabeza empieza a este volumen y acaba a HEAD_END... */
    private static final float HEAD_START = 1F;
    private static final float HEAD_END = 0.45F;
    /** ...y el cuerpo, al revés. */
    private static final float BODY_START = 0.5F;
    private static final float BODY_END = 0.9F;
    /** El cuerpo, algo más grave: dos gritos iguales de tono se tapan y suenan a uno. */
    private static final float BODY_PITCH = 0.94F;

    private static boolean blended = true;

    private FusionCry() {
    }

    public static boolean isBlended() {
        return blended;
    }

    /** /fusionvisual cries blend|plain: grito mezclado o el que toque Cobblemon (no se guarda). */
    public static void setBlended(boolean value) {
        blended = value;
    }

    /**
     * Un sonido de una animación va a sonar. ¿Lo toca Fusionmon? (entonces Cobblemon no).
     *
     * @param entity el Pokémon, o null en un menú (resumen, PC)
     * @param state  el estado que anima: el del Pokémon, o el de una cabeza pegada
     */
    public static boolean intercept(Entity entity, PosableState state, ResourceLocation sound) {
        // La cabeza pegada repite las animaciones del cuerpo (ver FusionGraft.mirrorAnimation): sin esto el grito
        // sonaría dos veces, y el de la cabeza como sonido de menú, igual de fuerte esté donde esté
        if (FusionGraft.isHeadState(state)) {
            return true;
        }
        String path = sound.getPath();
        if (!blended || !path.startsWith("pokemon.") || !path.endsWith(".cry")
                || entity != null && entity.isSilent()) {
            return false;
        }
        FusionBody body = FusionBody.of(state.getCurrentAspects());
        if (body == null) {
            return false;
        }
        ResourceLocation headCry;
        ResourceLocation bodyCry;
        if (FusionGraft.speaksAsBody(state)) {
            bodyCry = sound;
            String head = headSpecies(entity, state);
            headCry = head == null ? null : cry(sound.getNamespace(), head);
        } else {
            headCry = sound;
            bodyCry = cry(sound.getNamespace(), body.species());
        }
        if (headCry == null || headCry.equals(bodyCry) || !exists(headCry) || !exists(bodyCry)) {
            return false;
        }
        Minecraft.getInstance().getSoundManager().play(new Voice(headCry, entity, HEAD_START, HEAD_END, 1F, 0));
        Minecraft.getInstance().getSoundManager().play(new Voice(bodyCry, entity, BODY_START, BODY_END, BODY_PITCH,
                BODY_DELAY));
        return true;
    }

    /** La ruta de la especie de la cabeza ("charizard"): la del Pokémon, o la que ha pintado el graft en un menú. */
    private static String headSpecies(Entity entity, PosableState state) {
        if (entity instanceof PokemonEntity pokemon) {
            return pokemon.getPokemon().getSpecies().getResourceIdentifier().getPath();
        }
        ResourceLocation head = FusionGraft.headSpecies(state);
        return head == null ? null : head.getPath();
    }

    private static ResourceLocation cry(String namespace, String species) {
        return ResourceLocation.fromNamespaceAndPath(namespace, "pokemon." + species + ".cry");
    }

    private static boolean exists(ResourceLocation sound) {
        return Minecraft.getInstance().getSoundManager().getSoundEvent(sound) != null;
    }

    /**
     * Uno de los dos gritos: sigue al Pokémon (o suena como sonido de menú, sin posición, como los de Cobblemon) y
     * cambia de volumen en los primeros FADE ticks.
     */
    private static final class Voice extends AbstractTickableSoundInstance {
        private final Entity entity;
        private final float from;
        private final float to;
        private int age;

        Voice(ResourceLocation sound, Entity entity, float from, float to, float pitch, int delay) {
            super(SoundEvent.createVariableRangeEvent(sound), entity == null ? SoundSource.MASTER
                    : entity.getSoundSource(), SoundInstance.createUnseededRandom());
            this.entity = entity;
            this.from = from;
            this.to = to;
            this.volume = from;
            this.pitch = pitch;
            this.delay = delay;
            if (entity == null) {
                // Como SimpleSoundInstance.forUI
                this.relative = true;
                this.attenuation = SoundInstance.Attenuation.NONE;
            } else {
                this.x = entity.getX();
                this.y = entity.getY();
                this.z = entity.getZ();
            }
        }

        @Override
        public void tick() {
            if (entity != null) {
                if (entity.isRemoved()) {
                    stop();
                    return;
                }
                x = entity.getX();
                y = entity.getY();
                z = entity.getZ();
            }
            age++;
            float progress = Math.min(1F, age / (float) FADE);
            volume = from + (to - from) * progress;
        }
    }
}
