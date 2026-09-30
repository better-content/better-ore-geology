package com.bettercontent.betteroregeology.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

final class DisabledFeaturesDefinitionTest {
    private static final Gson GSON = new Gson();

    @Test
    void supportsAlternateSerializedFieldNames() {
        DisabledFeaturesDefinition definition = GSON.fromJson("""
                {
                  "biome_filter": "#minecraft:is_nether",
                  "generation_steps": ["underground_ores", "underground_decoration"],
                  "features": ["better_ore_geology:lead_zinc_vein_stone"]
                }
                """, DisabledFeaturesDefinition.class);

        definition.validate();

        assertEquals("#minecraft:is_nether", definition.biomes());
        assertEquals(2, definition.steps().size());
        assertEquals("better_ore_geology:lead_zinc_vein_stone", definition.features().get(0));
    }

    @Test
    void enabledDefaultsToTrueWhenFieldIsOmittedOrNull() {
        DisabledFeaturesDefinition omitted = GSON.fromJson("""
                {
                  "features": ["better_ore_geology:lead_zinc_vein_stone"]
                }
                """, DisabledFeaturesDefinition.class);
        DisabledFeaturesDefinition explicitNull = GSON.fromJson("""
                {
                  "features": ["better_ore_geology:lead_zinc_vein_stone"],
                  "enabled": null
                }
                """, DisabledFeaturesDefinition.class);

        assertTrue(omitted.enabled());
        assertTrue(explicitNull.enabled());
    }

    @Test
    void disabledFlagCanTurnEntryOff() {
        DisabledFeaturesDefinition definition = GSON.fromJson("""
                {
                  "features": ["better_ore_geology:lead_zinc_vein_stone"],
                  "enabled": false
                }
                """, DisabledFeaturesDefinition.class);

        assertFalse(definition.enabled());
    }

    @Test
    void validationRejectsMissingFeatures() {
        DisabledFeaturesDefinition definition = GSON.fromJson("""
                {
                  "features": []
                }
                """, DisabledFeaturesDefinition.class);

        assertThrows(IllegalArgumentException.class, definition::validate);
    }

    @Test
    void validationRejectsBlankBiomeFilter() {
        DisabledFeaturesDefinition definition = GSON.fromJson("""
                {
                  "biomes": " ",
                  "features": ["better_ore_geology:lead_zinc_vein_stone"]
                }
                """, DisabledFeaturesDefinition.class);

        assertThrows(IllegalArgumentException.class, definition::validate);
    }

    @Test
    void validationRejectsMissingGenerationSteps() {
        DisabledFeaturesDefinition definition = GSON.fromJson("""
                {
                  "steps": [],
                  "features": ["better_ore_geology:lead_zinc_vein_stone"]
                }
                """, DisabledFeaturesDefinition.class);

        assertThrows(IllegalArgumentException.class, definition::validate);
    }
}
