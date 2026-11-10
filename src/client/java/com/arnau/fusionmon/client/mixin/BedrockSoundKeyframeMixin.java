package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.client.sound.FusionCry;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockSoundKeyframe;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Los sonidos de las animaciones de Cobblemon (los gritos van así): el de una fusión suena mezclado con el de su otra
 * parte, y los de una cabeza pegada no suenan (ya suena el cuerpo). Ver FusionCry. Pasan por aquí todos los sonidos
 * de todas las animaciones: si algo falla, suena el de siempre.
 */
@Mixin(value = BedrockSoundKeyframe.class, remap = false)
public abstract class BedrockSoundKeyframeMixin {

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void fusionmon$fusionCry(Entity entity, PosableState state, CallbackInfo ci) {
        try {
            if (FusionCry.intercept(entity, state, ((BedrockSoundKeyframe) (Object) this).getSound())) {
                ci.cancel();
            }
        } catch (RuntimeException e) {
            FusionSafety.failed("el grito de una fusión", e);
        }
    }
}
