package org.rassvet.create_echo_radars.content.sonar;

/** Exact voxel DDA with a fast path for sections proven transparent by a snapshot.
 * Axis boundary times use the same repeated additions as the voxel walker, including
 * its X/Y/Z tie order. Skipping therefore cannot move a hit across a voxel boundary.
 */
public final class SonarSectionSkippingDda {
    private SonarSectionSkippingDda() {}

    public static void trace(double ox, double oy, double oz,
                             double dx, double dy, double dz, double from, double to,
                             Visitor visitor) {
        double distance = from + 1.0e-4;
        if (distance > to + 1.0e-6) return;
        double px = ox + dx * distance, py = oy + dy * distance, pz = oz + dz * distance;
        int x = (int) Math.floor(px), y = (int) Math.floor(py), z = (int) Math.floor(pz);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int sy = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tx = boundary(px, dx, distance, x, sx);
        double ty = boundary(py, dy, distance, y, sy);
        double tz = boundary(pz, dz, distance, z, sz);
        double ax = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double ay = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double az = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        double incidence = 1;
        double exitX = 0, exitY = 0, exitZ = 0;
        int exitSectionX = Integer.MIN_VALUE, exitSectionY = Integer.MIN_VALUE, exitSectionZ = Integer.MIN_VALUE;
        int checkedX = Integer.MIN_VALUE, checkedY = Integer.MIN_VALUE, checkedZ = Integer.MIN_VALUE;
        while (true) {
            int sectionX = x >> 4, sectionY = y >> 4, sectionZ = z >> 4;
            if (sectionX != checkedX || sectionY != checkedY || sectionZ != checkedZ) {
                checkedX = sectionX;
                checkedY = sectionY;
                checkedZ = sectionZ;
                if (visitor.skipSection(sectionX, sectionY, sectionZ)) {
                    if (exitSectionX != sectionX) {
                        exitX = sectionExit(tx, ax, x, sx); exitSectionX = sectionX;
                    }
                    if (exitSectionY != sectionY) {
                        exitY = sectionExit(ty, ay, y, sy); exitSectionY = sectionY;
                    }
                    if (exitSectionZ != sectionZ) {
                        exitZ = sectionExit(tz, az, z, sz); exitSectionZ = sectionZ;
                    }
                    int axis = exitX <= exitY && exitX <= exitZ ? 0 : exitY <= exitZ ? 1 : 2;
                    double exit = axis == 0 ? exitX : axis == 1 ? exitY : exitZ;
                    if (exit > to || !Double.isFinite(exit)) return;
                    // Consume the boundary that exits the section, and only earlier
                    // axes at ties, exactly as the non-skipping DDA does.
                    while (tx <= exit) { x += sx; tx += ax; }
                    while (ty < exit || (axis >= 1 && ty == exit)) { y += sy; ty += ay; }
                    while (tz < exit || (axis == 2 && tz == exit)) { z += sz; tz += az; }
                    distance = exit;
                    incidence = Math.abs(axis == 0 ? dx : axis == 1 ? dy : dz);
                    continue;
                }
            }
            if (!visitor.visit(x, y, z, distance, incidence)) return;
            if (tx <= ty && tx <= tz) {
                if (tx > to) return;
                x += sx; distance = tx; tx += ax; incidence = Math.abs(dx);
            } else if (ty <= tz) {
                if (ty > to) return;
                y += sy; distance = ty; ty += ay; incidence = Math.abs(dy);
            } else {
                if (tz > to) return;
                z += sz; distance = tz; tz += az; incidence = Math.abs(dz);
            }
        }
    }

    private static double sectionExit(double next, double delta, int cell, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        int additional = step > 0 ? 15 - (cell & 15) : cell & 15;
        for (int i = 0; i < additional; i++) next += delta;
        return next;
    }

    private static double boundary(double point, double direction, double start, int cell, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        return start + ((step > 0 ? cell + 1.0 : cell) - point) / direction;
    }

    public interface Visitor extends SonarVoxelDda.CellVisitor {
        boolean skipSection(int x, int y, int z);
    }
}
