package org.rassvet.create_echo_radars.content.sonar;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.datalink.DataLinkBlockItem;
import com.happysg.radar.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.rassvet.create_echo_radars.CreateEchoRadars;

public class SonarBlock extends KineticBlock implements IBE<SonarBlockEntity> {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty UPSIDE_DOWN = BooleanProperty.create("upside_down");

    private final SonarType sonarType;
    private final MapCodec<SonarBlock> codec;

    public SonarBlock(SonarType sonarType) {
        super(Properties.of().strength(2.5f).noOcclusion());
        this.sonarType = sonarType;
        this.codec = simpleCodec(properties -> new SonarBlock(sonarType));
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(UPSIDE_DOWN, false));
    }

    public SonarType sonarType() {
        return sonarType;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, UPSIDE_DOWN);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        boolean upsideDown = SonarPlacement.isUpsideDown(
                context.getClickedFace().getStepY(), context.isSecondaryUseActive());
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(UPSIDE_DOWN, upsideDown);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SonarVoxelShapes.forSonar(sonarType, state.getValue(FACING),
                state.getValue(UPSIDE_DOWN));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SonarVoxelShapes.forSonar(sonarType, state.getValue(FACING),
                state.getValue(UPSIDE_DOWN));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SonarBlockEntity sonar) {
            player.openMenu(sonar, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, net.minecraft.world.InteractionHand hand,
                                              BlockHitResult hit) {
        if (stack.getItem() instanceof DataLinkBlockItem || AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level instanceof ServerLevel serverLevel) {
            if (!moving && level.getBlockEntity(pos) instanceof SonarBlockEntity sonar
                    && sonar.hasDataLink()) {
                BlockPos linkPos = sonar.getDataLinkPos();
                sonar.removeDataLink();
                if (linkPos != null && level.getBlockState(linkPos).is(CreateEchoRadars.SONAR_DATA_LINK.get())) {
                    level.removeBlock(linkPos, false);
                }
                popResource(level, pos, new ItemStack(ModBlocks.RADAR_LINK.asItem()));
            }
            NetworkData.get(serverLevel).onEndpointRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        if (sonarType != SonarType.MECHANICAL_IMAGING_C) return false;
        return face == (state.getValue(UPSIDE_DOWN) ? Direction.UP : Direction.DOWN);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public Class<SonarBlockEntity> getBlockEntityClass() {
        return SonarBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SonarBlockEntity> getBlockEntityType() {
        return CreateEchoRadars.SONAR_BLOCK_ENTITY.get();
    }
}
