package com.bettercontent.betteroregeology.mixin;

import com.bettercontent.betteroregeology.visual.ExposureState;
import dev.lukebemish.excavatedvariants.impl.data.BaseOre;
import dev.lukebemish.excavatedvariants.impl.data.BaseStone;
import java.util.Arrays;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the shared property before EV constructs its state definition; other ores are untouched. */
// Reviewed upstream source: ExcavatedVariants c9b3ac5c61b1defaf7485b91828dc954c4a0dfe0 (3.0.9).
@Pseudo
@Mixin(targets = "dev.lukebemish.excavatedvariants.impl.ModifiedOreBlock", remap = false)
public abstract class ExcavatedExposureBlockMixin {
    @Shadow(remap = false) private static Property<?>[] staticProps;
    @Shadow(remap = false) protected Block target;

    @Inject(method = "setupStaticWrapper(Ldev/lukebemish/excavatedvariants/impl/data/BaseOre;Ldev/lukebemish/excavatedvariants/impl/data/BaseStone;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1, remap = false)
    private static void better_ore_geology$appendExposureProperty(BaseOre ore, BaseStone stone, CallbackInfo callback) {
        Block nativeOre = ForgeRegistries.BLOCKS.getValue(ore.blockId.get(0));
        if (nativeOre == null || ExposureState.family(nativeOre) == null) return;
        if (staticProps == null) throw new IllegalStateException("EV did not initialize native ore properties");
        if (Arrays.asList(staticProps).contains(ExposureState.STYLE)) return;
        Property<?>[] expanded = Arrays.copyOf(staticProps, staticProps.length + 1);
        expanded[staticProps.length] = ExposureState.STYLE;
        staticProps = expanded;
    }

    // Explicit development and production selectors: this optional pseudo target has no refmap.
    @Inject(method = {
            "getStateForPlacement(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;",
            "m_5573_(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"},
            at = @At("RETURN"), cancellable = true, require = 1, expect = 1, allow = 1, remap = false)
    private void better_ore_geology$placeExposure(BlockPlaceContext context, CallbackInfoReturnable<BlockState> callback) {
        String family = target == null ? null : ExposureState.family(target);
        BlockState placed = callback.getReturnValue();
        if (family != null && placed != null && placed.hasProperty(ExposureState.STYLE)) {
            callback.setReturnValue(placed.setValue(ExposureState.STYLE,
                    ExposureState.placedStyle(family, context.getClickedPos())));
        }
    }
}
