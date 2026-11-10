package com.arnau.fusionmon.compat.relics;

import it.hurts.sskirillss.relics.api.relics.AbilityMetricTemplate;
import it.hurts.sskirillss.relics.api.relics.AbilityStatisticTemplate;
import it.hurts.sskirillss.relics.api.relics.RelicTemplate;
import it.hurts.sskirillss.relics.api.relics.abilities.AbilitiesTemplate;
import it.hurts.sskirillss.relics.api.relics.abilities.AbilityTemplate;
import it.hurts.sskirillss.relics.api.relics.abilities.ExperienceSourceTemplate;
import it.hurts.sskirillss.relics.api.relics.abilities.ExperienceSourcesTemplate;
import it.hurts.sskirillss.relics.api.relics.abilities.stats.AbilityStatTemplate;
import it.hurts.sskirillss.relics.init.RelicsScalingModels;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.LevelingTemplate;
import it.hurts.sskirillss.relics.items.relics.base.data.loot.LootTemplate;
import it.hurts.sskirillss.relics.items.relics.base.data.loot.misc.LootEntries;
import it.hurts.sskirillss.relics.items.relics.base.data.research.ResearchTemplate;
import it.hurts.sskirillss.relics.utils.MathUtils;

/**
 * Brújula de Fusión (amuleto): los Pokémon salvajes que aparecen cerca pueden aparecer fusionados con una especie al
 * azar. En rango 3 ("kindred") el cuerpo comparte algún tipo con el Pokémon que aparece. Sube de nivel con cada Pokémon
 * salvaje que aparece cerca, y mucho más con cada fusión que provoca.
 */
public class FusionCompassItem extends PokeRelicItem {

    public static final String ABILITY = "fusion_attunement";

    @Override
    protected String ability() {
        return ABILITY;
    }

    @Override
    public RelicTemplate constructDefaultRelicTemplate() {
        return RelicTemplate.builder()
                .abilities(AbilitiesTemplate.builder()
                        .ability(AbilityTemplate.builder(ABILITY)
                                .initialMaxLevel(10)
                                .rankModifier(3, "kindred")
                                .stat(AbilityStatTemplate.builder("chance")
                                        .initialValue(0.01, 0.02)
                                        .thresholdValue(0.0, 1.0)
                                        .targetValue(RelicsScalingModels.ADDITIVE.get(), 0.08)
                                        .formatValue(value -> MathUtils.round(value * 100.0, 1))
                                        .build())
                                .experienceSources(ExperienceSourcesTemplate.builder()
                                        .source(ExperienceSourceTemplate.builder("nearby_spawn").build())
                                        .source(ExperienceSourceTemplate.builder("wild_fusion").build())
                                        .build())
                                .statistic(AbilityStatisticTemplate.builder()
                                        .metric(AbilityMetricTemplate.builder("wild_fusions")
                                                .formatValue(value -> String.valueOf(value.intValue())).build())
                                        .build())
                                // Una rosa de los vientos
                                .research(ResearchTemplate.builder()
                                        .star(0, 11, 3).star(1, 11, 14).star(2, 11, 25)
                                        .star(3, 3, 14).star(4, 19, 14)
                                        .link(0, 1).link(1, 2).link(3, 1).link(1, 4).link(0, 3).link(4, 2)
                                        .build())
                                .build())
                        .build())
                .leveling(LevelingTemplate.builder().initialCost(100.0).step(150.0).build())
                .loot(LootTemplate.builder().entry(LootEntries.WILDCARD).build())
                .build();
    }
}
