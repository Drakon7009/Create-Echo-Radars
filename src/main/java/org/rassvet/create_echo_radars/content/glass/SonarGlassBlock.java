package org.rassvet.create_echo_radars.content.glass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class SonarGlassBlock extends TransparentBlock implements EntityBlock, SonarGlass {
    public SonarGlassBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /**
     * Allows transparent Copycats shapes to discard their internal face when
     * it is fully covered by a native block of the same sonar-glass material.
     * Without external face hiding the decorative white filter remains visible
     * through the neighboring glass and makes the Copycat look disconnected.
     */
    @Override
    public boolean supportsExternalFaceHiding(BlockState state) {
        return true;
    }

    @Override
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos,
                                     BlockState state,
                                     BlockState neighborState,
                                     Direction direction) {
        return state.getBlock() == neighborState.getBlock();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SonarGlassBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (type != CreateEchoRadars.SONAR_GLASS_BLOCK_ENTITY.get()) return null;
        return (tickerLevel, pos, tickerState, blockEntity) ->
                SonarGlassBlockEntity.tick(tickerLevel, pos, tickerState,
                        (SonarGlassBlockEntity) blockEntity);
    }
}
