package com.bettercontent.betteroregeology.ore;

import com.bettercontent.betteroregeology.salience.DepositIdentity;
import com.bettercontent.betteroregeology.salience.FamiliarityState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

final class OreSalienceContractTest {
    @Test
    void everyDepositHasExactlyOneAspectAndExcavatedVariantsResolveBySuffix() {
        assertEquals(8, DepositIdentity.values().length);
        assertEquals(8, Arrays.stream(DepositIdentity.values()).map(DepositIdentity::family).distinct().count());
        for (DepositIdentity identity : DepositIdentity.values()) {
            assertEquals(identity, DepositIdentity.fromBlockId(new ResourceLocation("better_ore_geology", identity.family())).orElseThrow());
            assertEquals(identity, DepositIdentity.fromBlockId(new ResourceLocation("better_ore_geology", "deepslate_" + identity.family())).orElseThrow());
            assertEquals(identity, DepositIdentity.fromBlockId(new ResourceLocation("excavated_variants", "andesite_" + identity.family())).orElseThrow());
        }
        assertTrue(DepositIdentity.fromBlockId(new ResourceLocation("minecraft", "iron_ore")).isEmpty());
        assertTrue(DepositIdentity.fromBlockId(new ResourceLocation("better_ore_geology", "ore_chunk_hotstone")).isEmpty());
    }

    @Test
    void familiarityUsesExponentialCooldownAndDecaysOffline() {
        long start = 1_000_000L;
        FamiliarityState state = FamiliarityState.fresh(start);
        long[] waits = {0L, 30_000L, 120_000L, 480_000L, 1_920_000L};
        long now = start;
        for (long wait : waits) {
            now += wait;
            assertTrue(state.mayCue(now));
            state = state.afterCue(now);
            assertFalse(state.mayCue(now));
        }
        assertEquals(5, state.level());
        assertEquals(4, state.decay(now + FamiliarityState.DECAY_STEP_MILLIS).level());
        assertEquals(0, state.decay(now + 5 * FamiliarityState.DECAY_STEP_MILLIS).level());
    }

    @Test
    void persistenceRoundTripsAndBackwardClockCannotLockPlayerOut() {
        FamiliarityState original = new FamiliarityState(3, 50_000L, 40_000L);
        assertEquals(original, FamiliarityState.load(original.save(), 50_000L));
        FamiliarityState corrected = original.decay(30_000L);
        assertEquals(3, corrected.level());
        assertTrue(corrected.mayCue(30_000L));
    }
}
