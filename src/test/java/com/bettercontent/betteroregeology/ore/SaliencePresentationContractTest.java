package com.bettercontent.betteroregeology.ore;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

final class SaliencePresentationContractTest {
    @Test
    void oreTooltipsUseTheCanonicalEightCellBadgeFont() throws Exception {
        byte[] badge = resource("/assets/better_ore_geology/textures/gui/aspect_badges.png").readAllBytes();
        assertEquals("84bc0c5fe762fe2df5f1ed53a2e138ecb03bbfbb386657039ad8333927bf51ab",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(badge)));
        var image = ImageIO.read(new java.io.ByteArrayInputStream(badge));
        assertEquals(144, image.getWidth()); assertEquals(18, image.getHeight());
        String font = new String(resource("/assets/better_ore_geology/font/aspects.json").readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(font.contains(""));
    }

    @Test
    void everyOreAspectHasItsOwnPhysicalOgg() throws Exception {
        String sounds = new String(resource("/assets/better_ore_geology/sounds.json").readAllBytes(), StandardCharsets.UTF_8);
        for (String aspect : new String[]{"impact", "tempo", "work", "mobility", "endurance", "robustness", "renewal", "control"}) {
            assertTrue(sounds.contains("aspect." + aspect));
            byte[] ogg = resource("/assets/better_ore_geology/sounds/aspect/" + aspect + ".ogg").readAllBytes();
            assertTrue(ogg.length > 8_000, aspect);
            assertArrayEquals(new byte[]{'O', 'g', 'g', 'S'}, java.util.Arrays.copyOf(ogg, 4));
        }
    }

    private InputStream resource(String path) {
        InputStream stream = getClass().getResourceAsStream(path);
        assertNotNull(stream, path);
        return stream;
    }
}
