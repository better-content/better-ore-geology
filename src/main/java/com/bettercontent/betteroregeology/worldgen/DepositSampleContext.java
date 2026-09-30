package com.bettercontent.betteroregeology.worldgen;

import net.minecraft.core.BlockPos;

/** Immutable inputs for schedule-independent procedural deposit sampling. */
public record DepositSampleContext(
        long worldSeed,
        BlockPos origin,
        long occurrenceSalt,
        int blockBudget,
        DepositClass depositClass) {

    public DepositSampleContext {
        if (origin == null) throw new IllegalArgumentException("origin must not be null");
        if (blockBudget < 4 || blockBudget > 128)
            throw new IllegalArgumentException("blockBudget must be in 4..128");
        if (depositClass == null) throw new IllegalArgumentException("depositClass must not be null");
    }
}
