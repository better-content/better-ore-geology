package com.bettercontent.betteroregeology.ore;

import java.util.List;

public final class DisabledFeaturesLoader {
    private DisabledFeaturesLoader() {
    }

    public static List<DisabledFeaturesDefinition> loadAll() {
        return OreDefinitionLoader.loadDirectory("disabled_placed_features", DisabledFeaturesDefinition.class).stream()
                .peek(DisabledFeaturesDefinition::validate)
                .toList();
    }
}
