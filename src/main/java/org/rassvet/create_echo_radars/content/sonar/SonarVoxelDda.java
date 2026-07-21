package org.rassvet.create_echo_radars.content.sonar;

import java.util.ArrayList;
import java.util.List;

public final class SonarVoxelDda {
    private SonarVoxelDda() {}

    public static List<Cell> trace(double originX, double originY, double originZ,
                                   double directionX, double directionY, double directionZ,
                                   double startDistance, double endDistance) {
        List<Cell> cells = new ArrayList<>();
        traceCells(originX, originY, originZ, directionX, directionY, directionZ,
                startDistance, endDistance, (x, y, z, distance, incidence) -> {
                    cells.add(new Cell(x, y, z, distance, incidence));
                    return true;
                });
        return cells;
    }

    public static void traceCells(double originX, double originY, double originZ,
                                  double directionX, double directionY, double directionZ,
                                  double startDistance, double endDistance,
                                  CellVisitor visitor) {
        double start = startDistance + 1.0e-4;
        double px = originX + directionX * start;
        double py = originY + directionY * start;
        double pz = originZ + directionZ * start;
        int x = floor(px);
        int y = floor(py);
        int z = floor(pz);
        int stepX = sign(directionX);
        int stepY = sign(directionY);
        int stepZ = sign(directionZ);
        double tMaxX = nextBoundary(px, directionX, start, stepX);
        double tMaxY = nextBoundary(py, directionY, start, stepY);
        double tMaxZ = nextBoundary(pz, directionZ, start, stepZ);
        double tDeltaX = delta(directionX);
        double tDeltaY = delta(directionY);
        double tDeltaZ = delta(directionZ);
        double distance = start;
        double incidence = 1;

        while (distance <= endDistance + 1.0e-6) {
            if (!visitor.visit(x, y, z, distance, incidence)) return;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                if (tMaxX > endDistance) break;
                x += stepX;
                distance = tMaxX;
                tMaxX += tDeltaX;
                incidence = Math.abs(directionX);
            } else if (tMaxY <= tMaxZ) {
                if (tMaxY > endDistance) break;
                y += stepY;
                distance = tMaxY;
                tMaxY += tDeltaY;
                incidence = Math.abs(directionY);
            } else {
                if (tMaxZ > endDistance) break;
                z += stepZ;
                distance = tMaxZ;
                tMaxZ += tDeltaZ;
                incidence = Math.abs(directionZ);
            }
        }
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int sign(double value) {
        return value > 0 ? 1 : value < 0 ? -1 : 0;
    }

    private static double delta(double direction) {
        return direction == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / direction);
    }

    private static double nextBoundary(double coordinate, double direction, double rayDistance, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? Math.floor(coordinate) + 1 : Math.floor(coordinate);
        return rayDistance + (boundary - coordinate) / direction;
    }

    @FunctionalInterface
    public interface CellVisitor {
        /**
         * @return true to continue tracing, false to stop immediately.
         */
        boolean visit(int x, int y, int z, double distance, double incidence);
    }

    public record Cell(int x, int y, int z, double distance, double incidence) {}
}
