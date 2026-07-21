package org.rassvet.create_echo_radars.content.sonar;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class SonarTraceSupport {
    private SonarTraceSupport() {}

    static boolean boxMayIntersectCone(AABB box, Vec3 origin, SonarOrientation orientation,
                                       int horizontalSector, int range) {
        return boxMayIntersectCone(box, origin, orientation, horizontalSector, 20, range);
    }

    static boolean boxMayIntersectCone(AABB box, Vec3 origin, SonarOrientation orientation,
                                       int horizontalSector, int verticalSector, int range) {
        return boxMayIntersectCone(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                origin.x, origin.y, origin.z,
                orientation.forward().x, orientation.forward().y, orientation.forward().z,
                orientation.right().x, orientation.right().y, orientation.right().z,
                orientation.up().x, orientation.up().y, orientation.up().z,
                horizontalSector, verticalSector, range);
    }

    static boolean boxMayIntersectCone(double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ,
                                       double originX, double originY, double originZ,
                                       double forwardX, double forwardY, double forwardZ,
                                       double rightX, double rightY, double rightZ,
                                       double upX, double upY, double upZ,
                                       int horizontalSector, int range) {
        return boxMayIntersectCone(minX, minY, minZ, maxX, maxY, maxZ,
                originX, originY, originZ, forwardX, forwardY, forwardZ,
                rightX, rightY, rightZ, upX, upY, upZ, horizontalSector, 20, range);
    }

    static boolean boxMayIntersectCone(double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ,
                                       double originX, double originY, double originZ,
                                       double forwardX, double forwardY, double forwardZ,
                                       double rightX, double rightY, double rightZ,
                                       double upX, double upY, double upZ,
                                       int horizontalSector, int verticalSector, int range) {
        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        double relativeX = centerX - originX;
        double relativeY = centerY - originY;
        double relativeZ = centerZ - originZ;
        double radius = Math.sqrt(square(maxX - minX) + square(maxY - minY) + square(maxZ - minZ)) * 0.5;
        double distance = Math.sqrt(square(relativeX) + square(relativeY) + square(relativeZ));
        if (distance - radius > range) return false;
        if (distance <= Math.max(radius, 1.0e-6)) return true;

        double angularRadius = Math.asin(Math.min(1, radius / distance));
        double forward = dot(relativeX, relativeY, relativeZ, forwardX, forwardY, forwardZ);
        double side = dot(relativeX, relativeY, relativeZ, rightX, rightY, rightZ);
        double up = dot(relativeX, relativeY, relativeZ, upX, upY, upZ);
        double horizontal = Math.sqrt(forward * forward + side * side);

        double yaw = Math.atan2(Math.abs(side), forward);
        if (yaw - angularRadius > Math.toRadians(horizontalSector / 2.0)) return false;

        if (horizontal <= 1.0e-6) return true;
        double pitch = Math.atan2(up, horizontal);
        double halfVertical = Math.toRadians(verticalSector / 2.0);
        return pitch + angularRadius >= -halfVertical && pitch - angularRadius <= halfVertical;
    }

    static boolean firstDistanceBeatsSecond(boolean firstHit, double firstDistance,
                                            boolean secondHit, double secondDistance) {
        return firstHit && (!secondHit || firstDistance < secondDistance - 1.0e-6);
    }

    static AABB segmentBounds(Vec3 origin, Vec3 direction, double startDistance, double endDistance) {
        Vec3 start = origin.add(direction.scale(startDistance));
        Vec3 end = origin.add(direction.scale(endDistance));
        return new AABB(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z),
                Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z));
    }

    static AABB expand(AABB current, AABB next) {
        if (current == null) return next;
        return current.minmax(next);
    }

    private static double square(double value) {
        return value * value;
    }

    private static double dot(double ax, double ay, double az, double bx, double by, double bz) {
        return ax * bx + ay * by + az * bz;
    }
}
