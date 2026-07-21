package org.rassvet.create_echo_radars.content.sonar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure monitor-layout calculations shared by rendering and hit testing.
 */
public final class SonarDisplayLayout {
    private static final float DISPLAY_FILL = 0.90f;
    private static final float PIXEL_FILL_WITH_GAPS = 0.84f;
    private static final float PIXEL_FILL_SOLID = 1.00f;
    private static final float MIN_PIXEL_SIDE_FRACTION = 0.30f;
    private static final float MIN_ANGULAR_RESOLUTION_FRACTION = 0.30f;
    private static final float MAX_VERTICAL_SIZE_MULTIPLIER = 4.0f;
    private static final float ANGULAR_HALF_FILL_WITH_GAPS = 0.42f;
    private static final float ANGULAR_HALF_FILL_SOLID = 0.50f;

    private SonarDisplayLayout() {}

    public static SonarMonitorDimensions resolveDimensions(int storedWidth, int storedHeight,
                                                            int fallbackSize) {
        int width = Math.max(1, storedWidth);
        int height = Math.max(1, storedHeight);
        int fallback = Math.max(1, fallbackSize);
        if (width == 1 && height == 1 && fallback > 1) {
            return new SonarMonitorDimensions(fallback, fallback);
        }
        return new SonarMonitorDimensions(width, height);
    }

    public static Area area(SonarMonitorDimensions dimensions) {
        float width = dimensions.width();
        float height = dimensions.height();
        return new Area(1 - width, 1, 1 - height, 1);
    }

    public static float echoPixelPitch(Area area, int horizontalBeams, int displayRange) {
        int horizontalIntervals = Math.max(1, horizontalBeams - 1);
        int radialIntervals = Math.max(1, displayRange);
        float horizontalStep = DISPLAY_FILL * area.width() / horizontalIntervals;
        float radialStep = DISPLAY_FILL * area.height() / radialIntervals;
        return Math.max(1.0e-4f, Math.min(horizontalStep, radialStep));
    }

    public static float echoPixelSide(float pitch, boolean pointGaps) {
        float fill = pointGaps ? PIXEL_FILL_WITH_GAPS : PIXEL_FILL_SOLID;
        return Math.max(1.0e-4f, pitch * fill);
    }

    public static CircularGeometry circularGeometry(Area area, int displayRange) {
        float size = area.minSize();
        float radius = size * 0.43f;
        float minimumHalfSize = size / 512f;
        float maximumHalfSize = size / 96f;
        float radialHalfSize = radius * 0.48f / Math.max(1, displayRange);
        float pixelHalfSize = Math.max(minimumHalfSize,
                Math.min(maximumHalfSize, radialHalfSize));
        float pixelCenterRadius = Math.max(0,
                radius - pixelHalfSize * 1.414214f);
        return new CircularGeometry(radius, pixelHalfSize, pixelCenterRadius);
    }

    public static int compositeDisplayRange(Iterable<SonarReturn> returns, int physicalRange,
                                            int fallbackRange) {
        int safePhysicalRange = Math.max(1, physicalRange);
        int maximum = 0;
        for (SonarReturn sonarReturn : returns) {
            int distance = Math.max(sonarReturn.rangeBin() + 1,
                    (int) Math.ceil(sonarReturn.normalizedDistance() * safePhysicalRange));
            maximum = Math.max(maximum, distance);
        }
        if (maximum == 0) return Math.max(1, Math.min(safePhysicalRange, fallbackRange));
        return Math.max(1, Math.min(safePhysicalRange, maximum));
    }

    public static float[] dynamicPixelSides(List<PixelPoint> points, float maximumSide,
                                            boolean pointGaps) {
        float safeMaximum = Math.max(1.0e-4f, maximumSide);
        float neighborFill = pointGaps ? PIXEL_FILL_WITH_GAPS : PIXEL_FILL_SOLID;
        float minimumSide = safeMaximum * MIN_PIXEL_SIDE_FRACTION;
        float[] sides = new float[points.size()];
        Map<Long, List<Integer>> buckets = new HashMap<>();
        for (int index = 0; index < points.size(); index++) {
            PixelPoint point = points.get(index);
            int cellX = cellCoordinate(point.x(), safeMaximum);
            int cellZ = cellCoordinate(point.z(), safeMaximum);
            buckets.computeIfAbsent(bucketKey(cellX, cellZ), ignored -> new ArrayList<>()).add(index);
        }

        for (int index = 0; index < points.size(); index++) {
            PixelPoint point = points.get(index);
            int cellX = cellCoordinate(point.x(), safeMaximum);
            int cellZ = cellCoordinate(point.z(), safeMaximum);
            float nearest = safeMaximum;
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    List<Integer> candidates = buckets.get(bucketKey(cellX + offsetX, cellZ + offsetZ));
                    if (candidates == null) continue;
                    for (int candidateIndex : candidates) {
                        if (candidateIndex == index) continue;
                        PixelPoint candidate = points.get(candidateIndex);
                        float distance = Math.max(Math.abs(point.x() - candidate.x()),
                                Math.abs(point.z() - candidate.z()));
                        if (distance < minimumSide) continue;
                        nearest = Math.min(nearest, distance);
                    }
                }
            }
            sides[index] = Math.max(minimumSide,
                    Math.min(safeMaximum, nearest * neighborFill));
        }
        return sides;
    }

    public static PixelExtent[] dynamicPixelExtents(List<PixelPoint> points, float maximumWidth,
                                                    boolean pointGaps) {
        float safeMaximum = Math.max(1.0e-4f, maximumWidth);
        float[] widths = dynamicPixelSides(points, safeMaximum, pointGaps);
        PixelExtent[] extents = new PixelExtent[points.size()];
        float baseHalfHeight = safeMaximum * 0.5f;
        float minimumSeparation = safeMaximum * MIN_PIXEL_SIDE_FRACTION;
        float maximumVerticalGap = safeMaximum * MAX_VERTICAL_SIZE_MULTIPLIER;
        Map<Integer, List<Integer>> byBeam = new HashMap<>();
        for (int index = 0; index < points.size(); index++) {
            byBeam.computeIfAbsent(points.get(index).beam(), ignored -> new ArrayList<>()).add(index);
            extents[index] = new PixelExtent(widths[index], baseHalfHeight, baseHalfHeight);
        }

        for (List<Integer> beamPoints : byBeam.values()) {
            beamPoints.sort(Comparator.comparingDouble(index -> points.get(index).z()));
            for (int sortedIndex = 0; sortedIndex < beamPoints.size(); sortedIndex++) {
                int pointIndex = beamPoints.get(sortedIndex);
                float z = points.get(pointIndex).z();
                float lowerGap = distinctGap(points, beamPoints, sortedIndex, -1, z, minimumSeparation);
                float upperGap = distinctGap(points, beamPoints, sortedIndex, 1, z, minimumSeparation);
                extents[pointIndex] = new PixelExtent(widths[pointIndex],
                        verticalHalfExtent(lowerGap, baseHalfHeight, maximumVerticalGap),
                        verticalHalfExtent(upperGap, baseHalfHeight, maximumVerticalGap));
            }
        }
        return extents;
    }

    public static float[] angularResolutions(List<AngularPoint> points, float fallbackResolution) {
        float fallback = Math.max(0.01f, fallbackResolution);
        float readableMinimum = fallback * MIN_ANGULAR_RESOLUTION_FRACTION;
        float[] resolutions = new float[points.size()];
        Map<Integer, List<Integer>> byRangeBin = new HashMap<>();
        for (int index = 0; index < points.size(); index++) {
            AngularPoint point = points.get(index);
            resolutions[index] = point.resolutionDegrees() > 0
                    ? point.resolutionDegrees() : fallback;
            byRangeBin.computeIfAbsent(point.rangeBin(), ignored -> new ArrayList<>()).add(index);
        }

        for (List<Integer> row : byRangeBin.values()) {
            row.sort(Comparator.comparingDouble(index -> points.get(index).bearingDegrees()));
            for (int sortedIndex = 0; sortedIndex < row.size(); sortedIndex++) {
                int pointIndex = row.get(sortedIndex);
                float bearing = points.get(pointIndex).bearingDegrees();
                float nearest = nearestDistinctAngularGap(points, row, sortedIndex, bearing);
                if (Float.isFinite(nearest)) {
                    resolutions[pointIndex] = Math.min(resolutions[pointIndex], nearest);
                }
                if (!Float.isFinite(nearest) || nearest >= readableMinimum) {
                    resolutions[pointIndex] = Math.max(readableMinimum, resolutions[pointIndex]);
                }
            }
        }
        return resolutions;
    }

    public static AngularLayout readableAngularLayout(List<AngularPoint> points, float fallbackResolution,
                                                      boolean pointGaps) {
        return readableAngularLayout(points, fallbackResolution, pointGaps, false);
    }

    public static AngularLayout readableAngularLayout(List<AngularPoint> points, float fallbackResolution,
                                                      boolean pointGaps, boolean blockSizedPixels) {
        float fallback = Math.max(0.01f, fallbackResolution);
        float readableMinimum = fallback * MIN_ANGULAR_RESOLUTION_FRACTION;
        float[] resolutions;
        if (blockSizedPixels) {
            resolutions = new float[points.size()];
            for (int index = 0; index < resolutions.length; index++) {
                resolutions[index] = oneBlockAngularResolutionDegrees(points.get(index).rangeBin());
            }
        } else {
            resolutions = angularResolutions(points, fallback);
            for (int index = 0; index < resolutions.length; index++) {
                resolutions[index] = Math.max(readableMinimum, resolutions[index]);
            }
        }

        boolean[] visible = new boolean[points.size()];
        float halfFill = pointGaps ? ANGULAR_HALF_FILL_WITH_GAPS : ANGULAR_HALF_FILL_SOLID;
        Map<Integer, List<Integer>> byRangeBin = new HashMap<>();
        for (int index = 0; index < points.size(); index++) {
            byRangeBin.computeIfAbsent(points.get(index).rangeBin(), ignored -> new ArrayList<>()).add(index);
        }
        for (List<Integer> row : byRangeBin.values()) {
            row.sort(Comparator.<Integer>comparingDouble(index -> points.get(index).strength()).reversed()
                    .thenComparingInt(Integer::intValue));
            java.util.NavigableMap<Float, Integer> accepted = new java.util.TreeMap<>();
            for (int candidate : row) {
                float bearing = points.get(candidate).bearingDegrees();
                Map.Entry<Float, Integer> lower = accepted.floorEntry(bearing);
                Map.Entry<Float, Integer> upper = accepted.ceilingEntry(bearing);
                if (!overlapsAngularNeighbour(points, resolutions, halfFill, candidate, lower)
                        && !overlapsAngularNeighbour(points, resolutions, halfFill, candidate, upper)) {
                    visible[candidate] = true;
                    accepted.put(bearing, candidate);
                }
            }
        }
        float[] bearings = new float[points.size()];
        for (int index = 0; index < bearings.length; index++) {
            bearings[index] = points.get(index).bearingDegrees();
        }
        return new AngularLayout(resolutions, visible, bearings);
    }

    public static AngularLayout polarBlockLayout(List<AngularPoint> points, float sectorDegrees) {
        float halfSector = Math.max(0.5f, Math.min(180, sectorDegrees * 0.5f));
        float[] resolutions = new float[points.size()];
        float[] bearings = new float[points.size()];
        boolean[] visible = new boolean[points.size()];
        Map<Long, Integer> strongest = new HashMap<>();
        for (int index = 0; index < points.size(); index++) {
            AngularPoint point = points.get(index);
            float resolution = oneBlockAngularResolutionDegrees(point.rangeBin());
            float bearing = sectorDegrees >= 359
                    ? wrapSignedDegrees(point.bearingDegrees())
                    : Math.max(-halfSector, Math.min(halfSector, point.bearingDegrees()));
            int angularBin = (int) Math.floor((bearing + halfSector) / resolution);
            float centerBearing = -halfSector + (angularBin + 0.5f) * resolution;
            resolutions[index] = resolution;
            bearings[index] = centerBearing;
            long key = bucketKey(point.rangeBin(), angularBin);
            Integer current = strongest.get(key);
            if (current == null || point.strength() > points.get(current).strength()) {
                strongest.put(key, index);
            }
        }
        for (int index : strongest.values()) visible[index] = true;
        return new AngularLayout(resolutions, visible, bearings);
    }

    public static float oneBlockAngularResolutionDegrees(int rangeBin) {
        double centerDistance = Math.max(0, rangeBin) + 0.5;
        return (float) Math.toDegrees(2 * Math.atan(0.5 / centerDistance));
    }

    public static BlockPoint blockPoint(double distance, float bearingDegrees,
                                        float elevationDegrees) {
        double elevation = Math.toRadians(elevationDegrees);
        double horizontalDistance = Math.max(0, distance) * Math.cos(elevation);
        double bearing = Math.toRadians(bearingDegrees);
        return new BlockPoint(roundBlockCoordinate(Math.sin(bearing) * horizontalDistance),
                roundBlockCoordinate(Math.cos(bearing) * horizontalDistance));
    }

    public static BlockLayout strongestBlockPixels(List<BlockPixelPoint> points) {
        boolean[] visible = new boolean[points.size()];
        Map<Long, Integer> strongest = new HashMap<>();
        for (int index = 0; index < points.size(); index++) {
            BlockPixelPoint point = points.get(index);
            long key = bucketKey(point.lateralBlock(), point.forwardBlock());
            Integer current = strongest.get(key);
            if (current == null || point.strength() > points.get(current).strength()) {
                strongest.put(key, index);
            }
        }
        for (int index : strongest.values()) visible[index] = true;
        return new BlockLayout(visible);
    }

    private static boolean overlapsAngularNeighbour(List<AngularPoint> points, float[] resolutions,
                                                     float halfFill, int candidate,
                                                     Map.Entry<Float, Integer> neighbour) {
        if (neighbour == null) return false;
        int other = neighbour.getValue();
        float gap = Math.abs(points.get(candidate).bearingDegrees()
                - points.get(other).bearingDegrees());
        float requiredGap = halfFill * (resolutions[candidate] + resolutions[other]);
        return gap + 1.0e-4f < requiredGap;
    }

    public static float oldFrameAlpha(int lifetimeTicks, long ageTicks, boolean newScanActive,
                                      float newSweepAlpha, boolean latestCompletedFrame) {
        if (lifetimeTicks <= 0 && !latestCompletedFrame) return 0;
        if (lifetimeTicks < 0) return newScanActive ? 0 : 1;
        if (lifetimeTicks == 0) {
            return newScanActive ? 1 - clamp01(newSweepAlpha) : 1;
        }
        return clamp01(1 - Math.max(0, ageTicks) / (float) lifetimeTicks);
    }

    public static float revealProgressPerTick(int blocksPerTick, int range) {
        return Math.max(1, blocksPerTick) / (float) Math.max(1, range);
    }

    public static boolean hideOldFrameAfterFullRefresh(boolean enabled, long frameEpoch,
                                                       long fullyRefreshedEpoch) {
        return enabled && frameEpoch < fullyRefreshedEpoch;
    }

    private static float nearestDistinctAngularGap(List<AngularPoint> points, List<Integer> row,
                                                   int start, float bearing) {
        float nearest = Float.POSITIVE_INFINITY;
        for (int direction : new int[] {-1, 1}) {
            for (int index = start + direction; index >= 0 && index < row.size(); index += direction) {
                float gap = Math.abs(bearing - points.get(row.get(index)).bearingDegrees());
                if (gap <= 1.0e-4f) continue;
                nearest = Math.min(nearest, gap);
                break;
            }
        }
        return nearest;
    }

    private static float distinctGap(List<PixelPoint> points, List<Integer> sorted,
                                     int start, int direction, float z, float minimumSeparation) {
        for (int index = start + direction; index >= 0 && index < sorted.size(); index += direction) {
            float gap = Math.abs(points.get(sorted.get(index)).z() - z);
            if (gap >= minimumSeparation) return gap;
        }
        return Float.NaN;
    }

    private static float verticalHalfExtent(float gap, float fallback, float maximumGap) {
        if (Float.isNaN(gap) || gap > maximumGap) return fallback;
        return Math.max(fallback * MIN_PIXEL_SIDE_FRACTION, gap * 0.5f);
    }

    private static int cellCoordinate(float coordinate, float cellSize) {
        return (int) Math.floor(coordinate / cellSize);
    }

    private static int roundBlockCoordinate(double coordinate) {
        return (int) Math.copySign(Math.floor(Math.abs(coordinate) + 0.5), coordinate);
    }

    private static float wrapSignedDegrees(float degrees) {
        float wrapped = degrees % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }

    private static float clamp01(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private static long bucketKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public record PixelPoint(float x, float z, int beam) {
        public PixelPoint(float x, float z) {
            this(x, z, 0);
        }
    }

    public record AngularPoint(int rangeBin, float bearingDegrees, float resolutionDegrees, float strength) {
        public AngularPoint(int rangeBin, float bearingDegrees, float resolutionDegrees) {
            this(rangeBin, bearingDegrees, resolutionDegrees, 0);
        }
    }

    public record AngularLayout(float[] resolutions, boolean[] visible, float[] bearings) {}

    public record BlockPoint(int lateralBlock, int forwardBlock) {}

    public record BlockPixelPoint(int lateralBlock, int forwardBlock, float strength) {}

    public record BlockLayout(boolean[] visible) {}

    public record PixelExtent(float width, float lowerHalfHeight, float upperHalfHeight) {}

    public record CircularGeometry(float radius, float pixelHalfSize, float pixelCenterRadius) {}

    public record Area(float left, float right, float bottom, float top) {
        public float width() { return right - left; }
        public float height() { return top - bottom; }
        public float centerX() { return (left + right) * 0.5f; }
        public float centerZ() { return (bottom + top) * 0.5f; }
        public float minSize() { return Math.min(width(), height()); }
    }
}
