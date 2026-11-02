package com.bettercontent.betteroregeology.compat.emi;

import com.bettercontent.betteroregeology.compat.ExcavatedVariantSupport;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;

/** Concrete displays of the loaded dynamic recipes; no duplicate gameplay recipes. */
@EmiEntrypoint
public final class GeologyEmiPlugin implements EmiPlugin {
    @Override public void register(EmiRegistry registry) {
        if (!ModList.get().isLoaded("excavated_variants")) return;
        registerHostedOres(registry);
    }

    private static void registerHostedOres(EmiRegistry registry) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        // Index-only removal: unlike disabling ingredients this preserves R/U lookup and recipes.
        registry.removeEmiStacks(stack -> stack.getId().getNamespace().equals("excavated_variants"));
        var separation = registry.getRecipeManager().byKey(id("compat/excavated_variants/separation")).orElse(null);
        var reassembly = registry.getRecipeManager().byKey(id("compat/excavated_variants/reassembly")).orElse(null);
        for (var item : ForgeRegistries.ITEMS.getValues()) {
            var variant = ExcavatedVariantSupport.identify(item);
            if (variant == null) continue;
            var itemId = ForgeRegistries.ITEMS.getKey(item);
            var grid = grid();
            String suffix = itemId.getNamespace() + "/" + itemId.getPath();
            grid.setItem(0, new ItemStack(item));
            if (separation instanceof CraftingRecipe split && split.matches(grid, level)) {
                var output = split.assemble(grid, level.registryAccess());
                var returned = split.getRemainingItems(grid).get(0);
                if (!output.isEmpty()) registry.addRecipe(new HostedRecipe(
                        List.of(EmiStack.of(grid.getItem(0)).setRemainder(EmiStack.of(returned))), EmiStack.of(output),
                        id("/emi/separation/" + suffix), split, EmiStack.of(returned)));
            }
            // Each route is independently controlled by the loaded datapack recipes.
            var chunkItem = ForgeRegistries.ITEMS.getValue(id("ore_chunk_" + variant.family()));
            if (chunkItem == null) continue;
            var chunk = new ItemStack(chunkItem);
            var rock = new ItemStack(variant.substrate());
            grid.clearContent();
            grid.setItem(0, chunk.copy());
            grid.setItem(1, rock.copy());
            if (reassembly instanceof CraftingRecipe join && join.matches(grid, level)) {
                var output = join.assemble(grid, level.registryAccess());
                if (!output.isEmpty()) registry.addRecipe(new HostedRecipe(
                        List.of(EmiStack.of(chunk), EmiStack.of(rock)), EmiStack.of(output),
                        id("/emi/reassembly/" + suffix), join, EmiStack.EMPTY));
            }
        }
    }

    private static ResourceLocation id(String path) { return new ResourceLocation("better_ore_geology", path); }
    private static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 2, 2);
    }

    private static final class HostedRecipe extends EmiCraftingRecipe {
        private final Recipe<?> backing;
        private final EmiStack rock;
        HostedRecipe(List<EmiIngredient> inputs, EmiStack output, ResourceLocation id, Recipe<?> backing, EmiStack rock) {
            super(inputs, output, id, true);
            this.backing = backing;
            this.rock = rock;
        }
        @Override public Recipe<?> getBackingRecipe() { return backing; }
        // Avoid an automatically selected circular chunk -> hosted block -> chunk recipe tree.
        @Override public boolean supportsRecipeTree() { return false; }
        @Override public List<EmiStack> getOutputs() {
            return rock.isEmpty() ? super.getOutputs() : List.of(output, rock);
        }
        @Override public int getDisplayHeight() { return super.getDisplayHeight() + (rock.isEmpty() ? 0 : 24); }
        @Override public void addWidgets(WidgetHolder widgets) {
            super.addWidgets(widgets);
            if (!rock.isEmpty()) {
                widgets.addSlot(rock, 0, super.getDisplayHeight() + 3).recipeContext(this)
                        .appendTooltip(Component.translatable("better_ore_geology.emi.returned_rock"));
                widgets.addText(Component.translatable("better_ore_geology.emi.returned_rock"), 22,
                        super.getDisplayHeight() + 8, 0x404040, false);
            }
        }
    }
}
