package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.client.model.FusionGraft;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Las animaciones sueltas que manda el servidor (grito, ataques, retroceso, evolución) llegan por addFirstAnimation:
 * si es una fusión, su cabeza pegada hace las suyas (ver FusionGraft.mirrorAnimation).
 */
@Mixin(value = PosableState.class, remap = false)
public abstract class PosableStateMixin {

    @Inject(method = "addFirstAnimation", at = @At("HEAD"))
    private void fusionmon$mirrorOnHead(Set<String> animation, CallbackInfo ci) {
        try {
            FusionGraft.mirrorAnimation((PosableState) (Object) this, animation);
        } catch (RuntimeException e) {
            FusionSafety.failed("repetir una animación en la cabeza pegada", e);
        }
    }
}
