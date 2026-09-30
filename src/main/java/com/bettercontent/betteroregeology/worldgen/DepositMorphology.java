package com.bettercontent.betteroregeology.worldgen;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

/** Stateless, deterministic geological bodies with regionally coherent structural fabrics. */
public enum DepositMorphology implements StringRepresentable {
    BROKEN_STRATIFORM_SEAM("broken_stratiform_seam", 192,
            "channel-broken seam", "split benches", "fault-stepped seam", "folded pinch-out"),
    LENTICULAR_OOLITIC_BED("lenticular_oolitic_bed", 192,
            "single oolitic lens", "en-echelon lenses", "shoal lens chain", "erosional pod bed"),
    BRANCHING_STOCKWORK("branching_stockwork", 96,
            "crosscut stockwork", "sheeted veinlets", "arcuate stockwork", "breccia-margin veinlets"),
    STEEP_QUARTZ_LODE("steep_quartz_lode", 96,
            "faulted steep lode", "en-echelon twin lodes", "greisen-splay lode", "ladder-vein corridor"),
    DENDRITIC_FRACTURE_VEIN("dendritic_fracture_vein", 96,
            "joint-and-bedding fill", "stair-step fracture", "collapse pocket feeders", "en-echelon replacement"),
    STACKED_EVAPORITE_BEDS("stacked_evaporite_beds", 192,
            "paired beds", "rhythmic triple beds", "nodular lens chain", "dissolution-broken bed"),
    BRECCIA_PIPE_WITH_RADIAL_DIKES("breccia_pipe_with_radial_dikes", 48,
            "narrow crackle pipe", "asymmetric breccia corridor", "en-echelon breccia pods", "late-fissure pipe"),
    LAMINATED_SHALE_STRINGERS("laminated_shale_stringers", 192,
            "anastomosing fissility", "steep slaty cleavage", "crenulated carbon wisps", "compaction drapes");

    public static final Codec<DepositMorphology> CODEC = StringRepresentable.fromEnum(DepositMorphology::values);
    private static final int ARCHETYPE_COUNT = 4;
    private static final int HORIZONTAL_LIMIT = 12;
    private static final int VERTICAL_LIMIT = 16;
    private static final int[][] BED_GROWTH = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 1}, {-1, 0, -1}
    };
    private static final int[][] VEIN_GROWTH = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 0}, {-1, -1, 0}, {0, 1, 1}, {0, -1, -1}
    };
    private static final int[][] PIPE_GROWTH = {
            {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final String serializedName;
    private final int provinceScale;
    private final List<String> archetypes;

    DepositMorphology(String serializedName, int provinceScale, String... archetypes) {
        this.serializedName = serializedName;
        this.provinceScale = provinceScale;
        this.archetypes = List.of(archetypes);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public List<String> archetypes() {
        return archetypes;
    }

    public int selectArchetype(DepositSampleContext context) {
        long province = provinceSeed(context.worldSeed(), context.origin());
        int[] order = {0, 1, 2, 3};
        RandomSource provinceRandom = RandomSource.create(province);
        for (int index = order.length - 1; index > 0; index--) {
            int other = provinceRandom.nextInt(index + 1);
            int swap = order[index];
            order[index] = order[other];
            order[other] = swap;
        }
        int roll = RandomSource.create(localSeed(context)).nextInt(100);
        int[] weights = context.depositClass() == DepositClass.HOME
                ? new int[] {40, 30, 20, 10}
                : new int[] {15, 25, 30, 30};
        int total = 0;
        for (int rank = 0; rank < weights.length; rank++) {
            total += weights[rank];
            if (roll < total) return order[rank];
        }
        return order[order.length - 1];
    }

    public List<BlockPos> sample(DepositSampleContext context) {
        return sampleArchetype(context, selectArchetype(context));
    }

    /** Deterministic forced-archetype entry point for tests and review rendering. */
    public List<BlockPos> sampleArchetype(DepositSampleContext context, int archetype) {
        if (archetype < 0 || archetype >= ARCHETYPE_COUNT)
            throw new IllegalArgumentException("archetype must be in 0..3");
        long seed = localSeed(context) ^ mix64(archetype * 0x9e3779b97f4a7c15L);
        RandomSource random = RandomSource.create(seed);
        int strike = structuralStrike(context);
        ShapeBuilder shape = new ShapeBuilder(strike, seed, random);
        switch (this) {
            case BROKEN_STRATIFORM_SEAM -> coal(shape, random, context.blockBudget(), archetype);
            case LENTICULAR_OOLITIC_BED -> ironstone(shape, random, context.blockBudget(), archetype);
            case BRANCHING_STOCKWORK -> copper(shape, random, context.blockBudget(), archetype);
            case STEEP_QUARTZ_LODE -> tin(shape, random, context.blockBudget(), archetype);
            case DENDRITIC_FRACTURE_VEIN -> brassroot(shape, random, context.blockBudget(), archetype);
            case STACKED_EVAPORITE_BEDS -> evaporite(shape, random, context.blockBudget(), archetype);
            case BRECCIA_PIPE_WITH_RADIAL_DIKES -> hotstone(shape, random, context.blockBudget(), archetype);
            case LAMINATED_SHALE_STRINGERS -> blackShale(shape, random, context.blockBudget(), archetype);
        }
        return shape.finish(context.blockBudget(), growthDirections());
    }

    int structuralStrike(DepositSampleContext context) {
        long province = provinceSeed(context.worldSeed(), context.origin());
        int localJitter = (int) Math.floorMod(mix64(localSeed(context) ^ 0x4f1bbcdc6762c78dL), 3L) - 1;
        return Math.floorMod((int) province + localJitter, 32);
    }

    private int[][] growthDirections() {
        return switch (this) {
            case BROKEN_STRATIFORM_SEAM, LENTICULAR_OOLITIC_BED, STACKED_EVAPORITE_BEDS -> BED_GROWTH;
            case BRECCIA_PIPE_WITH_RADIAL_DIKES -> PIPE_GROWTH;
            default -> VEIN_GROWTH;
        };
    }

    private static void coal(ShapeBuilder s, RandomSource r, int budget, int type) {
        int half = Math.min(11, Math.max(6, budget / 5));
        int channel = r.nextInt(half * 2 - 2) - half + 2;
        for (int u = -half; u <= half; u++) {
            int y = switch (type) {
                case 2 -> u < 0 ? 0 : 2;
                case 3 -> Math.max(0, (u * u) / Math.max(8, half * half / 2)) - 1;
                default -> Math.floorDiv(u + half, 8) - 1;
            };
            int width = type == 3 ? Math.max(1, 3 - Math.abs(u) * 2 / Math.max(1, half)) : 2 + r.nextInt(2);
            boolean gap = type == 0 && Math.abs(u - channel) <= 1;
            if (!gap) s.add(u, y, 0, 0);
            for (int v = -width; v <= width; v++) if (!gap && (Math.abs(v) < width || r.nextFloat() > 0.25F))
                s.add(u, y, v, 1);
            if (type == 1 && Math.abs(u) > 1 && Math.floorMod(u, 5) != 0) {
                s.add(u, y + 2, 0, 1);
                if (Math.floorMod(u, 3) != 0) s.add(u, y + 2, u > 0 ? 1 : -1, 2);
            }
        }
    }

    private static void ironstone(ShapeBuilder s, RandomSource r, int budget, int type) {
        int lenses = type == 0 ? 1 : type == 2 ? 4 : 2 + r.nextInt(2);
        for (int lens = 0; lens < lenses; lens++) {
            int u = (lens - (lenses - 1) / 2) * (type == 2 ? 4 : 5) + r.nextInt(3) - 1;
            int v = (lens % 2 == 0 ? -1 : 1) * (type == 1 ? 2 : 1);
            int y = type == 3 && lens == lenses - 1 ? 2 : r.nextInt(2);
            int ru = type == 0 ? Math.min(7, Math.max(4, budget / 5)) : 2 + r.nextInt(3);
            int rv = 2 + r.nextInt(2);
            s.ellipse(u, y, v, ru, rv, lens == 0 ? 0 : 1, type == 3 ? 0.68F : 0.82F);
            if (r.nextFloat() < 0.65F) s.ellipse(u, y + 1, v, Math.max(1, ru - 2), 1, 2, 0.65F);
        }
    }

    private static void copper(ShapeBuilder s, RandomSource r, int budget, int type) {
        int branches = 4 + type + r.nextInt(2);
        for (int branch = 0; branch < branches; branch++) {
            int sign = branch % 2 == 0 ? 1 : -1;
            int u = sign * (4 + r.nextInt(5));
            int v = ((branch * 3 + type * 2) % 9) - 4 + (type == 2 ? sign * 2 : 0);
            int y = type == 1 ? (branch - branches / 2) * 2 : r.nextInt(9) - 4;
            s.line(0, 0, 0, u, y, v, branch < 2 ? 0 : 1, type == 1 ? 0.18F : 0.10F);
            if (type == 3 && branch % 2 == 0)
                s.line(u / 2, y / 2, v / 2, u, y + sign * 2, v + sign * 2, 2, 0.20F);
        }
    }

    private static void tin(ShapeBuilder s, RandomSource r, int budget, int type) {
        int half = Math.min(8, Math.max(5, budget / 4));
        int fault = r.nextInt(3) - 1;
        int rake = r.nextBoolean() ? 1 : -1;
        int wanderingV = r.nextInt(3) - 1;
        for (int u = -half; u <= half; u++) {
            int roughness = r.nextInt(3) - 1;
            int y = u + roughness + (type == 0 && u > fault ? 2 : 0);
            if (Math.floorMod(u + half, 3) == 0) wanderingV += r.nextInt(3) - 1;
            wanderingV = Math.max(-3, Math.min(3, wanderingV));
            int v = type == 1 ? (u > 0 ? 2 : -2) + wanderingV / 2
                    : Math.floorDiv(u + half, 6) - 1 + wanderingV;
            if (!(type == 1 && Math.abs(u) <= 1)) {
                s.add(u, y, v, 0);
                s.add(u, y, v + 1, 1);
                if (r.nextFloat() < 0.36F) s.add(u, y + rake, v, 2);
            }
            if (type == 2 && Math.floorMod(u + fault, 4) == 0)
                s.line(u, y, v, u + 2 + r.nextInt(3), y + rake, v + 2 + r.nextInt(3), 2, 0.08F);
            if (type == 3 && Math.floorMod(u + fault, 3) == 0)
                s.line(u, y, v, u + r.nextInt(3) - 1, y + 2 + r.nextInt(2), v + 2 + r.nextInt(3), 2, 0.08F);
        }
    }

    private static void brassroot(ShapeBuilder s, RandomSource r, int budget, int type) {
        int half = Math.min(7, Math.max(4, budget / 4));
        s.line(-half, -half, 0, half, half, type == 1 ? 2 : 0, 0, 0.08F);
        if (type == 0 || type == 1) {
            for (int y = -half + 2; y <= half - 2; y += 3)
                s.line(y, y, 0, y + (y % 2 == 0 ? 4 : -4), y, y % 2 == 0 ? 2 : -2, 1, 0.15F);
        } else if (type == 2) {
            s.ellipse(1, 0, 1, 3, 2, 1, 0.58F);
            s.line(-half, -half, 0, 1, 0, 1, 0, 0.0F);
            s.line(1, 0, 1, half, half - 1, -1, 1, 0.12F);
        } else {
            s.line(-half, -half, -2, -1, -1, -1, 0, 0.0F);
            s.line(1, 1, 1, half, half, 2, 0, 0.0F);
            for (int u = -half + 2; u < half; u += 3) s.add(u, u, u > 0 ? 2 : -2, 2);
        }
    }

    private static void evaporite(ShapeBuilder s, RandomSource r, int budget, int type) {
        int radius = Math.min(7, Math.max(3, (int) Math.ceil(Math.sqrt(budget / 2.0D))));
        int[] layers = type == 1 ? new int[] {-2, 0, 2} : new int[] {-1, 1};
        int cut = r.nextInt(radius * 2 + 1) - radius;
        for (int layer : layers) {
            for (int u = -radius; u <= radius; u++) for (int v = -radius; v <= radius; v++) {
                boolean within = u * u + v * v <= radius * radius;
                boolean dissolved = type == 3 && Math.abs(u - cut) <= 1;
                boolean nodule = type == 2 && Math.abs(u + v + layer) % 5 <= 1;
                if (within && !dissolved && (nodule || r.nextFloat() > 0.25F))
                    s.add(u, layer, v, Math.abs(layer) == 1 ? 1 : 2);
            }
        }
        s.line(-radius / 2, 0, 0, radius / 2, 0, 0, 0, 0.0F);
        if (type == 3) s.line(cut, -2, -radius / 2, cut + 1, 2, radius / 2, 2, 0.15F);
    }

    private static void hotstone(ShapeBuilder s, RandomSource r, int budget, int type) {
        int half = Math.min(8, Math.max(4, budget / 5));
        int lean = type == 1 ? (r.nextBoolean() ? 1 : -1) : r.nextInt(3) - 1;
        int wanderingU = 0;
        int wanderingV = 0;
        for (int y = -half; y <= half; y++) {
            if (Math.floorMod(y + half, 2) == 0) {
                wanderingU = Math.max(-2, Math.min(2, wanderingU + r.nextInt(3) - 1));
                wanderingV = Math.max(-2, Math.min(2, wanderingV + r.nextInt(3) - 1));
            }
            int u = wanderingU + (lean == 0 ? 0 : Math.floorDiv(y + half, 4) * lean);
            int v = wanderingV;
            s.add(u, y, v, 0);
            if (Math.floorMod(y + r.nextInt(2), type == 0 ? 2 : 3) == 0) {
                s.add(u + 1, y, v, 1);
                s.add(u - 1, y, v, 1);
                if (r.nextFloat() < 0.82F) s.add(u, y, v + 1, 1);
                if (r.nextFloat() < 0.82F) s.add(u, y, v - 1, 1);
            }
        }
        if (type == 2) {
            s.ellipse(-2, -2, 0, 2, 2, 1, 0.62F);
            s.ellipse(2, 3, 1, 2, 2, 1, 0.62F);
        }
        int spokes = type == 3 ? 2 : 3 + r.nextInt(3);
        for (int spoke = 0; spoke < spokes; spoke++) {
            int y = r.nextInt(half * 2 + 1) - half;
            int u = (spoke % 2 == 0 ? 1 : -1) * (2 + r.nextInt(4));
            int v = (spoke % 3 - 1) * (2 + r.nextInt(3));
            s.line(0, y, 0, u, y + r.nextInt(3) - 1, v, 2, 0.12F);
        }
    }

    private static void blackShale(ShapeBuilder s, RandomSource r, int budget, int type) {
        int half = Math.min(9, Math.max(5, budget / 3));
        if (type == 1) {
            for (int ribbon = -1; ribbon <= 1; ribbon++)
                s.line(-half + Math.abs(ribbon), -half, ribbon * 2, half - Math.abs(ribbon), half, ribbon * 2,
                        ribbon == 0 ? 0 : 1, 0.22F);
            return;
        }
        int films = type == 0 ? 4 : 3;
        for (int film = 0; film < films; film++) {
            int filmLift = r.nextInt(3) - 1;
            int filmDrift = r.nextInt(3) - 1;
            for (int u = -half; u <= half; u++) {
                int wave = switch (type) {
                    case 0 -> Math.floorMod(u + film * 2, 7) < 3 ? 0 : 1;
                    case 2 -> Math.abs(u) / 3 - 2 + (film - 1);
                    default -> (u * u) / Math.max(5, half) / 2 - 2 + film;
                };
                wave += filmLift;
                int v = film * 2 - films + filmDrift + (type == 2 && u > 0 ? 1 : 0);
                boolean concretionGap = type == 3 && Math.abs(u - (film - 1) * 3) <= 1;
                if (!concretionGap && Math.floorMod(u + film * 3, 6) != 0)
                    s.add(u, wave, v, film == 1 ? 0 : 1);
            }
        }
        if (type == 2) s.line(-2, -1, -2, 2, 2, 2, 2, 0.20F);
    }

    private long provinceSeed(long worldSeed, BlockPos origin) {
        int cellX = Math.floorDiv(origin.getX(), provinceScale);
        int cellZ = Math.floorDiv(origin.getZ(), provinceScale);
        long bestDistance = Long.MAX_VALUE;
        long bestSeed = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int candidateX = cellX + dx;
            int candidateZ = cellZ + dz;
            long seed = mix64(worldSeed ^ ((long) ordinal() << 52)
                    ^ candidateX * 0x9e3779b97f4a7c15L ^ candidateZ * 0xc2b2ae3d27d4eb4fL);
            int jitterX = (int) Math.floorMod(seed, provinceScale / 2) - provinceScale / 4;
            int jitterZ = (int) Math.floorMod(seed >>> 24, provinceScale / 2) - provinceScale / 4;
            long centerX = (long) candidateX * provinceScale + provinceScale / 2L + jitterX;
            long centerZ = (long) candidateZ * provinceScale + provinceScale / 2L + jitterZ;
            long x = origin.getX() - centerX;
            long z = origin.getZ() - centerZ;
            long distance = x * x + z * z;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSeed = seed;
            }
        }
        return bestSeed;
    }

    private long localSeed(DepositSampleContext context) {
        return mix64(context.worldSeed() ^ context.occurrenceSalt()
                ^ context.origin().getX() * 0x632be59bd9b4e019L
                ^ context.origin().getY() * 0x9e3779b97f4a7c15L
                ^ context.origin().getZ() * 0xc2b2ae3d27d4eb4fL
                ^ ((long) ordinal() << 48) ^ ((long) context.depositClass().ordinal() << 44));
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    private static final class ShapeBuilder {
        private final int strike;
        private final long seed;
        private final RandomSource random;
        private final Map<BlockPos, Integer> candidates = new HashMap<>();

        private ShapeBuilder(int strike, long seed, RandomSource random) {
            this.strike = strike;
            this.seed = seed;
            this.random = random;
            add(0, 0, 0, 0);
        }

        private void add(int u, int y, int v, int priority) {
            BlockPos point = rotate(u, y, v);
            if (Math.abs(point.getX()) > HORIZONTAL_LIMIT || Math.abs(point.getZ()) > HORIZONTAL_LIMIT
                    || Math.abs(point.getY()) > VERTICAL_LIMIT) return;
            candidates.merge(point, priority, Math::min);
        }

        private void line(int u0, int y0, int v0, int u1, int y1, int v1,
                          int priority, float gapChance) {
            int steps = Math.max(Math.abs(u1 - u0), Math.max(Math.abs(y1 - y0), Math.abs(v1 - v0)));
            for (int step = 0; step <= steps; step++) {
                if (step != 0 && step != steps && random.nextFloat() < gapChance) continue;
                double fraction = steps == 0 ? 0.0D : (double) step / steps;
                add((int) Math.round(u0 + (u1 - u0) * fraction),
                        (int) Math.round(y0 + (y1 - y0) * fraction),
                        (int) Math.round(v0 + (v1 - v0) * fraction), priority);
            }
        }

        private void ellipse(int centerU, int y, int centerV, int radiusU, int radiusV,
                             int priority, float fillChance) {
            for (int u = -radiusU; u <= radiusU; u++) for (int v = -radiusV; v <= radiusV; v++) {
                double distance = (double) u * u / (radiusU * radiusU)
                        + (double) v * v / (radiusV * radiusV);
                if (distance <= 1.0D && (u == 0 && v == 0 || random.nextFloat() < fillChance))
                    add(centerU + u, y, centerV + v, priority);
            }
        }

        private List<BlockPos> finish(int budget, int[][] localGrowth) {
            List<Map.Entry<BlockPos, Integer>> ranked = new ArrayList<>(candidates.entrySet());
            ranked.sort(Comparator.comparingInt(Map.Entry<BlockPos, Integer>::getValue)
                    .thenComparingInt(entry -> entry.getKey().distManhattan(BlockPos.ZERO))
                    .thenComparingLong(entry -> mix64(seed ^ entry.getKey().asLong()))
                    .thenComparingInt(entry -> entry.getKey().getX())
                    .thenComparingInt(entry -> entry.getKey().getY())
                    .thenComparingInt(entry -> entry.getKey().getZ()));
            LinkedHashSet<BlockPos> selected = new LinkedHashSet<>();
            for (Map.Entry<BlockPos, Integer> entry : ranked) {
                if (selected.size() == budget) break;
                selected.add(entry.getKey());
            }
            for (int attempt = 0; selected.size() < budget && attempt < budget * 128; attempt++) {
                List<BlockPos> existing = List.copyOf(selected);
                BlockPos base = existing.get(random.nextInt(existing.size()));
                int[] direction = localGrowth[random.nextInt(localGrowth.length)];
                BlockPos delta = rotate(direction[0], direction[1], direction[2]);
                BlockPos grown = base.offset(delta);
                if (Math.abs(grown.getX()) <= HORIZONTAL_LIMIT && Math.abs(grown.getZ()) <= HORIZONTAL_LIMIT
                        && Math.abs(grown.getY()) <= VERTICAL_LIMIT) selected.add(grown);
            }
            if (selected.size() != budget)
                throw new IllegalStateException("could not satisfy geological block budget " + budget);
            return selected.stream().sorted(Comparator.comparingInt((BlockPos pos) -> pos.getX())
                    .thenComparingInt(pos -> pos.getY()).thenComparingInt(pos -> pos.getZ())).toList();
        }

        private BlockPos rotate(int u, int y, int v) {
            double angle = strike * (StrictMath.PI * 2.0D / 32.0D);
            int x = (int) StrictMath.round(u * StrictMath.cos(angle) - v * StrictMath.sin(angle));
            int z = (int) StrictMath.round(u * StrictMath.sin(angle) + v * StrictMath.cos(angle));
            return new BlockPos(x, y, z);
        }
    }
}
