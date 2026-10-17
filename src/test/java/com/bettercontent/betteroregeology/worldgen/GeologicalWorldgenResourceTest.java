package com.bettercontent.betteroregeology.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeologicalWorldgenResourceTest {
    private static final Path DATA = Path.of("src/main/resources/data/better_ore_geology");

    @Test void resourcesUseOneDualHostConfigurationAndTwoAltitudeProfilesPerFamily() throws Exception {
        JsonObject manifest = read(Path.of("tools/geological_worldgen.json"));
        assertEquals(14, manifest.size());
        assertFalse(Files.exists(DATA.resolve("realistic_ore_generation")));
        assertEquals(manifest.keySet(), stems(DATA.resolve("worldgen/configured_feature")));
        assertEquals(33, stems(DATA.resolve("worldgen/placed_feature")).size());

        for (String family : manifest.keySet()) {
            JsonObject definition = manifest.getAsJsonObject(family);
            JsonObject configured = read(DATA.resolve("worldgen/configured_feature/" + family + ".json"));
            assertEquals("better_ore_geology:geological_deposit", configured.get("type").getAsString());
            JsonObject config = configured.getAsJsonObject("config");
            assertEquals(definition.get("morphology"), config.get("morphology"));
            var expectedTargets = definition.has("targets")
                    ? definition.getAsJsonArray("targets")
                    : null;
            int targetCount = expectedTargets == null ? 2 : expectedTargets.size();
            assertEquals(targetCount, config.getAsJsonArray("targets").size());
            if (expectedTargets == null) {
                assertEquals("minecraft:stone_ore_replaceables", targetTag(config, 0));
                assertEquals("minecraft:deepslate_ore_replaceables", targetTag(config, 1));
            } else {
                for (int index = 0; index < expectedTargets.size(); index++) {
                    JsonObject expected = expectedTargets.get(index).getAsJsonObject();
                    assertEquals(expected.get("tag").getAsString(), targetTag(config, index));
                    assertEquals(expected.get("state").getAsString(), targetState(config, index));
                }
            }
            assertEquals(definition.get("home_budget"), config.get("block_budget"));
            assertEquals(definition.get("home_spread"), config.get("budget_spread"));
            assertEquals("home", config.get("deposit_class").getAsString());

            assertProfile(family, "home", "minecraft:trapezoid", definition);
            assertProfile(family, "echo", "minecraft:uniform", definition);
            JsonObject echo = read(DATA.resolve("worldgen/placed_feature/" + family + "_echo.json"))
                    .getAsJsonObject("feature").getAsJsonObject("config");
            assertEquals(definition.get("echo_budget"), echo.get("block_budget"));
            assertEquals(definition.get("echo_spread"), echo.get("budget_spread"));
            assertEquals("echo", echo.get("deposit_class").getAsString());
            assertSupplyEnvelope(family, definition);
        }
    }

    @Test void depositsUseApprovedDimensionHostMappings() throws Exception {
        try (var modifiers = Files.list(DATA.resolve("forge/biome_modifier"))) {
            for (Path path : modifiers.filter(path -> path.getFileName().toString().startsWith("add_")).toList()) {
                String name = path.getFileName().toString();
                String biomes = read(path).get("biomes").getAsString();
                if (name.equals("add_ironstone_aether.json")) {
                    assertEquals("#better_ore_geology:aether_biomes", biomes);
                } else if (name.startsWith("add_fallout_")) {
                    assertEquals("#better_ore_geology:fallout_wasteland", biomes);
                } else if (name.equals("add_twilight_gold_home.json")
                        || name.equals("add_twilight_gold_echo.json")
                        || name.equals("add_tin_quartz_twilight.json")
                        || name.equals("add_coal_measures_twilight.json")
                        || name.equals("add_ironstone_twilight.json")
                        || name.equals("add_copper_bloom_twilight.json")) {
                    assertEquals("#better_ore_geology:twilight_biomes", biomes);
                } else {
                    assertEquals("#minecraft:is_overworld", biomes, path.toString());
                }
            }
        }

        JsonObject aether = read(DATA.resolve("worldgen/placed_feature/ironstone_aether.json"));
        JsonObject config = aether.getAsJsonObject("feature").getAsJsonObject("config");
        assertEquals("better_ore_geology:geological_deposit", aether.getAsJsonObject("feature").get("type").getAsString());
        assertEquals("better_ore_geology:aether_holystone", targetTag(config, 0));
        assertEquals("better_ore_geology:ironstone", config.getAsJsonArray("targets").get(0).getAsJsonObject()
                .getAsJsonObject("state").get("Name").getAsString());
        assertEquals("lenticular_oolitic_bed", config.get("morphology").getAsString());
        assertEquals("echo", config.get("deposit_class").getAsString());

        JsonObject twilight = read(DATA.resolve("worldgen/placed_feature/tin_quartz_twilight.json"));
        JsonObject twilightConfig = twilight.getAsJsonObject("feature").getAsJsonObject("config");
        assertEquals("better_ore_geology:geological_deposit", twilight.getAsJsonObject("feature").get("type").getAsString());
        assertEquals("minecraft:stone_ore_replaceables", targetTag(twilightConfig, 0));
        assertEquals("better_ore_geology:tin_quartz", twilightConfig.getAsJsonArray("targets").get(0).getAsJsonObject()
                .getAsJsonObject("state").get("Name").getAsString());
        assertEquals("steep_quartz_lode", twilightConfig.get("morphology").getAsString());
        assertEquals("echo", twilightConfig.get("deposit_class").getAsString());

        assertDimensionDeposit("coal_measures_twilight", "coal_measures", "broken_stratiform_seam");
        assertDimensionDeposit("ironstone_twilight", "ironstone", "lenticular_oolitic_bed");
        assertDimensionDeposit("copper_bloom_twilight", "copper_bloom", "branching_stockwork");

        JsonObject twilightGold = read(DATA.resolve("worldgen/configured_feature/twilight_gold.json"));
        JsonObject goldConfig = twilightGold.getAsJsonObject("config");
        assertEquals("minecraft:gold_ore", targetState(goldConfig, 0));
        assertEquals("minecraft:stone_ore_replaceables", targetTag(goldConfig, 0));
        assertEquals("lenticular_oolitic_bed", goldConfig.get("morphology").getAsString());

        JsonObject vanillaDisabled = read(DATA.resolve("disabled_placed_features/vanilla.json"));
        var disabledResources = vanillaDisabled.getAsJsonArray("features");
        Set<String> disabledFeatures = new HashSet<>();
        disabledResources.forEach(feature -> disabledFeatures.add(feature.getAsString()));
        assertTrue(disabledFeatures.containsAll(Set.of(
                "natures_spirit:ores/ore_coal_upper",
                "natures_spirit:ores/ore_coal_lower",
                "natures_spirit:ores/ore_iron_upper",
                "natures_spirit:ores/ore_iron_middle",
                "natures_spirit:ores/ore_iron_small",
                "natures_spirit:ores/ore_copper",
                "natures_spirit:ores/ore_copper_large",
                "natures_spirit:ores/ore_gold_extra",
                "natures_spirit:ores/ore_gold",
                "natures_spirit:ores/ore_gold_lower",
                "natures_spirit:ores/ore_redstone",
                "natures_spirit:ores/ore_redstone_extra",
                "natures_spirit:ores/ore_redstone_lower",
                "natures_spirit:ores/ore_lapis",
                "natures_spirit:ores/ore_lapis_buried",
                "natures_spirit:ores/ore_emerald",
                "natures_spirit:ores/ore_diamond",
                "natures_spirit:ores/ore_diamond_large",
                "natures_spirit:ores/ore_diamond_buried")),
                "Nature's Spirit must not restore Overworld ore families replaced by the geology policy");
        assertEquals(disabledResources.size(), disabledFeatures.size(), "disabled feature IDs must be unique");

        JsonObject falloutBiomes = read(DATA.resolve("tags/worldgen/biome/fallout_wasteland.json"));
        assertEquals(Set.of(
                "fallout_wastelands_:wasteland_badlands", "fallout_wastelands_:wasteland_city",
                "fallout_wastelands_:wasteland_dryland", "fallout_wastelands_:wasteland_plains",
                "fallout_wastelands_:wasteland_sea"),
                falloutBiomes.getAsJsonArray("values").asList().stream()
                        .map(value -> value.getAsJsonObject().get("id").getAsString()).collect(Collectors.toSet()));
        falloutBiomes.getAsJsonArray("values").forEach(value ->
                assertFalse(value.getAsJsonObject().get("required").getAsBoolean()));
        JsonObject falloutHost = read(DATA.resolve("tags/blocks/fallout_wasteland_stone.json"));
        assertEquals("fallout_wastelands_:wasteland_stone",
                falloutHost.getAsJsonArray("values").get(0).getAsJsonObject().get("id").getAsString());
        assertFalse(falloutHost.getAsJsonArray("values").get(0).getAsJsonObject().get("required").getAsBoolean());

        JsonObject falloutDisabled = read(DATA.resolve("disabled_placed_features/fallout_wasteland.json"));
        assertEquals("#better_ore_geology:fallout_wasteland", falloutDisabled.get("biomes").getAsString());
        Set<String> falloutDisabledIds = new HashSet<>();
        falloutDisabled.getAsJsonArray("features").forEach(feature -> falloutDisabledIds.add(feature.getAsString()));
        assertEquals(falloutDisabled.getAsJsonArray("features").size(), falloutDisabledIds.size());
        assertTrue(falloutDisabledIds.containsAll(Set.of(
                "fallout_wastelands_:wastelandcoalore", "fallout_wastelands_:wastelandironore",
                "fallout_wastelands_:wastelandcopperore", "fallout_wastelands_:wasteland_goldore",
                "fallout_wastelands_:tin_ore", "fallout_wastelands_:wastelanddiamondore",
                "fallout_wastelands_:wastelandemeraldore", "minecraft:ore_coal_upper",
                "minecraft:ore_coal_lower", "minecraft:ore_iron_upper", "minecraft:ore_iron_middle",
                "minecraft:ore_iron_small", "minecraft:ore_gold", "minecraft:ore_gold_lower",
                "minecraft:ore_copper")), "only overlapping vanilla/custom routes are retired in the Wasteland");
        for (String family : Set.of("fallout_coal_measures", "fallout_ironstone", "fallout_copper_bloom",
                "fallout_gold", "fallout_tin_quartz")) {
            JsonObject deposit = read(DATA.resolve("worldgen/configured_feature/" + family + ".json"))
                    .getAsJsonObject("config");
            assertEquals("better_ore_geology:fallout_wasteland_stone", targetTag(deposit, 0));
            assertEquals("#better_ore_geology:fallout_wasteland",
                    read(DATA.resolve("forge/biome_modifier/add_" + family + "_home.json"))
                            .get("biomes").getAsString());
        }
        assertFalse(falloutDisabledIds.contains("fallout_wastelands_:lead_ore"));
        assertFalse(falloutDisabledIds.contains("fallout_wastelands_:uranium_ore"));
        assertFalse(falloutDisabledIds.contains("fallout_wastelands_:wasteland_aluminum_ore"));

        JsonObject goldDisabled = read(DATA.resolve("disabled_placed_features/twilight_gold.json"));
        assertEquals("#better_ore_geology:twilight_biomes", goldDisabled.get("biomes").getAsString());
        assertEquals("twilightforest:legacy_gold_ore",
                goldDisabled.getAsJsonArray("features").get(0).getAsString());
    }

    @Test void optionalDimensionBiomeWrappersContainOnlyTheOptionalProviderTag() {
        for (var entry : java.util.Map.of(
                "aether_biomes", "#aether:is_aether",
                "twilight_biomes", "#twilightforest:in_twilight_forest").entrySet()) {
            JsonObject wrapper = read(DATA.resolve("tags/worldgen/biome/" + entry.getKey() + ".json"));
            assertFalse(wrapper.get("replace").getAsBoolean());
            var values = wrapper.getAsJsonArray("values");
            assertEquals(1, values.size(), "Wrapper must preserve exactly the provider's membership");
            JsonObject reference = values.get(0).getAsJsonObject();
            assertEquals(entry.getValue(), reference.get("id").getAsString());
            assertFalse(reference.get("required").getAsBoolean(),
                    "Absent provider must leave a bound empty wrapper instead of an undefined selector");
        }
    }

    private static void assertDimensionDeposit(String feature, String family, String morphology) {
        JsonObject config = read(DATA.resolve("worldgen/placed_feature/" + feature + ".json"))
                .getAsJsonObject("feature").getAsJsonObject("config");
        assertEquals("minecraft:stone_ore_replaceables", targetTag(config, 0));
        assertEquals("better_ore_geology:" + family, config.getAsJsonArray("targets").get(0).getAsJsonObject()
                .getAsJsonObject("state").get("Name").getAsString());
        assertEquals(morphology, config.get("morphology").getAsString());
        assertEquals("echo", config.get("deposit_class").getAsString());
    }

    private static void assertProfile(String family, String profile, String distribution, JsonObject definition) {
        JsonObject placed = read(DATA.resolve("worldgen/placed_feature/" + family + "_" + profile + ".json"));
        var placement = placed.getAsJsonArray("placement");
        JsonObject range = placement.get(2).getAsJsonObject().getAsJsonObject("height");
        assertEquals(distribution, range.get("type").getAsString(), family + " " + profile);
        var band = definition.getAsJsonArray(profile);
        assertEquals(band.get(0).getAsInt(), range.getAsJsonObject("min_inclusive").get("absolute").getAsInt());
        assertEquals(band.get(1).getAsInt(), range.getAsJsonObject("max_inclusive").get("absolute").getAsInt());
        assertTrue(band.get(0).getAsInt() >= -128 && band.get(1).getAsInt() <= 512);
    }

    private static void assertSupplyEnvelope(String family, JsonObject definition) {
        double home = definition.get("home_count").getAsDouble() * definition.get("home_budget").getAsDouble();
        double echoFrequency = definition.has("echo_count")
                ? definition.get("echo_count").getAsDouble()
                : 1.0 / definition.get("echo_rarity").getAsDouble();
        double echo = echoFrequency * definition.get("echo_budget").getAsDouble();
        double baseline = definition.get("baseline_supply").getAsDouble();
        assertTrue(Math.abs((home + echo) / baseline - 1.0) <= 0.040001, family + " changed supply");
        double share = echo / (home + echo);
        if (family.equals("hotstone")) {
            assertTrue(share >= 0.04 && share <= 0.06, "hotstone echo share " + share);
        } else {
            assertTrue(share >= 0.10 && share <= 0.20, family + " echo share " + share);
        }
        assertTrue(definition.get("echo_budget").getAsInt() > definition.get("home_budget").getAsInt(),
                family + " echo bodies must be larger");
    }

    private static String targetTag(JsonObject config, int index) {
        return config.getAsJsonArray("targets").get(index).getAsJsonObject()
                .getAsJsonObject("target").get("tag").getAsString();
    }

    private static String targetState(JsonObject config, int index) {
        return config.getAsJsonArray("targets").get(index).getAsJsonObject()
                .getAsJsonObject("state").get("Name").getAsString();
    }

    private static Set<String> stems(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private static JsonObject read(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException(path.toString(), exception);
        }
    }
}
