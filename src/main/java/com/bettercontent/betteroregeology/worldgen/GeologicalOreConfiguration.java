package com.bettercontent.betteroregeology.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public record GeologicalOreConfiguration(
        List<OreConfiguration.TargetBlockState> targets,
        DepositMorphology morphology,
        int blockBudget,
        int budgetSpread,
        DepositClass depositClass,
        float discardChanceOnAirExposure) implements FeatureConfiguration {

    public static final Codec<GeologicalOreConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OreConfiguration.TargetBlockState.CODEC.listOf().fieldOf("targets").forGetter(GeologicalOreConfiguration::targets),
            DepositMorphology.CODEC.fieldOf("morphology").forGetter(GeologicalOreConfiguration::morphology),
            Codec.intRange(4, 128).fieldOf("block_budget").forGetter(GeologicalOreConfiguration::blockBudget),
            Codec.intRange(0, 124).optionalFieldOf("budget_spread", 0)
                    .forGetter(GeologicalOreConfiguration::budgetSpread),
            DepositClass.CODEC.optionalFieldOf("deposit_class", DepositClass.HOME)
                    .forGetter(GeologicalOreConfiguration::depositClass),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("discard_chance_on_air_exposure", 0.0F)
                    .forGetter(GeologicalOreConfiguration::discardChanceOnAirExposure)
    ).apply(instance, GeologicalOreConfiguration::new));

    public GeologicalOreConfiguration {
        targets = List.copyOf(targets);
        if (targets.isEmpty()) throw new IllegalArgumentException("targets must not be empty");
        new DepositBudget(blockBudget, budgetSpread);
    }
}
