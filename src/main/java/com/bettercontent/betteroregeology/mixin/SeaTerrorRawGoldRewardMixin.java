package com.bettercontent.betteroregeology.mixin;

import com.bettercontent.betteroregeology.ore.DirectRawMetalRoute;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.objectweb.asm.Opcodes;

/** Routes the pinned Sea Terror stomach reward through the authored gold processing feed. */
@Pseudo
@Mixin(targets = "net.mcreator.borninchaosv.procedures.SeaTerrorStomachPriShchielchkiePKMProcedure")
public abstract class SeaTerrorRawGoldRewardMixin {
    @Redirect(
            method = "execute",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/item/Items;RAW_GOLD:Lnet/minecraft/world/item/Item;",
                    opcode = Opcodes.GETSTATIC))
    private static Item better_ore_geology$routeSeaTerrorGoldReward() {
        ResourceLocation output = ResourceLocation.tryParse(DirectRawMetalRoute.outputId("minecraft:raw_gold"));
        if (output == null) {
            throw new IllegalStateException("Invalid routed Sea Terror reward identifier");
        }
        Item item = ForgeRegistries.ITEMS.getValue(output);
        if (item == null) {
            throw new IllegalStateException("Missing routed Sea Terror reward item " + output);
        }
        return item;
    }
}
