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
 * Guante Trampa (manos): más probabilidad de captura con las Poké Balls que lanzas. En rango 3 ("fusion_snare") la
 * bonificación se dobla contra fusiones. Sube de nivel con cada captura (más si es una fusión).
 */
public class SnareGloveItem extends PokeRelicItem {

    public static final String ABILITY = "snare";

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
                                .rankModifier(3, "fusion_snare")
                                .stat(AbilityStatTemplate.builder("bonus")
                                        .initialValue(0.10, 0.15)
                                        .targetValue(RelicsScalingModels.ADDITIVE.get(), 0.50)
                                        .formatValue(value -> (int) MathUtils.round(value * 100.0, 0))
                                        .build())
                                .experienceSources(ExperienceSourcesTemplate.builder()
                                        .source(ExperienceSourceTemplate.builder("capture").build())
                                        .source(ExperienceSourceTemplate.builder("fusion_capture").build())
                                        .build())
                                .statistic(AbilityStatisticTemplate.builder()
                                        .metric(AbilityMetricTemplate.builder("assisted_captures")
                                                .formatValue(value -> String.valueOf(value.intValue())).build())
                                        .build())
                                // Una mano abierta
                                .research(ResearchTemplate.builder()
                                        .star(0, 11, 22).star(1, 4, 10).star(2, 8, 5)
                                        .star(3, 13, 4).star(4, 17, 7).star(5, 19, 14)
                                        .link(0, 1).link(0, 2).link(0, 3).link(0, 4).link(0, 5)
                                        .build())
                                .build())
                        .build())
                .leveling(LevelingTemplate.builder().initialCost(100.0).step(100.0).build())
                .loot(LootTemplate.builder().entry(LootEntries.FOREST).entry(LootEntries.TAIGA).build())
                .build();
    }
}
