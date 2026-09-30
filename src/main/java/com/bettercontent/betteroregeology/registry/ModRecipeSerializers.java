package com.bettercontent.betteroregeology.registry;

import com.bettercontent.betteroregeology.RealisticOresMod;
import com.bettercontent.betteroregeology.compat.ExcavatedReassemblyRecipe;
import com.bettercontent.betteroregeology.compat.ExcavatedSeparationRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, RealisticOresMod.MOD_ID);
    public static final RegistryObject<RecipeSerializer<ExcavatedSeparationRecipe>> EXCAVATED_SEPARATION =
            SERIALIZERS.register("excavated_separation", () -> new SimpleCraftingRecipeSerializer<>(ExcavatedSeparationRecipe::new));
    public static final RegistryObject<RecipeSerializer<ExcavatedReassemblyRecipe>> EXCAVATED_REASSEMBLY =
            SERIALIZERS.register("excavated_reassembly", () -> new SimpleCraftingRecipeSerializer<>(ExcavatedReassemblyRecipe::new));

    private ModRecipeSerializers() {
    }

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }
}
