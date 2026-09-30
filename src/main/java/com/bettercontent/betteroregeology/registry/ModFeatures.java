package com.bettercontent.betteroregeology.registry;

import com.bettercontent.betteroregeology.RealisticOresMod;
import com.bettercontent.betteroregeology.worldgen.LavaExposedOreFeature;
import com.bettercontent.betteroregeology.worldgen.GeologicalDepositFeature;
import com.bettercontent.betteroregeology.worldgen.GeologicalOreConfiguration;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, RealisticOresMod.MOD_ID);

    public static final RegistryObject<Feature<OreConfiguration>> LAVA_EXPOSED_ORE =
            FEATURES.register("lava_exposed_ore", () -> new LavaExposedOreFeature(OreConfiguration.CODEC));

    public static final RegistryObject<Feature<GeologicalOreConfiguration>> GEOLOGICAL_DEPOSIT =
            FEATURES.register("geological_deposit", () -> new GeologicalDepositFeature(GeologicalOreConfiguration.CODEC));

    private ModFeatures() {
    }

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
