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
 * Amuleto de Corazones Gemelos (collar): las fusiones del equipo ganan más experiencia. En rango 3 ("kinship") los
 * Pokémon que no son fusión ganan la mitad de esa bonificación. Sube de nivel cada vez que una fusión gana experiencia.
 */
public class TwinHeartAmuletItem extends PokeRelicItem {

    public static final String ABILITY = "twin_bond";

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
                                .rankModifier(3, "kinship")
                                .stat(AbilityStatTemplate.builder("bonus")
                                        .initialValue(0.10, 0.20)
                                        .targetValue(RelicsScalingModels.ADDITIVE.get(), 0.75)
                                        .formatValue(value -> (int) MathUtils.round(value * 100.0, 0))
                                        .build())
                                .experienceSources(ExperienceSourcesTemplate.builder()
                                        .source(ExperienceSourceTemplate.builder("fusion_experience").build())
                                        .build())
                                .statistic(AbilityStatisticTemplate.builder()
                                        .metric(AbilityMetricTemplate.builder("boosted_gains")
                                                .formatValue(value -> String.valueOf(value.intValue())).build())
                                        .build())
                                // Dos corazones unidos
                                .research(ResearchTemplate.builder()
                                        .star(0, 5, 8).star(1, 8, 5).star(2, 11, 9)
                                        .star(3, 14, 5).star(4, 17, 8).star(5, 11, 20)
                                        .link(0, 1).link(1, 2).link(2, 3).link(3, 4).link(4, 5).link(5, 0)
                                        .build())
                                .build())
                        .build())
                .leveling(LevelingTemplate.builder().initialCost(100.0).step(100.0).build())
                .loot(LootTemplate.builder().entry(LootEntries.VILLAGE).entry(LootEntries.PLAINS).build())
                .build();
    }
}
