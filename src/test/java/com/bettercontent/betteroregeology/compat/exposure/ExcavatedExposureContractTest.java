package com.bettercontent.betteroregeology.compat.exposure;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class ExcavatedExposureContractTest {
    @Test
    void everyAlternativeRetainsItsHostGroupAndTextureOrdinal() {
        for (int host = 0; host < 3; host++) {
            for (int current = 0; current < 12; current++) {
                String stem = "excavated_variants:block/granite_ironstone_";
                String location = stem + (host * 12 + current);
                JsonObject source = JsonParser.parseString("""
                        {"textures":{},"elements":[{"faces":{"north":{"texture":"#side"}}}],
                         "ambientocclusion":false,"display":{"gui":{"rotation":[1,2,3]}}}
                        """).getAsJsonObject();
                source.getAsJsonObject("textures").addProperty("side", location + "_1");
                source.getAsJsonObject("textures").addProperty("top", location + "_2");
                JsonObject result = ExcavatedExposureModels.decorate(source, location, "ironstone").getAsJsonObject();
                assertEquals("better_ore_geology:exposure", result.get("loader").getAsString());
                assertEquals("ironstone", result.get("family").getAsString());
                assertEquals(source.get("elements"), result.get("elements"));
                assertEquals(source.get("display"), result.get("display"));
                assertEquals(source.get("ambientocclusion"), result.get("ambientocclusion"));
                assertEquals(2, source.getAsJsonObject("textures").size(), "Input model must remain untouched");
                assertEquals(26, result.getAsJsonObject("textures").size());
                for (int slot = 0; slot < 12; slot++) {
                    assertEquals(stem + (host * 12 + slot) + "_1",
                            result.getAsJsonObject("textures").get("slot" + slot + "_side").getAsString());
                    assertEquals(stem + (host * 12 + slot) + "_2",
                            result.getAsJsonObject("textures").get("slot" + slot + "_top").getAsString());
                }
            }
        }
    }

    @Test
    void incompatibleSpriteNamingFailsRatherThanMixingHosts() {
        JsonObject model = JsonParser.parseString("""
                {"textures":{"all":"excavated_variants:block/granite_ironstone_0_bad"}}
                """).getAsJsonObject();
        assertThrows(IllegalStateException.class, () -> ExcavatedExposureModels.decorate(
                model, "excavated_variants:block/granite_ironstone_0", "ironstone"));
    }

    @Test
    void inheritedAliasesAndNonGeneratedHostTexturesSurvive() {
        JsonObject model = JsonParser.parseString("""
                {"textures":{"side":"excavated_variants:block/granite_ironstone_0_1",
                 "particle":"#side","untouched":"minecraft:block/stone"}}
                """).getAsJsonObject();
        JsonObject textures = ExcavatedExposureModels.decorate(model,
                "excavated_variants:block/granite_ironstone_0", "ironstone")
                .getAsJsonObject().getAsJsonObject("textures");
        for (int slot = 0; slot < 12; slot++) {
            assertEquals("#slot" + slot + "_side", textures.get("slot" + slot + "_particle").getAsString());
            assertEquals("minecraft:block/stone", textures.get("slot" + slot + "_untouched").getAsString());
        }
    }

    @Test
    void blendMatchesAuthoredIntegerSrcOverForEveryAlpha() {
        Random random = new Random(0x455650L);
        for (int alpha = 0; alpha < 256; alpha++) {
            for (int sample = 0; sample < 64; sample++) {
                int host = random.nextInt() | 0xff000000;
                int overlay = random.nextInt() & 0xffffff | alpha << 24;
                int result = ExposureComposite.over(host, overlay);
                assertEquals(255, result >>> 24);
                for (int shift : new int[] {0, 8, 16}) {
                    int expected = (((overlay >>> shift) & 255) * alpha
                            + ((host >>> shift) & 255) * (255 - alpha)) / 255;
                    assertEquals(expected, (result >>> shift) & 255);
                }
                if (alpha == 0) assertEquals(host, result);
                if (alpha == 255) assertEquals(overlay, result);
            }
        }
    }
}
