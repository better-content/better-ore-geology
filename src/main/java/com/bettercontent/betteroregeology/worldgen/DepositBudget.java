package com.bettercontent.betteroregeology.worldgen;

import net.minecraft.util.RandomSource;

/** Inclusive, centered block-count variation for one configured occurrence class. */
public record DepositBudget(int target, int spread) {
    public DepositBudget {
        if (target - spread < 4 || target + spread > 128 || spread < 0)
            throw new IllegalArgumentException("target ± spread must stay in 4..128");
    }

    public int sample(RandomSource random) {
        return target + (spread == 0 ? 0 : random.nextInt(spread * 2 + 1) - spread);
    }
}
