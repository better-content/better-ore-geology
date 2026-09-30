package com.bettercontent.betteroregeology.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

final class OreDefinitionTest {
    private static final Gson GSON = new Gson();

    @Test
    void validatesCubeAllDefinitionAndDerivesPrimaryCrushedItem() {
        OreDefinition definition = GSON.fromJson("""
                {
                  "id": "test_ore",
                  "display_name": "Test Ore",
                  "variants": [
                    {
                      "host": "stone",
                      "block_id": "test_ore",
                      "texture_mode": "cube_all",
                      "textures": { "all": "better_ore_geology:block/test_ore" },
                      "copy_properties_from": "minecraft:stone"
                    }
                  ]
                }
                """, OreDefinition.class);

        definition.validate();

        assertEquals("test_ore", definition.primaryVariant().blockId());
        assertEquals("ore_chunk_test_ore", definition.oreChunkItemId());
        assertEquals("small_ore_chunk_test_ore", definition.smallOreChunkItemId());
        assertEquals("crushed_test_ore", definition.crushedItemId());
        assertEquals("surface_sample_test_ore", definition.surfaceSampleBlockId());
        assertEquals("stone", definition.primaryVariant().host());
    }

    @Test
    void rejectsDuplicateVariantHosts() {
        OreDefinition definition = GSON.fromJson("""
                {
                  "id": "test_ore",
                  "display_name": "Test Ore",
                  "variants": [
                    {
                      "host": "stone",
                      "block_id": "test_ore",
                      "texture_mode": "cube_all",
                      "textures": { "all": "better_ore_geology:block/test_ore" },
                      "copy_properties_from": "minecraft:stone"
                    },
                    {
                      "host": "stone",
                      "block_id": "test_ore_duplicate",
                      "texture_mode": "cube_all",
                      "textures": { "all": "better_ore_geology:block/test_ore_duplicate" },
                      "copy_properties_from": "minecraft:stone"
                    }
                  ]
                }
                """, OreDefinition.class);

        assertThrows(IllegalArgumentException.class, definition::validate);
    }

    @Test
    void rejectsColumnTexturesWithoutAllRequiredFaces() {
        OreDefinition definition = GSON.fromJson("""
                {
                  "id": "test_ore",
                  "display_name": "Test Ore",
                  "variants": [
                    {
                      "host": "deepslate",
                      "block_id": "deepslate_test_ore",
                      "texture_mode": "cube_column_like",
                      "textures": {
                        "side": "better_ore_geology:block/deepslate_test_ore_side",
                        "top": "better_ore_geology:block/deepslate_test_ore_top"
                      },
                      "copy_properties_from": "minecraft:deepslate"
                    }
                  ]
                }
                """, OreDefinition.class);

        assertThrows(IllegalArgumentException.class, definition::validate);
    }

    @Test
    void rejectsSidedTexturesWithoutAllRequiredFaces() {
        OreDefinition definition = GSON.fromJson("""
                {
                  "id": "test_ore",
                  "display_name": "Test Ore",
                  "variants": [
                    {
                      "host": "stone",
                      "block_id": "test_ore",
                      "texture_mode": "cube_sided",
                      "textures": {
                        "north": "better_ore_geology:block/test_ore_0_north",
                        "east": "better_ore_geology:block/test_ore_0_east",
                        "south": "better_ore_geology:block/test_ore_0_south",
                        "west": "better_ore_geology:block/test_ore_0_west",
                        "up": "better_ore_geology:block/test_ore_0_up"
                      },
                      "copy_properties_from": "minecraft:stone"
                    }
                  ]
                }
                """, OreDefinition.class);

        assertThrows(IllegalArgumentException.class, definition::validate);
    }

    @Test
    void fallsBackToFirstVariantWhenStoneHostIsAbsent() {
        OreDefinition definition = GSON.fromJson("""
                {
                  "id": "test_ore",
                  "display_name": "Test Ore",
                  "variants": [
                    {
                      "host": "deepslate",
                      "block_id": "deepslate_test_ore",
                      "texture_mode": "cube_column_like",
                      "textures": {
                        "side": "better_ore_geology:block/deepslate_test_ore_side",
                        "top": "better_ore_geology:block/deepslate_test_ore_top",
                        "bottom": "better_ore_geology:block/deepslate_test_ore_bottom"
                      },
                      "copy_properties_from": "minecraft:deepslate"
                    }
                  ]
                }
                """, OreDefinition.class);

        definition.validate();

        assertEquals("deepslate", definition.primaryVariant().host());
        assertTrue(definition.variantByHost("deepslate").isPresent());
        assertTrue(definition.variantByHost("stone").isEmpty());
    }

    @Test
    void validationNormalizesNullTagsToEmptyList() {
        OreDefinition definition = GSON.fromJson("""
                {
                  "id": "test_ore",
                  "display_name": "Test Ore",
                  "tags": null,
                  "variants": [
                    {
                      "host": "stone",
                      "block_id": "test_ore",
                      "texture_mode": "cube_all",
                      "textures": { "all": "better_ore_geology:block/test_ore" },
                      "copy_properties_from": "minecraft:stone"
                    }
                  ]
                }
                """, OreDefinition.class);

        definition.validate();

        assertTrue(definition.tags().isEmpty());
    }

    @Test
    void textureModeParsingIsCaseInsensitive() {
        assertEquals(OreDefinition.TextureMode.CUBE_ALL, OreDefinition.TextureMode.fromSerialized("CUBE_ALL"));
        assertEquals(
                OreDefinition.TextureMode.CUBE_COLUMN_LIKE,
                OreDefinition.TextureMode.fromSerialized("Cube_Column_Like"));
        assertEquals(OreDefinition.TextureMode.CUBE_SIDED, OreDefinition.TextureMode.fromSerialized("Cube_Sided"));
    }

    @Test
    void textureModeRejectsUnsupportedValues() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> OreDefinition.TextureMode.fromSerialized("unsupported"));

        assertFalse(error.getMessage().isBlank());
    }
}
