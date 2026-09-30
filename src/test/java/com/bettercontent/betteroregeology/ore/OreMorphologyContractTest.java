package com.bettercontent.betteroregeology.ore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OreMorphologyContractTest {
    private static final Path MASTERS = Path.of("art/block-masters/geology-v7");
    private static final Map<String, String> MORPHOLOGIES = Map.of(
        "hotstone", "asymmetric_breccia_pipe_with_radial_fissures",
        "copper_bloom", "branching_stockwork_with_oxidation_halos",
        "tin_quartz", "steep_quartz_lodes_with_cassiterite_splays",
        "brassroot", "asymmetric_dendritic_fracture_vein",
        "coal_measures", "broken_uneven_stratiform_carbon_seams",
        "ironstone", "rusty_lenticular_oolitic_beds_and_pods",
        "evaporite_beds", "stacked_salt_gypsum_beds_and_crystalline_pockets",
        "black_shale", "tapered_fissility_oblique_cleavage_and_crenulated_carbon_wisps");

    @Test
    void manifestLocksTheEightNonColorMorphologies() throws Exception {
        JsonObject manifest = JsonParser.parseString(
                Files.readString(Path.of("tools/ore_art_manifest.json"))).getAsJsonObject();
        assertEquals(MORPHOLOGIES.keySet(), manifest.keySet());
        MORPHOLOGIES.forEach((family, morphology) ->
            assertEquals(morphology,
                    manifest.getAsJsonObject(family).get("morphology").getAsString(), family));
    }

    @Test
    void approvedMastersSurviveDirectReductionAsDistinctEdgeCrossingTextures() throws Exception {
        Set<Integer> silhouettes = new HashSet<>();
        for (String family : MORPHOLOGIES.keySet()) {
            for (int variant = 0; variant < 3; variant++) {
                Path path = MASTERS.resolve(family).resolve("variant_" + variant + ".png");
                BufferedImage master = ImageIO.read(path.toFile());
                assertTrue(master != null && master.getColorModel().hasAlpha(), path.toString());
                boolean[] mask = reduceMask(master);
                int visible = count(mask);
                assertTrue(visible >= 50 && visible <= 120,
                        family + " variant " + variant + " visible coverage " + visible);
                assertTrue(touchedEdges(mask) >= 2,
                        family + " variant " + variant + " must cross at least two edges");
                assertTrue(silhouettes.add(Arrays.hashCode(mask)),
                        family + " variant " + variant + " duplicates another silhouette");
            }
        }
        assertEquals(24, silhouettes.size());
    }

    @Test
    void coalBenchesRemainMorphologicallyDistinctFromBlackShaleFissility() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            boolean[] coal = mask("coal_measures", variant);
            boolean[] shale = mask("black_shale", variant);
            assertEquals(16, maxRow(coal), "Coal must retain an edge-to-edge bed: variant " + variant);
            assertTrue(broadRows(coal) >= 5, "Coal needs multiple thick bench rows: variant " + variant);
            assertTrue(maxRow(shale) <= 9, "Black Shale must remain broken fissility: variant " + variant);
            assertTrue(broadRows(shale) <= 3, "Black Shale must not become a coal bench: variant " + variant);
        }
    }

    private static boolean[] mask(String family, int variant) throws Exception {
        return reduceMask(ImageIO.read(MASTERS.resolve(family)
                .resolve("variant_" + variant + ".png").toFile()));
    }

    private static boolean[] reduceMask(BufferedImage source) {
        boolean[] result = new boolean[256];
        for (int y = 0; y < 16; y++) {
            int sourceY = (int) (((long) (2 * y + 1) * source.getHeight()) / 32L);
            for (int x = 0; x < 16; x++) {
                int sourceX = (int) (((long) (2 * x + 1) * source.getWidth()) / 32L);
                result[y * 16 + x] = (source.getRGB(sourceX, sourceY) >>> 24) >= 128;
            }
        }
        return result;
    }

    private static int count(boolean[] mask) {
        int result = 0;
        for (boolean value : mask) if (value) result++;
        return result;
    }

    private static int touchedEdges(boolean[] mask) {
        boolean left = false, right = false, top = false, bottom = false;
        for (int offset = 0; offset < 16; offset++) {
            left |= mask[offset * 16];
            right |= mask[offset * 16 + 15];
            top |= mask[offset];
            bottom |= mask[15 * 16 + offset];
        }
        return (left ? 1 : 0) + (right ? 1 : 0) + (top ? 1 : 0) + (bottom ? 1 : 0);
    }

    private static int maxRow(boolean[] mask) {
        int result = 0;
        for (int y = 0; y < 16; y++) {
            int count = 0;
            for (int x = 0; x < 16; x++) if (mask[y * 16 + x]) count++;
            result = Math.max(result, count);
        }
        return result;
    }

    private static int broadRows(boolean[] mask) {
        int result = 0;
        for (int y = 0; y < 16; y++) {
            int count = 0;
            for (int x = 0; x < 16; x++) if (mask[y * 16 + x]) count++;
            if (count >= 8) result++;
        }
        return result;
    }
}
