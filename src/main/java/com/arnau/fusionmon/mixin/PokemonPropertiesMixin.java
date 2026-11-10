package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.fusion.FusionRequest;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fusiones pedidas con "fusion_body=<especie>" (FusionRequest): al terminar de aplicar unas propiedades a un Pokémon se
 * busca la petición entre sus aspects y características (las formas en que Raid Dens y RCT pueden escribirla).
 *
 * require = 0: si una versión de Cobblemon cambiase este método, fusion_body deja de funcionar (avisa MixinHealth),
 * pero todo lo demás sigue.
 */
@Mixin(value = PokemonProperties.class, remap = false)
public abstract class PokemonPropertiesMixin {

    @Inject(method = "apply(Lcom/cobblemon/mod/common/pokemon/Pokemon;)V", at = @At("TAIL"), require = 0)
    private void fusionmon$requestFusion(Pokemon pokemon, CallbackInfo ci) {
        FusionRequest.afterApply((PokemonProperties) (Object) this, pokemon);
    }
}
