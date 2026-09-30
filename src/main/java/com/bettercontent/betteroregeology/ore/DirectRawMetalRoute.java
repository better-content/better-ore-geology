package com.bettercontent.betteroregeology.ore;

import java.util.Map;

/** Item identities used when an active Java reward bypasses LootJS loot-table modifiers. */
public final class DirectRawMetalRoute {
    private static final Map<String, String> OUTPUTS = Map.of(
            "minecraft:raw_iron", "better_ore_geology:small_ore_chunk_ironstone",
            "minecraft:raw_copper", "better_ore_geology:small_ore_chunk_copper_bloom",
            "minecraft:raw_gold", "better_ore_geology:gold_concentrate",
            "minecraft:raw_iron_block", "minecraft:iron_block",
            "minecraft:raw_copper_block", "minecraft:copper_block",
            "minecraft:raw_gold_block", "minecraft:gold_block");

    private DirectRawMetalRoute() {
    }

    public static String outputId(String inputId) {
        return OUTPUTS.getOrDefault(inputId, inputId);
    }
}
