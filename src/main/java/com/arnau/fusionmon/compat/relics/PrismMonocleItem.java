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
 * Monóculo Prisma (cabeza): multiplica la probabilidad de shiny de los Pokémon salvajes que aparecen cerca. En rango 3
 * ("fusion_shine") las fusiones salvajes la tienen el doble. Sube de nivel con cada Pokémon que aparece cerca, y mucho
 * más con cada shiny.
 */
public class PrismMonocleItem extends PokeRelicItem {

    public static final String ABILITY = "prismatic_sight";

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
                                .rankModifier(3, "fusion_shine")
                                .stat(AbilityStatTemplate.builder("multiplier")
                                        .initialValue(1.5, 2.0)
                                        .targetValue(RelicsScalingModels.ADDITIVE.get(), 5.0)
                                        .formatValue(value -> MathUtils.round(value, 1))
                                        .build())
                                .experienceSources(ExperienceSourcesTemplate.builder()
                                        .source(ExperienceSourceTemplate.builder("nearby_spawn").build())
                                        .source(ExperienceSourceTemplate.builder("shiny").build())
                                        .build())
                                .statistic(AbilityStatisticTemplate.builder()
                                        .metric(AbilityMetricTemplate.builder("shinies_revealed")
                                                .formatValue(value -> String.valueOf(value.intValue())).build())
                                        .build())
                                // Un destello con cuatro puntas
                                .research(ResearchTemplate.builder()
                                        .star(0, 11, 14).star(1, 11, 4).star(2, 18, 14)
                                        .star(3, 11, 24).star(4, 4, 14).star(5, 16, 8)
                                        .link(0, 1).link(0, 2).link(0, 3).link(0, 4).link(1, 5).link(5, 2)
                                        .build())
                                .build())
                        .build())
                .leveling(LevelingTemplate.builder().initialCost(100.0).step(150.0).build())
                .loot(LootTemplate.builder().entry(LootEntries.CAVE).entry(LootEntries.MOUNTAIN).build())
                .build();
    }
}
