package com.bettercontent.betteroregeology.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A deposit that teaches its energetic character before the player learns its assay. */
public final class HotstoneBlock extends GeologicalOreBlock {
    public HotstoneBlock(Properties properties) {
        super("hotstone", properties.lightLevel(state -> 6));
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.fireImmune()) {
            entity.hurt(level.damageSources().hotFloor(), 1.0F);
        }
        super.stepOn(level, pos, state, entity);
    }
}
