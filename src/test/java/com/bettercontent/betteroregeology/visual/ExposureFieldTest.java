package com.bettercontent.betteroregeology.visual;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExposureFieldTest {
    @Test
    void styleSpaceContainsExactlyThe64LegalPersistentStates() {
        Set<Integer> styles = new HashSet<>();
        for (int direction = 0; direction < 8; direction++) {
            for (long salt = 0; salt < 4096; salt++) {
                int style = ExposureField.style(direction * 4, salt);
                assertTrue(style >= 0 && style < 64);
                assertEquals(direction, style / 8);
                styles.add(style);
            }
        }
        assertEquals(64, styles.size());
        for (String family : ExposureField.FAMILIES) {
            for (int x = -33; x <= 33; x += 11) {
                int placed = ExposureField.placedStyle(family, x, -19, -x);
                assertTrue(placed >= 0 && placed < 64);
                assertEquals(placed, ExposureField.placedStyle(family, x, -19, -x));
            }
        }
    }

    @Test
    void roleAndVariantAreDeterministicForSignedWorldCoordinates() {
        for (String family : ExposureField.FAMILIES) for (int style = 0; style < 64; style++) {
            for (double x : new double[] {-33.25, -16.25, -0.25, 0.25, 15.75, 32.25}) {
                double y = -12.375, z = -23.625;
                int role = ExposureField.role(family, style, x, y, z);
                assertTrue(role >= 0 && role < 4);
                assertEquals(role, ExposureField.role(family, style, x, y, z));
                int variant = ExposureField.variant(family, style, role, x, y, z);
                assertTrue(variant >= 0 && variant < 3);
                assertEquals(variant, ExposureField.variant(family, style, role, x, y, z));
            }
        }
    }

    @Test
    void continuousRolesDoNotGainNegativeBlockOrChunkBorderSeams() {
        double epsilon = 1e-7;
        for (String family : ExposureField.FAMILIES) for (int style = 0; style < 64; style += 5) {
            for (int border : new int[] {-32, -16, -4, -1, 0, 1, 4, 16}) {
                for (int sample = 0; sample < 16; sample++) {
                    double other = sample * 1.37 - 10.125;
                    assertEquals(
                            ExposureField.role(family, style, border - epsilon, -7.625, other),
                            ExposureField.role(family, style, border + epsilon, -7.625, other),
                            family + " x border " + border);
                    assertEquals(
                            ExposureField.role(family, style, other, border - epsilon, -7.625),
                            ExposureField.role(family, style, other, border + epsilon, -7.625),
                            family + " y border " + border);
                    assertEquals(
                            ExposureField.role(family, style, other, -7.625, border - epsilon),
                            ExposureField.role(family, style, other, -7.625, border + epsilon),
                            family + " z border " + border);
                }
            }
        }
    }

    @Test
    void everyRoleCanSelectThreeIndependentPatternIds() {
        for (String family : ExposureField.FAMILIES) for (int role = 0; role < 4; role++) {
            Set<Integer> variants = new HashSet<>();
            for (int style = 0; style < 64; style += 7) {
                for (int x = -24; x <= 24; x += 4) {
                    variants.add(ExposureField.variant(family, style, role, x + 0.5, -7.5, -x + 0.5));
                }
            }
            assertEquals(Set.of(0, 1, 2), variants, family + " role " + role);
        }
    }

    @Test
    void broadFieldHasQuietDominanceAndRareOriginals() {
        for (String family : ExposureField.FAMILIES) {
            long[] counts = new long[4];
            for (int style = 0; style < 64; style++) {
                for (int x = -24; x <= 24; x += 4) for (int y = -12; y <= 12; y += 2)
                    for (int z = -24; z <= 24; z += 4) {
                        counts[ExposureField.role(family, style, x + 0.5, y + 0.5, z + 0.5)]++;
                    }
            }
            long total = counts[0] + counts[1] + counts[2] + counts[3];
            assertTrue(counts[0] > counts[1] && counts[0] > counts[2] && counts[0] > counts[3],
                    family + " quiet should be the most common role: " + java.util.Arrays.toString(counts));
            assertTrue(counts[3] > 0 && counts[3] * 10 < total,
                    family + " original should occur but remain under 10%: "
                            + java.util.Arrays.toString(counts));
        }
    }

    @Test
    void roleFieldCanVaryWithinOneBlockFace() {
        for (String family : ExposureField.FAMILIES) {
            boolean foundMixedFace = false;
            for (int style = 0; style < 64 && !foundMixedFace; style += 3) {
                for (int blockX = -12; blockX <= 12 && !foundMixedFace; blockX += 3)
                    for (int blockY = -8; blockY <= 8 && !foundMixedFace; blockY += 2) {
                        int first = -1;
                        for (int tx = 0; tx < 16; tx++) for (int ty = 0; ty < 16; ty++) {
                            int role = ExposureField.role(family, style,
                                    blockX + (tx + 0.5) / 16.0,
                                    blockY + (ty + 0.5) / 16.0, -4.0);
                            if (first < 0) first = role;
                            else if (role != first) foundMixedFace = true;
                        }
                    }
            }
            assertTrue(foundMixedFace, family + " should permit partial exposure within a face");
        }
    }
}
