package org.rassvet.create_echo_radars.mixin;

import com.happysg.radar.block.datalink.DataLinkBlockItem;
import com.happysg.radar.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.rassvet.create_echo_radars.content.sonar.SonarBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DataLinkBlockItem.class, remap = false)
public abstract class DataLinkBlockItemMixin {
    @Redirect(
            method = "getFilterTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;",
                    ordinal = 1
            )
    )
    private static Block createEchoRadars$acceptSonarAsStationaryRadar(BlockState state) {
        Block block = state.getBlock();
        return block instanceof SonarBlock ? ModBlocks.STATIONARY_RADAR.get() : block;
    }
}
