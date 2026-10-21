package com.bettercontent.betteroregeology.worldgen;

import com.mojang.serialization.Codec;
import com.bettercontent.betteroregeology.diagnostics.PlaytestDiagnostics;
import com.bettercontent.betteroregeology.diagnostics.PlaytestLog;
import com.bettercontent.betteroregeology.visual.ExposureField;
import com.bettercontent.betteroregeology.visual.ExposureState;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public final class GeologicalDepositFeature extends Feature<GeologicalOreConfiguration> {
    public GeologicalDepositFeature(Codec<GeologicalOreConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<GeologicalOreConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        GeologicalOreConfiguration config = context.config();
        BlockPos origin = context.origin();
        int budget = new DepositBudget(config.blockBudget(), config.budgetSpread()).sample(random);
        long occurrenceSalt = random.nextLong();
        int placed = 0, candidates = 0, unavailable = 0, nativeReturnsTrue = 0;

        DepositSampleContext sampleContext = new DepositSampleContext(
                level.getSeed(), origin, occurrenceSalt, budget, config.depositClass());
        int style = ExposureField.style(config.morphology().structuralStrike(sampleContext), occurrenceSalt);
        for (BlockPos offset : config.morphology().sample(sampleContext)) {
            BlockPos pos = origin.offset(offset);
            candidates++;
            if (level.isOutsideBuildHeight(pos) || !level.ensureCanWrite(pos)) { unavailable++; continue; }
            BlockState current = level.getBlockState(pos);
            for (OreConfiguration.TargetBlockState target : config.targets()) {
                if (canPlace(current, level::getBlockState, random, target, pos, config.discardChanceOnAirExposure())) {
                    BlockState state = target.state.hasProperty(ExposureState.STYLE)
                            ? target.state.setValue(ExposureState.STYLE, style) : target.state;
                    if (level.setBlock(pos, state, 2)) nativeReturnsTrue++;
                    placed++;
                    break;
                }
            }
        }
        var log = PlaytestDiagnostics.LOG;
        if (log.enabled("worldgen")) {
            final int calls = placed, candidateCount = candidates, unavailableCount = unavailable, returnedTrue = nativeReturnsTrue;
            log.event("worldgen", "geology.occurrence", "server", calls > 0 ? "observed" : "rejected",
                calls > 0 ? "native_placement_calls" : "no_placement", () -> PlaytestLog.fields("deposit_class", config.depositClass(), "budget", budget) +
                    com.bettercontent.betteroregeology.diagnostics.FeatureObservation.fields(candidateCount, unavailableCount, calls, returnedTrue));
        }
        return placed > 0;
    }

    private static boolean canPlace(
            BlockState current,
            Function<BlockPos, BlockState> reader,
            RandomSource random,
            OreConfiguration.TargetBlockState target,
            BlockPos pos,
            float discardChance) {
        if (!target.target.test(current, random)) return false;
        return discardChance <= 0.0F
                || random.nextFloat() >= discardChance
                || !Feature.isAdjacentToAir(reader, pos);
    }
}
