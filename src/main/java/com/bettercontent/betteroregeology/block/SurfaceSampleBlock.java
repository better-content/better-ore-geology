package com.bettercontent.betteroregeology.block;

import com.bettercontent.betteroregeology.salience.DepositSurveyEpisodes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public final class SurfaceSampleBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0);
    @Nullable
    private final ResourceLocation collectedItemId;
    @Nullable
    private final String depositFamily;

    public SurfaceSampleBlock(Properties properties, @Nullable ResourceLocation collectedItemId, @Nullable String depositFamily) {
        super(properties);
        this.collectedItemId = collectedItemId;
        this.depositFamily = depositFamily;
        registerDefaultState(stateDefinition.any().setValue(WATERLOGGED, false));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos position,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult) {
        if (collectedItemId == null || !player.mayBuild()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Item collectedItem = ForgeRegistries.ITEMS.getValue(collectedItemId);
        if (collectedItem == null) {
            return InteractionResult.PASS;
        }

        BlockState replacement = state.getValue(WATERLOGGED)
                ? Blocks.WATER.defaultBlockState()
                : Blocks.AIR.defaultBlockState();
        if (!level.setBlock(position, replacement, Block.UPDATE_ALL)) {
            return InteractionResult.PASS;
        }

        // Flower-like: the indicator pops off into its collected item instead of
        // silently appearing in the player's inventory.
        popResource(level, position, new ItemStack(collectedItem));
        if (depositFamily != null && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            DepositSurveyEpisodes.sampleRead(serverPlayer, depositFamily);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        BlockPos below = position.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbor,
            LevelAccessor level,
            BlockPos position,
            BlockPos neighborPosition) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(position, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        // Flower-like: schedule a support check instead of vanishing silently, so a
        // lost support pops the indicator off into its collected item.
        if (!canSurvive(state, level, position)) {
            level.scheduleTick(position, this, 1);
        }
        return super.updateShape(state, direction, neighbor, level, position, neighborPosition);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        if (canSurvive(state, level, position)) {
            return;
        }
        BlockState replacement = state.getValue(WATERLOGGED)
                ? Blocks.WATER.defaultBlockState()
                : Blocks.AIR.defaultBlockState();
        dropResources(state, level, position);
        level.setBlock(position, replacement, Block.UPDATE_ALL);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    public boolean canPlaceLiquid(BlockGetter level, BlockPos position, BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return !state.getValue(WATERLOGGED) && fluid.is(FluidTags.WATER);
    }
}
