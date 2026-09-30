package com.bettercontent.betteroregeology.worldgen;

import com.mojang.serialization.Codec;
import com.bettercontent.betteroregeology.RealisticOresMod;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class DisabledFeatureBiomeModifier implements BiomeModifier {
    private static final DisabledFeatureBiomeModifier INSTANCE = new DisabledFeatureBiomeModifier();
    private static final DeferredRegister<Codec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, RealisticOresMod.MOD_ID);

    public static final RegistryObject<Codec<? extends BiomeModifier>> DISABLED_FEATURE_BIOME_MODIFIER =
            SERIALIZERS.register("disabled_feature_biome_modifier", () -> Codec.unit(INSTANCE));

    private DisabledFeatureBiomeModifier() {
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.REMOVE) {
            FeatureRemovalHandler.removeDisabledFeatures(biome, builder.getGenerationSettings());
        }
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return DISABLED_FEATURE_BIOME_MODIFIER.get();
    }
}
