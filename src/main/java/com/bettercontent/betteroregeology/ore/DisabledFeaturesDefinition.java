package com.bettercontent.betteroregeology.ore;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class DisabledFeaturesDefinition {
    @SerializedName(value = "biomes", alternate = "biome_filter")
    private String biomes = "#minecraft:is_overworld";
    @SerializedName(value = "steps", alternate = "generation_steps")
    private List<String> steps = List.of("underground_ores");
    private List<String> features = List.of();
    private Boolean enabled = Boolean.TRUE;

    public String biomes() {
        return biomes;
    }

    public List<String> steps() {
        return steps;
    }

    public List<String> features() {
        return features;
    }

    public boolean enabled() {
        return enabled == null || enabled;
    }

    public void validate() {
        require(biomes, "biomes");
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("steps must contain at least one generation step");
        }
        if (features == null || features.isEmpty()) {
            throw new IllegalArgumentException("features must contain at least one placed feature id");
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
    }
}
