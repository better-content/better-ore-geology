package com.bettercontent.betteroregeology.worldgen;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DepositMorphologyTest {
    @Test void serializedNamesRoundTripThroughTheCodecs() {
        for (DepositMorphology morphology : DepositMorphology.values()) {
            var encoded = DepositMorphology.CODEC.encodeStart(JsonOps.INSTANCE, morphology).result().orElseThrow();
            assertEquals(morphology.getSerializedName(), encoded.getAsString());
            assertEquals(morphology, DepositMorphology.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
        }
        for (DepositClass depositClass : DepositClass.values()) {
            var encoded = DepositClass.CODEC.encodeStart(JsonOps.INSTANCE, depositClass).result().orElseThrow();
            assertEquals(depositClass.getSerializedName(), encoded.getAsString());
            assertEquals(depositClass, DepositClass.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
        }
    }

    @Test void everyArchetypeIsDeterministicBoundedDistinctAndHonorsItsBudget() {
        Set<Set<BlockPos>> crossFamilySilhouettes = new HashSet<>();
        for (DepositMorphology morphology : DepositMorphology.values()) {
            Set<Set<BlockPos>> family = new HashSet<>();
            for (int archetype = 0; archetype < 4; archetype++) {
                DepositSampleContext context = context(morphology, archetype, 48, DepositClass.HOME);
                List<BlockPos> first = morphology.sampleArchetype(context, archetype);
                List<BlockPos> replay = morphology.sampleArchetype(context, archetype);
                assertEquals(first, replay, morphology + " " + archetype);
                assertEquals(48, new HashSet<>(first).size(), morphology + " " + archetype);
                assertTrue(first.contains(BlockPos.ZERO), morphology + " " + archetype);
                assertTrue(first.stream().allMatch(pos -> Math.abs(pos.getX()) <= 12
                                && Math.abs(pos.getY()) <= 16 && Math.abs(pos.getZ()) <= 12),
                        morphology + " " + archetype);
                assertTrue(family.add(Set.copyOf(first)), morphology + " duplicate archetype " + archetype);
            }
            assertTrue(crossFamilySilhouettes.add(family.iterator().next()), morphology.toString());
        }
    }

    @Test void proceduralFamiliesProduceHighSilhouetteVarietyAndAllArchetypes() {
        for (DepositMorphology morphology : DepositMorphology.values()) {
            Set<Set<BlockPos>> silhouettes = new HashSet<>();
            Set<Integer> archetypes = new HashSet<>();
            for (int sample = 0; sample < 256; sample++) {
                DepositSampleContext context = new DepositSampleContext(
                        0x5eed5eedL,
                        new BlockPos(sample * 37 - 4000, sample % 97 - 48, sample * 53 - 6000),
                        sample * 0x9e3779b97f4a7c15L,
                        32,
                        sample % 3 == 0 ? DepositClass.ECHO : DepositClass.HOME);
                archetypes.add(morphology.selectArchetype(context));
                silhouettes.add(Set.copyOf(morphology.sample(context)));
            }
            assertEquals(Set.of(0, 1, 2, 3), archetypes, morphology.toString());
            assertTrue(silhouettes.size() >= 192, morphology + " unique silhouettes " + silhouettes.size());
        }
    }

    @Test void familyShapesRetainTheirGeologicalAxes() {
        Set<BlockPos> coal = sample(DepositMorphology.BROKEN_STRATIFORM_SEAM, 0, 56);
        Set<BlockPos> tin = sample(DepositMorphology.STEEP_QUARTZ_LODE, 0, 30);
        Set<BlockPos> hot = sample(DepositMorphology.BRECCIA_PIPE_WITH_RADIAL_DIKES, 0, 40);
        Set<BlockPos> shale = sample(DepositMorphology.LAMINATED_SHALE_STRINGERS, 1, 30);
        assertTrue(horizontalSpan(coal) >= 2 * span(coal, 'y'), "coal should read as strata");
        assertTrue(span(tin, 'y') >= 8, "tin should climb as a steep lode");
        assertTrue(span(hot, 'y') >= 8, "hotstone should retain a pipe axis");
        assertTrue(span(shale, 'y') >= 8, "slaty shale should retain steep cleavage");
        assertNotEquals(coal, shale, "black shale must not duplicate a coal seam");
    }

    @Test void neighboringOccurrencesShareProvinceScaleStructuralStrike() {
        for (DepositMorphology morphology : DepositMorphology.values()) {
            int coherent = 0;
            for (int sample = 0; sample < 1024; sample++) {
                BlockPos origin = new BlockPos(sample * 197 - 90_000, -32, sample * -283 + 40_000);
                DepositSampleContext first = new DepositSampleContext(sample * 7919L, origin,
                        sample * 104729L, 32, DepositClass.HOME);
                DepositSampleContext neighbor = new DepositSampleContext(sample * 7919L, origin.offset(1, 0, 1),
                        sample * 104729L + 1, 32, DepositClass.HOME);
                int difference = Math.abs(morphology.structuralStrike(first) - morphology.structuralStrike(neighbor));
                difference = Math.min(difference, 32 - difference);
                if (difference <= 2) coherent++;
            }
            assertTrue(coherent >= 960, morphology + " neighboring strike coherence " + coherent);
        }
    }

    @Test void samplingIsIdenticalAcrossSerialAndConcurrentExecution() throws Exception {
        List<Callable<List<BlockPos>>> work = new ArrayList<>();
        for (int index = 0; index < 512; index++) {
            DepositMorphology morphology = DepositMorphology.values()[index % DepositMorphology.values().length];
            DepositSampleContext context = new DepositSampleContext(
                    9081726354L, new BlockPos(index * 11, index % 128 - 64, index * -17),
                    index * 7919L, 20 + index % 37, index % 5 == 0 ? DepositClass.ECHO : DepositClass.HOME);
            work.add(() -> morphology.sample(context));
        }
        List<List<BlockPos>> serial = work.stream().map(task -> {
            try {
                return task.call();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).toList();
        var executor = Executors.newFixedThreadPool(32);
        try {
            var futures = executor.invokeAll(work);
            for (int index = 0; index < futures.size(); index++)
                assertEquals(serial.get(index), futures.get(index).get(), "concurrent sample " + index);
        } finally {
            executor.shutdownNow();
        }
    }

    private static DepositSampleContext context(
            DepositMorphology morphology, int archetype, int budget, DepositClass depositClass) {
        return new DepositSampleContext(981723L, new BlockPos(160 + morphology.ordinal() * 97, -32, -240),
                1201L + archetype * 7919L, budget, depositClass);
    }

    private static Set<BlockPos> sample(DepositMorphology morphology, int archetype, int budget) {
        return Set.copyOf(morphology.sampleArchetype(context(morphology, archetype, budget, DepositClass.HOME), archetype));
    }

    private static int horizontalSpan(Set<BlockPos> points) {
        return Math.max(span(points, 'x'), span(points, 'z'));
    }

    private static int span(Set<BlockPos> points, char axis) {
        int min = points.stream().mapToInt(point -> coordinate(point, axis)).min().orElseThrow();
        int max = points.stream().mapToInt(point -> coordinate(point, axis)).max().orElseThrow();
        return max - min + 1;
    }

    private static int coordinate(BlockPos pos, char axis) {
        return switch (axis) {
            case 'x' -> pos.getX();
            case 'y' -> pos.getY();
            case 'z' -> pos.getZ();
            default -> throw new IllegalArgumentException("unknown axis " + axis);
        };
    }
}
