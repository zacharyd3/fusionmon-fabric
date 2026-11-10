package com.arnau.fusionmon.client.compat.jade;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.fusion.FusionBodyForm;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Integración con Jade (opcional): al mirar una fusión en el mundo, una línea "Fusión: Pikachu + Charizard".
 *
 * Jade busca sus plugins por la anotación @WailaPlugin al arrancar, y solo si está instalado: sin Jade esta clase nunca
 * se carga. Solo cliente: el cuerpo sale de los aspects de la entidad (FusionBodyForm), que ya llegan al cliente, así
 * que no hace falta mandar nada desde el servidor (vale también en servidores sin Jade). Se puede apagar en los
 * ajustes de Jade (Plugins → Fusionmon).
 */
@WailaPlugin(Fusionmon.MOD_ID)
public class FusionmonJadePlugin implements IWailaPlugin {

    static final ResourceLocation UID = Fusionmon.id("fusion_parts");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(FusionPartsProvider.INSTANCE, PokemonEntity.class);
    }

    enum FusionPartsProvider implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            try {
                if (!(accessor.getEntity() instanceof PokemonEntity entity)) {
                    return;
                }
                FormData body = FusionBodyForm.of(entity);
                Pokemon pokemon = entity.getPokemon();
                if (body == null || pokemon == null) {
                    return;
                }
                // La fusión es la especie de la cabeza; el nombre visible ya es el de la fusión
                tooltip.add(Component.translatable("jade.fusionmon.fusion_parts",
                        pokemon.getSpecies().getTranslatedName(), body.getSpecies().getTranslatedName())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            } catch (RuntimeException e) {
                // Se llama en cada fotograma mientras se mira la entidad
                FusionSafety.failed("la línea de Jade de una fusión", e);
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
