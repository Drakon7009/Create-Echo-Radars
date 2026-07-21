package org.rassvet.create_echo_radars.content.sonar;

import com.happysg.radar.compat.vs2.PhysicsHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Full world-space sonar basis. The previous implementation rebuilt sonar
 * geometry from the transformed forward vector and global Y, which erased
 * Sable/VS pitch and roll. This keeps the block's local forward/right/up axes
 * together and transforms all of them into world space.
 */
public record SonarOrientation(Vec3 forward, Vec3 right, Vec3 up) {
    public SonarOrientation {
        forward = safeNormalize(forward, new Vec3(0, 0, -1));
        right = safeNormalize(right, new Vec3(1, 0, 0));
        up = safeNormalize(up, new Vec3(0, 1, 0));
    }

    public static SonarOrientation of(SonarBlockEntity sonar) {
        Direction facing = sonar.getBlockState().getValue(SonarBlock.FACING);
        Vec3 localForward = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 localRight = Vec3.atLowerCornerOf(facing.getClockWise().getNormal());
        Vec3 localUp = new Vec3(0, 1, 0);
        return new SonarOrientation(
                PhysicsHandler.getWorldVecDirectionTransform(localForward, sonar),
                PhysicsHandler.getWorldVecDirectionTransform(localRight, sonar),
                PhysicsHandler.getWorldVecDirectionTransform(localUp, sonar));
    }

    public static SonarOrientation flatFromForward(Vec3 forward) {
        Vec3 flatForward = new Vec3(forward.x, 0, forward.z);
        if (flatForward.lengthSqr() < 1.0e-8) flatForward = new Vec3(0, 0, -1);
        flatForward = flatForward.normalize();
        Vec3 right = new Vec3(-flatForward.z, 0, flatForward.x);
        return new SonarOrientation(flatForward, right, new Vec3(0, 1, 0));
    }

    public Vec3 direction(double yawDegrees, double pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        return forward.scale(Math.cos(yaw) * horizontal)
                .add(right.scale(Math.sin(yaw) * horizontal))
                .add(up.scale(Math.sin(pitch)))
                .normalize();
    }

    private static Vec3 safeNormalize(Vec3 vec, Vec3 fallback) {
        return vec.lengthSqr() < 1.0e-8 ? fallback : vec.normalize();
    }
}
