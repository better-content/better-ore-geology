package com.bettercontent.betteroregeology.worldgen;

import com.bettercontent.betteroregeology.ore.DisabledFeaturesDefinition;
import com.bettercontent.betteroregeology.ore.DisabledFeaturesLoader;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeGenerationSettingsBuilder;

public final class FeatureRemovalHandler {
    private static final List<DisabledFeatureRule> DISABLED_RULES = DisabledFeaturesLoader.loadAll().stream()
            .filter(DisabledFeaturesDefinition::enabled)
            .map(DisabledFeatureRule::fromDefinition)
            .toList();

    private FeatureRemovalHandler() {
    }

    public static void removeDisabledFeatures(Holder<Biome> biome, BiomeGenerationSettingsBuilder generationSettings) {
        for (DisabledFeatureRule rule : DISABLED_RULES) {
            if (!rule.matchesBiome(biome)) {
                continue;
            }
            for (Decoration step : rule.steps()) {
                generationSettings.getFeatures(step).removeIf(rule::matchesFeature);
            }
        }
    }

    private static ResourceLocation parseResourceLocation(String value) {
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            throw new IllegalArgumentException("Invalid resource location " + value);
        }
        return location;
    }

    private static Decoration parseDecoration(String value) {
        try {
            return Decoration.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid generation step " + value, exception);
        }
    }

    private record DisabledFeatureRule(String biomes, Set<Decoration> steps, Set<ResourceLocation> features) {
        private static DisabledFeatureRule fromDefinition(DisabledFeaturesDefinition definition) {
            return new DisabledFeatureRule(
                    definition.biomes(),
                    definition.steps().stream().map(FeatureRemovalHandler::parseDecoration).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    definition.features().stream().map(FeatureRemovalHandler::parseResourceLocation).collect(java.util.stream.Collectors.toUnmodifiableSet())
            );
        }

        private boolean matchesBiome(Holder<Biome> biome) {
            if (biomes.startsWith("#")) {
                return biome.is(TagKey.create(Registries.BIOME, parseResourceLocation(biomes.substring(1))));
            }
            return biome.unwrapKey()
                    .map(key -> key.location().equals(parseResourceLocation(biomes)))
                    .orElse(false);
        }

        private boolean matchesFeature(Holder<PlacedFeature> featureHolder) {
            return featureHolder.unwrapKey()
                    .map(key -> features.contains(key.location()))
                    .orElse(false);
        }
    }
}
