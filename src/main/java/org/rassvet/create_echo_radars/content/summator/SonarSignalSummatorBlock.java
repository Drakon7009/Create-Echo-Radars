package org.rassvet.create_echo_radars.content.summator;

import com.mojang.serialization.MapCodec;
import com.happysg.radar.registry.ModBlocks;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.rassvet.create_echo_radars.CreateEchoRadars;

public final class SonarSignalSummatorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final int SLOT_COUNT = 4;
    public static final int MAX_GLASS_DISTANCE = 16;
    private static final Vec3[] SLOT_CENTERS = {
            // Centers of elements 1-4 in converted/summator.bbmodel after
            // applying their 22.5 degree panel rotation.
            new Vec3(0.4375, 0.5486775, 0.5450024),
            new Vec3(0.2500, 0.5486775, 0.5450024),
            new Vec3(0.4375, 0.3611775, 0.4700024),
            new Vec3(0.2500, 0.3611775, 0.4700024)
    };
    private static final double SLOT_HALF_SIZE = 0.075;
    private static final VoxelShaper SHAPES = VoxelShaper.forHorizontal(
            buildNorthShape(), Direction.NORTH);
    private static final MapCodec<SonarSignalSummatorBlock> CODEC = simpleCodec(
            SonarSignalSummatorBlock::new);

    public SonarSignalSummatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    private static VoxelShape buildNorthShape() {
        VoxelShape shape = Shapes.or(
                // Rear casing and base plate.
                box(0, 0, 10, 16, 16, 16),
                box(0, 0, 6, 16, 3, 10),

                // Upper front details.
                box(13, 13, 9, 15, 15, 10),
                box(10, 13, 9, 12, 15, 10),
                box(1, 12, 9, 4, 15, 10),
                box(5, 12, 9, 8, 15, 10),

                // Side rails and their lower feet.
                box(0, 3, 8, 1, 11, 10),
                box(10, 3, 8, 11, 11, 10),
                box(0, 3, 6, 1, 6, 8),
                box(10, 3, 6, 11, 6, 8),
                box(1, 3, 6, 2, 4, 8),
                box(9, 3, 6, 10, 4, 8),

                // Four antenna contacts protrude slightly from the sloped panel.
                box(6, 7.74015536, 8.0601904, 8, 9.81752448, 9.37988496),
                box(3, 7.74015536, 8.0601904, 5, 9.81752448, 9.37988496),
                box(6, 4.74015536, 6.8601904, 8, 6.81752448, 8.17988496),
                box(3, 4.74015536, 6.8601904, 5, 6.81752448, 8.17988496));

        // Voxel shapes cannot rotate cuboids. Nine thin slices closely follow the
        // 22.5 degree panel from converted/summator.bbmodel without filling its
        // entire axis-aligned bounding box.
        final double minY = 2.19687824;
        final double maxY = 11.2771608;
        final double frontStartY = 2.96224512;
        final double frontStartZ = 6.6706584;
        final double backStartZ = 8.51841744;
        final double maxZ = 11.96256848;
        final double slope = Math.tan(Math.toRadians(22.5));
        final int slices = 9;
        for (int slice = 0; slice < slices; slice++) {
            double y0 = minY + (maxY - minY) * slice / slices;
            double y1 = minY + (maxY - minY) * (slice + 1) / slices;
            double z0 = Math.max(frontStartZ,
                    frontStartZ + (y0 - frontStartY) * slope);
            double z1 = Math.min(maxZ,
                    backStartZ + (y1 - minY) * slope);
            shape = Shapes.or(shape, box(1, y0, z0, 10, y1, z1));
        }
        return shape;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos)
                instanceof SonarSignalSummatorBlockEntity summator) {
            BlockPos target = SonarSignalSummatorItem.target(stack, level);
            if (target != null) summator.setGlassTarget(target);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
                                               Level level, BlockPos pos, Player player,
                                               net.minecraft.world.InteractionHand hand,
                                               BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int slot = slotAt(state, pos, hit);
        if (slot < 0 || !(level.getBlockEntity(pos)
                instanceof SonarSignalSummatorBlockEntity summator)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && summator.removeAntenna(slot)) {
            returnDataLink(level, pos, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        int slot = slotAt(state, pos, hit);
        if (slot < 0 || !(level.getBlockEntity(pos)
                instanceof SonarSignalSummatorBlockEntity summator)) return InteractionResult.PASS;
        if (!level.isClientSide && summator.removeAntenna(slot)) {
            returnDataLink(level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void returnDataLink(Level level, BlockPos pos, Player player) {
        ItemStack returned = new ItemStack(ModBlocks.RADAR_LINK.asItem());
        if (!player.addItem(returned)) {
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5,
                    pos.getY() + 0.5, pos.getZ() + 0.5, returned);
            level.addFreshEntity(entity);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && !moving
                && level.getBlockEntity(pos) instanceof SonarSignalSummatorBlockEntity summator) {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                if (summator.hasAntenna(slot)) {
                    popResource(level, pos, new ItemStack(ModBlocks.RADAR_LINK.asItem()));
                }
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SonarSignalSummatorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (type != CreateEchoRadars.SIGNAL_SUMMATOR_BLOCK_ENTITY.get()) return null;
        return (tickerLevel, pos, tickerState, blockEntity) ->
                SonarSignalSummatorBlockEntity.tick(tickerLevel,
                        (SonarSignalSummatorBlockEntity) blockEntity);
    }

    public static int slotAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        if (hit.getDirection() != facing) return -1;
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        double horizontal = switch (facing) {
            case NORTH -> local.x;
            case SOUTH -> 1.0 - local.x;
            case WEST -> 1.0 - local.z;
            case EAST -> local.z;
            default -> -1;
        };
        for (int slot = 0; slot < SLOT_CENTERS.length; slot++) {
            Vec3 center = SLOT_CENTERS[slot];
            if (Math.abs(horizontal - center.x) <= SLOT_HALF_SIZE
                    && Math.abs(local.y - center.y) <= SLOT_HALF_SIZE) {
                return slot;
            }
        }
        return -1;
    }

    public static Vec3 slotCenter(BlockState state, int slot) {
        if (slot < 0 || slot >= SLOT_CENTERS.length) {
            return new Vec3(0.5, 0.5, 0.5);
        }
        Vec3 local = SLOT_CENTERS[slot];
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case NORTH -> local;
            case EAST -> new Vec3(1.0 - local.z, local.y, local.x);
            case SOUTH -> new Vec3(1.0 - local.x, local.y, 1.0 - local.z);
            case WEST -> new Vec3(local.z, local.y, 1.0 - local.x);
            default -> new Vec3(0.5, local.y, 0.5);
        };
    }
}
