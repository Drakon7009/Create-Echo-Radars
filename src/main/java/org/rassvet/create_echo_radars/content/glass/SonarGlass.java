package org.rassvet.create_echo_radars.content.glass;

import net.minecraft.world.level.block.state.BlockState;

public interface SonarGlass {
    static boolean isGlass(BlockState state) {
        return state.getBlock() instanceof SonarGlass;
    }
}
