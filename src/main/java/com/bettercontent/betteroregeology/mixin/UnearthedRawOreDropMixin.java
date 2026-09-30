package com.bettercontent.betteroregeology.mixin;

import com.bettercontent.betteroregeology.ore.DirectRawMetalRoute;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Unearthed's Java-backed vanilla-ore drops on the authored feed routes. */
@Pseudo
@Mixin(targets = "lilypuree.unearthed.block.type.VanillaOreTypes")
public abstract class UnearthedRawOreDropMixin {
    @Inject(method = "getOreDrop", at = @At("RETURN"), cancellable = true)
    private void better_ore_geology$routeVanillaOreDrop(CallbackInfoReturnable<Item> callback) {
        Item original = callback.getReturnValue();
        ResourceLocation originalId = ForgeRegistries.ITEMS.getKey(original);
        if (originalId == null) {
            return;
        }
        ResourceLocation routedId = ResourceLocation.tryParse(DirectRawMetalRoute.outputId(originalId.toString()));
        if (routedId == null || routedId.equals(originalId)) {
            return;
        }
        Item routed = ForgeRegistries.ITEMS.getValue(routedId);
        if (routed == null) {
            throw new IllegalStateException("Missing routed Unearthed ore drop item " + routedId);
        }
        callback.setReturnValue(routed);
    }
}
