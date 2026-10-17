package com.bettercontent.betteroregeology.block;

import com.bettercontent.betteroregeology.visual.ExposureState;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Ordinary ore mechanics with a compact, automatically persisted visual style. */
public class GeologicalOreBlock extends Block {
    private final String family;
    public GeologicalOreBlock(String family, Properties properties) {
        super(properties);
        this.family = family;
        registerDefaultState(defaultBlockState().setValue(ExposureState.STYLE, 0));
    }
    public final String family() { return family; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ExposureState.STYLE);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ExposureState.STYLE, ExposureState.placedStyle(family, context.getClickedPos()));
    }
}
