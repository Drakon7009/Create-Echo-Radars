package org.rassvet.create_echo_radars.content.glass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.Set;

public final class SonarGlassNetwork {
    public static final int MAX_BLOCKS = 4096;

    private SonarGlassNetwork() {}

    public static Component find(Level level, BlockPos start) {
        SonarGlassFloodFill.Result result = SonarGlassFloodFill.find(start.asLong(),
                packed -> SonarGlass.isGlass(level, BlockPos.of(packed)),
                packed -> Arrays.stream(Direction.values()).mapToLong(direction ->
                        BlockPos.of(packed).relative(direction).asLong()).toArray(), MAX_BLOCKS);
        return new Component(result.nodes().stream().map(BlockPos::of).collect(
                java.util.stream.Collectors.toUnmodifiableSet()), result.overflow());
    }

    public record Component(Set<BlockPos> blocks, boolean overflow) {}
}
