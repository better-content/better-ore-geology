package com.bettercontent.betteroregeology.compat;

import com.bettercontent.betteroregeology.RealisticOresMod;
import com.bettercontent.betteroregeology.registry.ModRecipeSerializers;
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

public final class ExcavatedReassemblyRecipe extends CustomRecipe {
    private static final String CHUNK_PREFIX = "ore_chunk_";

    public ExcavatedReassemblyRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return resolve(container) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registries) {
        ExcavatedVariantSupport.Variant variant = resolve(container);
        return variant == null ? ItemStack.EMPTY : new ItemStack(variant.hostedOre());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.EXCAVATED_REASSEMBLY.get();
    }

    private static ExcavatedVariantSupport.Variant resolve(CraftingContainer container) {
        String family = null;
        Item substrate = null;
        int occupied = 0;
        for (int index = 0; index < container.getContainerSize(); index++) {
            ItemStack stack = container.getItem(index);
            if (stack.isEmpty()) continue;
            if (++occupied > 2) return null;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && id.getNamespace().equals(RealisticOresMod.MOD_ID)
                    && id.getPath().startsWith(CHUNK_PREFIX)) {
                if (family != null) return null;
                family = id.getPath().substring(CHUNK_PREFIX.length());
            } else {
                if (substrate != null) return null;
                substrate = stack.getItem();
            }
        }
        return family == null || substrate == null ? null : ExcavatedVariantSupport.find(family, substrate);
    }
}
