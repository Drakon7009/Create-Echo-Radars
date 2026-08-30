package org.rassvet.create_echo_radars.content.sonar;

import com.happysg.radar.block.behavior.networks.NetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SonarDataLinkBlock extends Block implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty UPSIDE_DOWN = SonarBlock.UPSIDE_DOWN;

    /*
     * These shapes follow the rotations in sonar_data_link.json exactly.
     * VoxelShaper uses Create's rotation convention, whose X rotation is the
     * opposite of Minecraft's block-model convention for horizontal facings.
     */
    private static final VoxelShape BASE_UP = base(
            Block.box(1, 1, 9, 15, 4, 16),
            Block.box(2, -1, 10, 14, 1, 15));
    private static final VoxelShape BASE_DOWN = base(
            Block.box(1, 12, 0, 15, 15, 7),
            Block.box(2, 15, 1, 14, 17, 6));
    private static final VoxelShape BASE_NORTH = base(
            Block.box(1, 9, 12, 15, 16, 15),
            Block.box(2, 10, 15, 14, 15, 17));
    private static final VoxelShape BASE_NORTH_LOWER = base(
            Block.box(1, 0, 12, 15, 7, 15),
            Block.box(2, 1, 15, 14, 6, 17));
    private static final VoxelShape BASE_SOUTH = base(
            Block.box(1, 9, 1, 15, 16, 4),
            Block.box(2, 10, -1, 14, 15, 1));
    private static final VoxelShape BASE_SOUTH_LOWER = base(
            Block.box(1, 0, 1, 15, 7, 4),
            Block.box(2, 1, -1, 14, 6, 1));
    private static final VoxelShape BASE_EAST = base(
            Block.box(1, 9, 1, 4, 16, 15),
            Block.box(-1, 10, 2, 1, 15, 14));
    private static final VoxelShape BASE_EAST_LOWER = base(
            Block.box(1, 0, 1, 4, 7, 15),
            Block.box(-1, 1, 2, 1, 6, 14));
    private static final VoxelShape BASE_WEST = base(
            Block.box(12, 9, 1, 15, 16, 15),
            Block.box(15, 10, 2, 17, 15, 14));
    private static final VoxelShape BASE_WEST_LOWER = base(
            Block.box(12, 0, 1, 15, 7, 15),
            Block.box(15, 1, 2, 17, 6, 14));

    public SonarDataLinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(UPSIDE_DOWN, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, UPSIDE_DOWN, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getClickedFace())
                .setValue(UPSIDE_DOWN, false)
                .setValue(WATERLOGGED, context.getLevel().getFluidState(
                        context.getClickedPos().relative(context.getClickedFace())).is(Fluids.WATER));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        boolean upperBase = state.getValue(UPSIDE_DOWN);
        return switch (state.getValue(FACING)) {
            case DOWN -> BASE_DOWN;
            case NORTH -> upperBase ? BASE_NORTH : BASE_NORTH_LOWER;
            case SOUTH -> upperBase ? BASE_SOUTH : BASE_SOUTH_LOWER;
            case EAST -> upperBase ? BASE_EAST : BASE_EAST_LOWER;
            case WEST -> upperBase ? BASE_WEST : BASE_WEST_LOWER;
            default -> BASE_UP;
        };
    }

    private static VoxelShape base(VoxelShape housing, VoxelShape foot) {
        return Shapes.or(housing, foot);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level instanceof ServerLevel serverLevel) {
            NetworkData data = NetworkData.get(serverLevel);
            data.removeDataLinkAndCleanup(serverLevel.dimension(), pos, serverLevel);
            BlockPos sonarPos = pos.relative(state.getValue(FACING).getOpposite());
            if (level.getBlockEntity(sonarPos) instanceof SonarBlockEntity sonar
                    && sonar.ownsDataLink(pos)) {
                data.onEndpointRemoved(serverLevel, sonarPos);
                sonar.removeDataLink();
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
