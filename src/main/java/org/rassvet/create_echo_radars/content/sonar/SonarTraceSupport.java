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
        return segmentBounds(origin.x, origin.y, origin.z,
                direction.x, direction.y, direction.z, startDistance, endDistance);
    }

    static AABB segmentBounds(double originX, double originY, double originZ,
                              double directionX, double directionY, double directionZ,
                              double startDistance, double endDistance) {
        double startX = originX + directionX * startDistance;
        double startY = originY + directionY * startDistance;
        double startZ = originZ + directionZ * startDistance;
        double endX = originX + directionX * endDistance;
        double endY = originY + directionY * endDistance;
        double endZ = originZ + directionZ * endDistance;
        return new AABB(Math.min(startX, endX), Math.min(startY, endY), Math.min(startZ, endZ),
                Math.max(startX, endX), Math.max(startY, endY), Math.max(startZ, endZ));
    }

    static AABB expand(AABB current, AABB next) {
        if (current == null) return next;
        return current.minmax(next);
    }

    /** Conservative slab test, including parallel rays and origins inside the box. */
    static boolean segmentIntersectsBox(AABB box, double ox, double oy, double oz,
                                        double dx, double dy, double dz, double from, double to) {
        double padding = 1.0e-3;
        if (dx == 0) {
            if (ox < box.minX - padding || ox > box.maxX + padding) return false;
        } else {
            double a = (box.minX - padding - ox) / dx, b = (box.maxX + padding - ox) / dx;
            from = Math.max(from, Math.min(a,b)); to = Math.min(to, Math.max(a,b));
            if (from > to) return false;
        }
        if (dy == 0) {
            if (oy < box.minY - padding || oy > box.maxY + padding) return false;
        } else {
            double a = (box.minY - padding - oy) / dy, b = (box.maxY + padding - oy) / dy;
            from = Math.max(from, Math.min(a,b)); to = Math.min(to, Math.max(a,b));
            if (from > to) return false;
        }
        if (dz == 0) return oz >= box.minZ - padding && oz <= box.maxZ + padding;
        double a = (box.minZ - padding - oz) / dz, b = (box.maxZ + padding - oz) / dz;
        return Math.max(from, Math.min(a,b)) <= Math.min(to, Math.max(a,b));
    }

    private static double square(double value) {
        return value * value;
    }

    private static double dot(double ax, double ay, double az, double bx, double by, double bz) {
        return ax * bx + ay * by + az * bz;
    }
}
