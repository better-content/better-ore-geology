package com.bettercontent.betteroregeology.visual;

import com.bettercontent.betteroregeology.block.GeologicalOreBlock;
import com.bettercontent.betteroregeology.compat.ExcavatedVariantSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Shared property identity also installed on our optional Excavated Variants blocks. */
public final class ExposureState {
    public static final IntegerProperty STYLE = IntegerProperty.create("geological_style", 0, 63);
    private ExposureState() {}

    public static String family(Block block) {
        if (block instanceof GeologicalOreBlock ore) return ore.family();
        var variant = ExcavatedVariantSupport.identify(block.asItem());
        return variant == null ? null : variant.family();
    }

    public static int placedStyle(String family, BlockPos pos) {
        return ExposureField.placedStyle(family, pos.getX(), pos.getY(), pos.getZ());
    }
}
