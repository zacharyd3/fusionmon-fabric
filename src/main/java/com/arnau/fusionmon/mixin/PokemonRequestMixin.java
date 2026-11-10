package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.fusion.FusionRequest;
import com.cobblemon.mod.common.pokemon.Pokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Los dos sitios de Pokemon donde se completa una fusión pedida con fusion_body (FusionRequest):
 *  - initialize(): Cobblemon crea los Pokémon de unas propiedades con new Pokemon() → apply → initialize(). Al terminar,
 *    ya tiene especie, nivel y movimientos. Jefes de incursión de Raid Dens, su premio, /pokespawn...
 *  - setForcedAspects(): Radical Cobblemon Trainers no usa propiedades; crea cada Pokémon del equipo con setters y le
 *    pone los "aspects" del JSON del entrenador con este método (después de especie, nivel, IVs...).
 *
 * Aparte de PokemonMixin (obligatorio) para poder ser opcional: require = 0.
 */
@Mixin(value = Pokemon.class, remap = false)
public abstract class PokemonRequestMixin {

    @Inject(method = "initialize", at = @At("RETURN"), require = 0)
    private void fusionmon$completeRequest(CallbackInfoReturnable<Pokemon> cir) {
        FusionRequest.complete((Pokemon) (Object) this);
    }

    @Inject(method = "setForcedAspects", at = @At("TAIL"), require = 0)
    private void fusionmon$forcedAspectsRequest(Set<String> aspects, CallbackInfo ci) {
        FusionRequest.afterForcedAspects((Pokemon) (Object) this, aspects);
    }
}
