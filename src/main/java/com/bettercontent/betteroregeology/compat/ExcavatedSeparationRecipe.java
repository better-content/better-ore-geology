package com.bettercontent.betteroregeology.compat;

import com.bettercontent.betteroregeology.RealisticOresMod;
import com.bettercontent.betteroregeology.registry.ModRecipeSerializers;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

public final class ExcavatedSeparationRecipe extends CustomRecipe {
    public ExcavatedSeparationRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return singleVariant(container) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registries) {
        ExcavatedVariantSupport.Variant variant = singleVariant(container);
        if (variant == null) return ItemStack.EMPTY;
        Item chunk = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(
                RealisticOresMod.MOD_ID, "ore_chunk_" + variant.family()));
        return chunk == null ? ItemStack.EMPTY : new ItemStack(chunk);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int index = 0; index < container.getContainerSize(); index++) {
            ItemStack stack = container.getItem(index);
            ExcavatedVariantSupport.Variant variant = ExcavatedVariantSupport.identify(stack.getItem());
            if (variant != null) remaining.set(index, new ItemStack(variant.substrate()));
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.EXCAVATED_SEPARATION.get();
    }

    private static ExcavatedVariantSupport.Variant singleVariant(CraftingContainer container) {
        ExcavatedVariantSupport.Variant found = null;
        for (int index = 0; index < container.getContainerSize(); index++) {
            ItemStack stack = container.getItem(index);
            if (stack.isEmpty()) continue;
            if (found != null) return null;
            found = ExcavatedVariantSupport.identify(stack.getItem());
            if (found == null) return null;
        }
        return found;
    }
}
