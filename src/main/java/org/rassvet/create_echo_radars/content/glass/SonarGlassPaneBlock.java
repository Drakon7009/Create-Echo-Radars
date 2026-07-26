package org.rassvet.create_echo_radars.content.glass;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class SonarGlassPaneBlock extends IronBarsBlock implements EntityBlock, SonarGlass {
    public SonarGlassPaneBlock(BlockBehaviour.Properties properties) {
        super(properties);
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
