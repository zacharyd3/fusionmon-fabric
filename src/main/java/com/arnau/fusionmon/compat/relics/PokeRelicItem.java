package com.arnau.fusionmon.compat.relics;

import it.hurts.sskirillss.relics.api.relics.data.AbilityData;
import it.hurts.sskirillss.relics.items.relics.base.WearableRelicItem;
import it.hurts.sskirillss.relics.utils.EntityUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Base de las reliquias de Fusionmon para el mod Relics: objetos que se llevan puestos en los huecos de Curios, suben de
 * nivel al usarlos y desbloquean sus habilidades con la investigación de estrellas de Relics (como las suyas).
 *
 * Cada una tiene UNA habilidad (su id = ABILITY de la subclase). Lo que hace se programa en RelicsCompat con los eventos
 * de Cobblemon; aquí solo lo común: buscar la puesta y leer sus stats.
 *
 * Relics busca los textos por la ruta del objeto ("relics.description.twin_heart_amulet...") y las imágenes de las
 * habilidades en assets/relics/textures/abilities/&lt;objeto&gt;/: por eso los nombres de los objetos son únicos y esas
 * imágenes van en el namespace "relics" dentro de nuestro jar.
 */
public abstract class PokeRelicItem extends WearableRelicItem {

    /** Su habilidad (la única). */
    protected abstract String ability();

    @Override
    public String getConfigRoute() {
        // config/fusionmon/relics/<objeto>.json: los valores de cada reliquia, editables como las de Relics
        return "fusionmon";
    }

    /** La habilidad de una reliquia puesta, si ya se puede usar (desbloqueada e investigada). */
    public AbilityData usable(LivingEntity entity, ItemStack stack) {
        AbilityData data = getRelicData(entity, stack).getAbilitiesData().getAbilityData(ability());
        return data != null && data.canPlayerUse(entity) ? data : null;
    }

    /** La reliquia de este tipo puesta con el valor más alto de ese stat que se pueda usar; null si no hay ninguna. */
    public Worn best(LivingEntity entity, String stat) {
        Worn best = null;
        for (ItemStack stack : EntityUtils.findEquippedCurios(entity, this)) {
            AbilityData data = usable(entity, stack);
            if (data == null) {
                continue;
            }
            double value = data.getStatData(stat).getValue();
            if (best == null || value > best.value()) {
                best = new Worn(stack, data, value);
            }
        }
        return best;
    }

    /** Suma experiencia de reliquia (la que la sube de nivel) por una de sus fuentes, y su estadística si la hay. */
    public void reward(LivingEntity entity, Worn worn, String source, double experience, String metric) {
        getRelicData(entity, worn.stack()).getLevelingData().addExperience(ability(), source, experience);
        if (metric != null) {
            worn.data().getStatisticData().getMetricData(metric).addValue(1.0);
        }
    }

    /** Una reliquia puesta: su objeto, su habilidad y el valor del stat pedido. */
    public record Worn(ItemStack stack, AbilityData data, double value) {

        public boolean rank(String modifier) {
            return data.getRankModifierData(modifier).isEnabled();
        }
    }
}
