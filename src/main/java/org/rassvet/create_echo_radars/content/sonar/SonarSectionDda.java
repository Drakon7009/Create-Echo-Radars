package org.rassvet.create_echo_radars.content.sonar;

/** Traverses the 16-block section grid without visiting every block on the ray. */
final class SonarSectionDda {
    private static final double START_EPSILON = 1.0e-4;
    private static final double END_EPSILON = 1.0e-6;
    private static final int SECTION_SIZE = 16;

    private SonarSectionDda() {}

    static void traceSections(double originX, double originY, double originZ,
                              double directionX, double directionY, double directionZ,
                              double startDistance, double endDistance,
                              SectionVisitor visitor) {
        double start = startDistance + START_EPSILON;
        double px = originX + directionX * start;
        double py = originY + directionY * start;
        double pz = originZ + directionZ * start;
        int x = sectionCoordinate(px);
        int y = sectionCoordinate(py);
        int z = sectionCoordinate(pz);
        int stepX = sign(directionX);
        int stepY = sign(directionY);
        int stepZ = sign(directionZ);
        double tMaxX = nextBoundary(px, directionX, start, x, stepX);
        double tMaxY = nextBoundary(py, directionY, start, y, stepY);
        double tMaxZ = nextBoundary(pz, directionZ, start, z, stepZ);
        double tDeltaX = delta(directionX);
        double tDeltaY = delta(directionY);
        double tDeltaZ = delta(directionZ);
        double distance = start;

        while (distance <= endDistance + END_EPSILON) {
            visitor.visit(x, y, z);
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                if (tMaxX > endDistance) break;
                x += stepX;
                distance = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                if (tMaxY > endDistance) break;
                y += stepY;
                distance = tMaxY;
                tMaxY += tDeltaY;
            } else {
                if (tMaxZ > endDistance) break;
                z += stepZ;
                distance = tMaxZ;
                tMaxZ += tDeltaZ;
            }
        }
    }

    private static int sectionCoordinate(double coordinate) {
        return (int) Math.floor(coordinate / SECTION_SIZE);
    }

    private static int sign(double value) {
        return value > 0 ? 1 : value < 0 ? -1 : 0;
    }

    private static double delta(double direction) {
        return direction == 0 ? Double.POSITIVE_INFINITY : SECTION_SIZE / Math.abs(direction);
    }

    private static double nextBoundary(double coordinate, double direction, double rayDistance,
                                       int section, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = (step > 0 ? section + 1 : section) * (double) SECTION_SIZE;
        return rayDistance + (boundary - coordinate) / direction;
    }

    @FunctionalInterface
    interface SectionVisitor {
        void visit(int sectionX, int sectionY, int sectionZ);
    }
}
