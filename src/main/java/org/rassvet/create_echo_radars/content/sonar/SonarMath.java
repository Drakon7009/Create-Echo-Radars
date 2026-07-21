package org.rassvet.create_echo_radars.content.sonar;

import net.minecraft.world.phys.Vec3;

public final class SonarMath {
    private SonarMath() {}

    public static boolean insideCone(Vec3 relative, Vec3 forward, int horizontalSector, float range) {
        return insideCone(relative, SonarOrientation.flatFromForward(forward), horizontalSector, range);
    }

    public static boolean insideCone(Vec3 relative, SonarOrientation orientation,
                                     int horizontalSector, float range) {
        return insideCone(relative, orientation, horizontalSector, 20, range);
    }

    public static boolean insideCone(Vec3 relative, SonarOrientation orientation,
                                     int horizontalSector, int verticalSector, float range) {
        return SonarKinematics.insideCone(project(relative, orientation),
                horizontalSector, verticalSector, range);
    }

    public static Projection project(Vec3 relative, SonarOrientation orientation) {
        return new Projection(relative.dot(orientation.forward()),
                relative.dot(orientation.right()),
                relative.dot(orientation.up()),
                relative.length());
    }

    public static Projection projectHorizontal(Vec3 relative, SonarOrientation orientation) {
        Vec3 forward = horizontalUnit(orientation.forward());
        Vec3 right = horizontalUnit(orientation.right());
        double projectedForward = relative.dot(forward);
        double projectedSide = relative.dot(right);
        return new Projection(projectedForward, projectedSide, relative.y,
                Math.hypot(projectedForward, projectedSide));
    }

    private static Vec3 horizontalUnit(Vec3 direction) {
        Vec3 horizontal = new Vec3(direction.x, 0, direction.z);
        if (horizontal.lengthSqr() < 1.0e-8) return new Vec3(0, 0, 1);
        return horizontal.normalize();
    }

    public record Projection(double forward, double side, double up, double range) {
        public double bearingDegrees() {
            return Math.toDegrees(Math.atan2(side, forward));
        }
    }
}
