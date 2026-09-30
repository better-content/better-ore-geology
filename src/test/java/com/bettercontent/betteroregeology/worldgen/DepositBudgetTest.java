package com.bettercontent.betteroregeology.worldgen;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DepositBudgetTest {
    @Test void spreadIsInclusiveCenteredAndDeterministic() {
        DepositBudget budget = new DepositBudget(46, 12);
        RandomSource first = RandomSource.create(0x51eedL);
        RandomSource replay = RandomSource.create(0x51eedL);
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        long total = 0;
        int samples = 25_000;
        for (int index = 0; index < samples; index++) {
            int sampled = budget.sample(first);
            assertEquals(sampled, budget.sample(replay));
            minimum = Math.min(minimum, sampled);
            maximum = Math.max(maximum, sampled);
            total += sampled;
        }
        assertEquals(34, minimum);
        assertEquals(58, maximum);
        assertTrue(Math.abs((double) total / samples - 46.0D) < 0.15D);
        assertEquals(46, new DepositBudget(46, 0).sample(first));
    }

    @Test void invalidRangesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DepositBudget(4, 1));
        assertThrows(IllegalArgumentException.class, () -> new DepositBudget(128, 1));
        assertThrows(IllegalArgumentException.class, () -> new DepositBudget(20, -1));
    }
}
