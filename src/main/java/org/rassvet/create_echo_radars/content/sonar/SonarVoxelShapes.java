package org.rassvet.create_echo_radars.content.sonar;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Deliberately simple hitboxes for sonar models: a mounting base and a housing.
 * They track every model's horizontal facing and its vertical placement without
 * turning small visual details into collision geometry.
 */
public final class SonarVoxelShapes {
    private static final VoxelShaper LOW_MOUNT = horizontal(
            Block.box(0.5, 0, 0.5, 15.5, 4, 15.5),
            Block.box(2, 4, 2, 14, 11, 14));
    private static final VoxelShaper HIGH_MOUNT = horizontal(
            Block.box(0.5, 12, 0.5, 15.5, 16, 15.5),
            Block.box(2, 5, 2, 14, 12, 14));
    private static final VoxelShaper ECHO_LOW_MOUNT = horizontal(
            Block.box(0.5, 0, 0.5, 15.5, 5, 15.5),
            Block.box(2, 4, 2, 14, 12, 14));
    private static final VoxelShaper ECHO_HIGH_MOUNT = horizontal(
            Block.box(0.5, 11, 0.5, 15.5, 16, 15.5),
            Block.box(2, 4, 2, 14, 12, 14));

    private SonarVoxelShapes() {
    }

    public static VoxelShape forSonar(SonarType type, Direction facing, boolean upsideDown) {
        boolean upperMount = hasUpperMount(type, upsideDown);
        VoxelShaper shaper = switch (type) {
            case ECHO_SOUNDER_A -> upperMount ? ECHO_HIGH_MOUNT : ECHO_LOW_MOUNT;
            case SIDE_SCAN_D, FORWARD_LOOKING_F, MECHANICAL_IMAGING_C ->
                    upperMount ? HIGH_MOUNT : LOW_MOUNT;
        };
        return shaper.get(facing);
    }

    public static boolean hasUpperMount(SonarType type, boolean upsideDown) {
        return switch (type) {
            case ECHO_SOUNDER_A, SIDE_SCAN_D -> !upsideDown;
            case FORWARD_LOOKING_F, MECHANICAL_IMAGING_C -> upsideDown;
        };
    }

    private static VoxelShaper horizontal(VoxelShape base, VoxelShape housing) {
        return VoxelShaper.forHorizontal(Shapes.or(base, housing), Direction.NORTH);
    }
}
