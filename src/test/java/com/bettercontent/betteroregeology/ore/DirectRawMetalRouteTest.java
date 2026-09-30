package com.bettercontent.betteroregeology.ore;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DirectRawMetalRouteTest {
    @Test void mapsEveryRawMetalIdentityToItsAuthoredGeologicalRoute() {
        assertEquals("better_ore_geology:small_ore_chunk_ironstone",
                DirectRawMetalRoute.outputId("minecraft:raw_iron"));
        assertEquals("better_ore_geology:small_ore_chunk_copper_bloom",
                DirectRawMetalRoute.outputId("minecraft:raw_copper"));
        assertEquals("better_ore_geology:gold_concentrate",
                DirectRawMetalRoute.outputId("minecraft:raw_gold"));
        assertEquals("minecraft:iron_block", DirectRawMetalRoute.outputId("minecraft:raw_iron_block"));
        assertEquals("minecraft:copper_block", DirectRawMetalRoute.outputId("minecraft:raw_copper_block"));
        assertEquals("minecraft:gold_block", DirectRawMetalRoute.outputId("minecraft:raw_gold_block"));
    }

    @Test void leavesOrdinaryItemsUnchangedAndRegistersThePinnedSeaTerrorInterception() throws Exception {
        assertEquals("minecraft:iron_ingot", DirectRawMetalRoute.outputId("minecraft:iron_ingot"));
        Path config = Path.of("src/main/resources/better_ore_geology.mixins.json");
        Path seaTerrorMixin = Path.of("src/main/java/com/bettercontent/betteroregeology/mixin/SeaTerrorRawGoldRewardMixin.java");
        Path unearthedMixin = Path.of("src/main/java/com/bettercontent/betteroregeology/mixin/UnearthedRawOreDropMixin.java");
        String json = Files.readString(config);
        String seaTerror = Files.readString(seaTerrorMixin);
        String unearthed = Files.readString(unearthedMixin);
        assertTrue(json.contains("SeaTerrorRawGoldRewardMixin"));
        assertTrue(json.contains("UnearthedRawOreDropMixin"));
        assertTrue(seaTerror.contains("net.mcreator.borninchaosv.procedures.SeaTerrorStomachPriShchielchkiePKMProcedure"));
        assertTrue(seaTerror.contains("Items;RAW_GOLD:Lnet/minecraft/world/item/Item;"));
        assertTrue(seaTerror.contains("DirectRawMetalRoute.outputId(\"minecraft:raw_gold\")"));
        assertTrue(unearthed.contains("lilypuree.unearthed.block.type.VanillaOreTypes"));
        assertTrue(unearthed.contains("method = \"getOreDrop\""));
        assertTrue(unearthed.contains("DirectRawMetalRoute.outputId(originalId.toString())"));
    }
}
