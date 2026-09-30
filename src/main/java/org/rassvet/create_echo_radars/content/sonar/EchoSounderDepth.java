package org.rassvet.create_echo_radars.content.sonar;

import java.util.Arrays;
import java.util.List;

/** Converts downward sonar returns into depths and an angular surface map. */
public final class EchoSounderDepth {
    private EchoSounderDepth() {}

    public static SonarDisplayLayout.Area mapArea(SonarDisplayLayout.Area display) {
        float unit = display.minSize();
        float size = Math.min(display.height() * 0.68f, display.width() - unit * 0.32f);
        float left = display.left() + unit * 0.075f;
        float bottom = display.centerZ() - size * 0.5f;
        return new SonarDisplayLayout.Area(left, left + size, bottom, bottom + size);
    }

    public static SonarDisplayLayout.Area worldMapArea(SonarDisplayLayout.Area display) {
        if (display.width() == 1 && display.height() == 1) {
            // The single monitor model has a 2..14 pixel opening on a 16 pixel face.
            return inset(display, 2f / 16f);
        }
        return mapArea(display);
    }

    public static SonarDisplayLayout.Area worldScreenArea(SonarDisplayLayout.Area display) {
        return inset(display, 2f / 16f);
    }

    private static SonarDisplayLayout.Area inset(SonarDisplayLayout.Area display, float amount) {
        return new SonarDisplayLayout.Area(display.left() + amount, display.right() - amount,
                display.bottom() + amount, display.top() - amount);
    }

    public static SonarDisplayProjection.Point angularPoint(double bearing, double elevation,
                                                            int horizontalSector, int verticalSector) {
        double x = bearing * 2 / Math.max(1, horizontalSector);
        double z = elevation * 2 / Math.max(1, verticalSector);
        if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > 1 || Math.abs(z) > 1) {
            return null;
        }
        return new SonarDisplayProjection.Point(x, z);
    }

    public static float fromFrames(List<SonarFrame> frames, int range) {
        for (int index = frames.size() - 1; index >= 0; index--) {
            SonarFrame frame = frames.get(index);
            float depth = fromReturns(frame.returns(), range);
            if (!Float.isNaN(depth)) return depth;
            if (frame.completedTick() != 0) return Float.NaN;
        }
        return Float.NaN;
    }

    static float fromReturns(List<SonarReturn> returns, int range) {
        SonarReturn closestToVertical = null;
        double bestAngle = Double.POSITIVE_INFINITY;
        for (SonarReturn sonarReturn : returns) {
            if (sonarReturn.rangeBin() >= range) continue;
            double bearing = Math.toRadians(sonarReturn.bearingDegrees());
            double elevation = Math.toRadians(sonarReturn.elevationDegrees());
            double angle = bearing * bearing + elevation * elevation;
            if (angle < bestAngle || (angle == bestAngle && closestToVertical != null
                    && sonarReturn.normalizedDistance() < closestToVertical.normalizedDistance())) {
                bestAngle = angle;
                closestToVertical = sonarReturn;
            }
        }
        if (closestToVertical == null) return Float.NaN;
        return verticalDepth(closestToVertical, range);
    }

    public static Surface surface(List<SonarFrame> frames, int range,
                                  int horizontalSector, int verticalSector, int resolution) {
        int side = Math.max(1, resolution);
        float[] depths = new float[side * side];
        float[] areas = new float[depths.length];
        float[] distances = new float[depths.length];
        Arrays.fill(depths, Float.NaN);
        int first = frames.size() - 1;
        for (int index = frames.size() - 1; index >= 0; index--) {
            if (frames.get(index).completedTick() != 0) {
                first = index;
                break;
            }
        }
        for (int index = Math.max(0, first); index < frames.size(); index++) {
            Arrays.fill(areas, Float.POSITIVE_INFINITY);
            Arrays.fill(distances, Float.POSITIVE_INFINITY);
            for (SonarReturn hit : frames.get(index).returns()) {
                if (hit.rangeBin() >= range) continue;
                float horizontalWidth = hit.angularResolutionDegrees() > 0
                        ? hit.angularResolutionDegrees() : horizontalSector;
                float verticalWidth = hit.verticalAngularResolutionDegrees() > 0
                        ? hit.verticalAngularResolutionDegrees() : verticalSector;
                float area = horizontalWidth * verticalWidth;
                int left = cell(hit.bearingDegrees() - horizontalWidth * 0.5f,
                        horizontalSector, side);
                int right = cell(hit.bearingDegrees() + horizontalWidth * 0.5f,
                        horizontalSector, side);
                int bottom = cell(hit.elevationDegrees() - verticalWidth * 0.5f,
                        verticalSector, side);
                int top = cell(hit.elevationDegrees() + verticalWidth * 0.5f,
                        verticalSector, side);
                float depth = verticalDepth(hit, range);
                float distance = hit.normalizedDistance();
                for (int y = bottom; y <= top; y++) {
                    for (int x = left; x <= right; x++) {
                        int position = y * side + x;
                        // An angular cell can contain both a near grate hit and a
                        // later refined ray that passed through a hole. Display
                        // the first surface along the ray, regardless of pixel size.
                        if (distance < distances[position] || (distance == distances[position]
                                && area < areas[position])) {
                            depths[position] = depth;
                            areas[position] = area;
                            distances[position] = distance;
                        }
                    }
                }
            }
        }
        return new Surface(side, depths);
    }

    private static int cell(float angle, int sector, int side) {
        float fraction = angle / Math.max(1, sector) + 0.5f;
        return Math.max(0, Math.min(side - 1, (int) Math.floor(fraction * side)));
    }

    private static float verticalDepth(SonarReturn hit, int range) {
        double verticalFraction = Math.cos(Math.toRadians(hit.bearingDegrees()))
                * Math.cos(Math.toRadians(hit.elevationDegrees()));
        return (float) Math.max(0, hit.normalizedDistance() * range * verticalFraction);
    }

    public record Surface(int side, float[] depths) {
        public float depthAt(int x, int y) {
            return depths[y * side + x];
        }

        public float depthAtPosition(SonarDisplayLayout.Area map, double x, double z) {
            if (x < map.left() || x > map.right() || z < map.bottom() || z > map.top()) {
                return Float.NaN;
            }
            int cellX = Math.min(side - 1, (int) ((x - map.left()) / map.width() * side));
            int cellY = Math.min(side - 1, (int) ((z - map.bottom()) / map.height() * side));
            return depthAt(cellX, cellY);
        }
    }
}
