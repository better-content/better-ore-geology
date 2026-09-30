package com.bettercontent.betteroregeology.worldgen;

import com.mojang.serialization.Codec;
import java.util.BitSet;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public final class LavaExposedOreFeature extends OreFeature {
    public LavaExposedOreFeature(Codec<OreConfiguration> codec) {
        super(codec);
    }

    @Override
    protected boolean doPlace(
            WorldGenLevel level,
            RandomSource random,
            OreConfiguration config,
            double startX,
            double endX,
            double startZ,
            double endZ,
            double startY,
            double endY,
            int minX,
            int minY,
            int minZ,
            int horizontalSize,
            int verticalSize) {
        int placed = 0;
        BitSet visited = new BitSet(horizontalSize * verticalSize * horizontalSize);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int size = config.size;
        double[] spheres = new double[size * 4];

        for (int i = 0; i < size; ++i) {
            float progress = (float) i / (float) size;
            double x = Mth.lerp(progress, startX, endX);
            double y = Mth.lerp(progress, startY, endY);
            double z = Mth.lerp(progress, startZ, endZ);
            double randomRadius = random.nextDouble() * (double) size / 16.0D;
            double radius = ((double) (Mth.sin((float) Math.PI * progress) + 1.0F) * randomRadius + 1.0D) / 2.0D;
            spheres[i * 4] = x;
            spheres[i * 4 + 1] = y;
            spheres[i * 4 + 2] = z;
            spheres[i * 4 + 3] = radius;
        }

        for (int first = 0; first < size - 1; ++first) {
            if (spheres[first * 4 + 3] <= 0.0D) continue;
            for (int second = first + 1; second < size; ++second) {
                if (spheres[second * 4 + 3] <= 0.0D) continue;
                double dx = spheres[first * 4] - spheres[second * 4];
                double dy = spheres[first * 4 + 1] - spheres[second * 4 + 1];
                double dz = spheres[first * 4 + 2] - spheres[second * 4 + 2];
                double dr = spheres[first * 4 + 3] - spheres[second * 4 + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0.0D) spheres[second * 4 + 3] = -1.0D;
                    else spheres[first * 4 + 3] = -1.0D;
                }
            }
        }

        try (BulkSectionAccess sections = new BulkSectionAccess(level)) {
            for (int sphere = 0; sphere < size; ++sphere) {
                double radius = spheres[sphere * 4 + 3];
                if (radius < 0.0D) continue;

                double centerX = spheres[sphere * 4];
                double centerY = spheres[sphere * 4 + 1];
                double centerZ = spheres[sphere * 4 + 2];
                int x0 = Math.max(Mth.floor(centerX - radius), minX);
                int y0 = Math.max(Mth.floor(centerY - radius), minY);
                int z0 = Math.max(Mth.floor(centerZ - radius), minZ);
                int x1 = Math.max(Mth.floor(centerX + radius), x0);
                int y1 = Math.max(Mth.floor(centerY + radius), y0);
                int z1 = Math.max(Mth.floor(centerZ + radius), z0);

                for (int x = x0; x <= x1; ++x) {
                    double normalizedX = ((double) x + 0.5D - centerX) / radius;
                    if (normalizedX * normalizedX >= 1.0D) continue;

                    for (int y = y0; y <= y1; ++y) {
                        double normalizedY = ((double) y + 0.5D - centerY) / radius;
                        if (normalizedX * normalizedX + normalizedY * normalizedY >= 1.0D) continue;

                        for (int z = z0; z <= z1; ++z) {
                            double normalizedZ = ((double) z + 0.5D - centerZ) / radius;
                            if (normalizedX * normalizedX + normalizedY * normalizedY + normalizedZ * normalizedZ >= 1.0D || level.isOutsideBuildHeight(y)) {
                                continue;
                            }

                            int visitedIndex = x - minX + (y - minY) * horizontalSize + (z - minZ) * horizontalSize * verticalSize;
                            if (visited.get(visitedIndex)) continue;
                            visited.set(visitedIndex);
                            mutablePos.set(x, y, z);
                            if (!level.ensureCanWrite(mutablePos)) continue;

                            LevelChunkSection section = sections.getSection(mutablePos);
                            if (section == null) continue;

                            int sectionX = SectionPos.sectionRelative(x);
                            int sectionY = SectionPos.sectionRelative(y);
                            int sectionZ = SectionPos.sectionRelative(z);
                            BlockState currentState = section.getBlockState(sectionX, sectionY, sectionZ);

                            for (OreConfiguration.TargetBlockState target : config.targetStates) {
                                if (canPlaceLavaExposedOre(currentState, sections::getBlockState, random, target, mutablePos)) {
                                    section.setBlockState(sectionX, sectionY, sectionZ, target.state, false);
                                    ++placed;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        return placed > 0;
    }

    private static boolean canPlaceLavaExposedOre(
            BlockState currentState,
            Function<BlockPos, BlockState> blockReader,
            RandomSource random,
            OreConfiguration.TargetBlockState target,
            BlockPos.MutableBlockPos pos) {
        return target.target.test(currentState, random)
                && Feature.checkNeighbors(blockReader, pos, state -> state.getFluidState().is(FluidTags.LAVA));
    }
}
