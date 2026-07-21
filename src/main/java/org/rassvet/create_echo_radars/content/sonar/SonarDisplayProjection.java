package org.rassvet.create_echo_radars.content.sonar;

/**
 * Shared projection used by the monitor renderer and its mouse hit testing.
 * Coordinates are normalized to the monitor display. Horizontal and radial
 * axes independently fill 90% of the available width and height.
 */
public final class SonarDisplayProjection {
    private static final float ECHO_CELL_FILL_WITH_GAPS = 0.42f;
    private static final float ECHO_CELL_FILL_SOLID = 0.50f;
    private static final double DISPLAY_FILL = 0.90;

    private SonarDisplayProjection() {}

    public static Point project(double normalizedDistance, double bearingRadians, float sectorDegrees) {
        double halfAngle = Math.toRadians(Math.max(1, Math.min(179, sectorDegrees)) / 2.0);
        double distance = clamp(normalizedDistance, 0, 1);
        double bearing = clamp(bearingRadians, -halfAngle, halfAngle);
        double x = Math.sin(bearing) / Math.max(0.01, Math.sin(halfAngle))
                * distance * DISPLAY_FILL;
        double z = DISPLAY_FILL - Math.cos(bearing) * distance * DISPLAY_FILL * 2;
        return new Point(x, z);
    }

    public static float echoAngularHalfWidth(float sectorDegrees, int horizontalBeams) {
        return echoAngularHalfWidth(sectorDegrees, horizontalBeams, false);
    }

    public static float echoAngularHalfWidth(float sectorDegrees, int horizontalBeams, boolean pointGaps) {
        float spacing = Math.max(1, sectorDegrees) / Math.max(1, horizontalBeams - 1);
        return echoAngularHalfWidth(spacing, pointGaps);
    }

    public static float echoAngularHalfWidth(float angularResolutionDegrees, boolean pointGaps) {
        return Math.max(0.01f, angularResolutionDegrees) * echoCellFill(pointGaps);
    }

    public static float echoRangeHalfWidth(int range) {
        return echoRangeHalfWidth(range, false);
    }

    public static float echoRangeHalfWidth(int range, boolean pointGaps) {
        return echoCellFill(pointGaps) / Math.max(1, range);
    }

    public static float rangeBinCenter(int rangeBin, int range) {
        return (Math.max(0, rangeBin) + 0.5f) / Math.max(1, range);
    }

    public static Cell echoCell(int rangeBin, int displayRange, float bearingDegrees,
                                float angularResolutionDegrees, float sectorDegrees,
                                boolean pointGaps) {
        float radialHalfWidth = echoRangeHalfWidth(displayRange, pointGaps);
        float centerDistance = rangeBinCenter(rangeBin, displayRange);
        float innerDistance = Math.max(0, centerDistance - radialHalfWidth);
        float outerDistance = Math.min(1, centerDistance + radialHalfWidth);
        float angularHalfWidth = echoAngularHalfWidth(angularResolutionDegrees, pointGaps);
        double halfSector = Math.max(1, Math.min(179, sectorDegrees)) * 0.5;
        double leftBearing = Math.max(-halfSector, bearingDegrees - angularHalfWidth);
        double rightBearing = Math.min(halfSector, bearingDegrees + angularHalfWidth);
        return new Cell(
                project(innerDistance, Math.toRadians(leftBearing), sectorDegrees),
                project(innerDistance, Math.toRadians(rightBearing), sectorDegrees),
                project(outerDistance, Math.toRadians(rightBearing), sectorDegrees),
                project(outerDistance, Math.toRadians(leftBearing), sectorDegrees));
    }

    /**
     * Builds an annular cell for the 360 degree mechanical display. A fixed-size
     * square leaves increasingly large holes as its distance from the centre
     * grows; a polar cell keeps neighbouring angular and range samples joined.
     */
    public static Cell circularCell(int rangeBin, int displayRange, float bearingDegrees,
                                    float angularResolutionDegrees, boolean pointGaps) {
        float radialHalfWidth = echoRangeHalfWidth(displayRange, pointGaps);
        float centerDistance = rangeBinCenter(rangeBin, displayRange);
        float innerDistance = Math.max(0, centerDistance - radialHalfWidth);
        float outerDistance = Math.min(1, centerDistance + radialHalfWidth);
        float angularHalfWidth = echoAngularHalfWidth(angularResolutionDegrees, pointGaps);
        double leftBearing = Math.toRadians(bearingDegrees - angularHalfWidth);
        double rightBearing = Math.toRadians(bearingDegrees + angularHalfWidth);
        return new Cell(
                circularPoint(innerDistance, leftBearing),
                circularPoint(innerDistance, rightBearing),
                circularPoint(outerDistance, rightBearing),
                circularPoint(outerDistance, leftBearing));
    }

    public static Cell blockCell(int lateralBlock, int forwardBlock, int displayRange,
                                 float sectorDegrees, boolean pointGaps) {
        double halfAngle = Math.toRadians(Math.max(1, Math.min(179, sectorDegrees)) / 2.0);
        double horizontalScale = DISPLAY_FILL
                / (Math.max(1, displayRange) * Math.max(0.01, Math.sin(halfAngle)));
        double forwardScale = DISPLAY_FILL * 2 / Math.max(1, displayRange);
        double fill = pointGaps ? ECHO_CELL_FILL_WITH_GAPS * 2 : 1;
        double centerX = lateralBlock * horizontalScale;
        double centerZ = DISPLAY_FILL - forwardBlock * forwardScale;
        double halfWidth = horizontalScale * 0.5 * fill;
        double halfHeight = forwardScale * 0.5 * fill;
        return new Cell(new Point(centerX - halfWidth, centerZ + halfHeight),
                new Point(centerX + halfWidth, centerZ + halfHeight),
                new Point(centerX + halfWidth, centerZ - halfHeight),
                new Point(centerX - halfWidth, centerZ - halfHeight));
    }

    public static Cell circularBlockCell(int lateralBlock, int forwardBlock, int displayRange,
                                         boolean pointGaps) {
        double scale = 1.0 / Math.max(1, displayRange);
        double fill = pointGaps ? ECHO_CELL_FILL_WITH_GAPS * 2 : 1;
        double centerX = lateralBlock * scale;
        double centerZ = forwardBlock * scale;
        double halfSize = scale * 0.5 * fill;
        return new Cell(new Point(centerX - halfSize, centerZ + halfSize),
                new Point(centerX + halfSize, centerZ + halfSize),
                new Point(centerX + halfSize, centerZ - halfSize),
                new Point(centerX - halfSize, centerZ - halfSize));
    }

    public static Cell polarBlockCell(int lateralBlock, int forwardBlock, int displayRange,
                                      float sectorDegrees, boolean pointGaps) {
        PolarBlockBounds bounds = polarBlockBounds(lateralBlock, forwardBlock, displayRange,
                pointGaps);
        return new Cell(
                project(bounds.innerDistance(), bounds.leftBearing(), sectorDegrees),
                project(bounds.innerDistance(), bounds.rightBearing(), sectorDegrees),
                project(bounds.outerDistance(), bounds.rightBearing(), sectorDegrees),
                project(bounds.outerDistance(), bounds.leftBearing(), sectorDegrees));
    }

    public static Cell circularPolarBlockCell(int lateralBlock, int forwardBlock, int displayRange,
                                              boolean pointGaps) {
        PolarBlockBounds bounds = polarBlockBounds(lateralBlock, forwardBlock, displayRange,
                pointGaps);
        return new Cell(
                circularPoint(bounds.innerDistance(), bounds.leftBearing()),
                circularPoint(bounds.innerDistance(), bounds.rightBearing()),
                circularPoint(bounds.outerDistance(), bounds.rightBearing()),
                circularPoint(bounds.outerDistance(), bounds.leftBearing()));
    }

    private static PolarBlockBounds polarBlockBounds(int lateralBlock, int forwardBlock,
                                                      int displayRange, boolean pointGaps) {
        double centerBearing = Math.atan2(lateralBlock, forwardBlock);
        double centerDistance = Math.hypot(lateralBlock, forwardBlock);
        double minimumDistance = Double.POSITIVE_INFINITY;
        double maximumDistance = 0;
        double minimumDelta = Double.POSITIVE_INFINITY;
        double maximumDelta = Double.NEGATIVE_INFINITY;
        for (double lateralOffset : new double[] {-0.5, 0.5}) {
            for (double forwardOffset : new double[] {-0.5, 0.5}) {
                double lateral = lateralBlock + lateralOffset;
                double forward = forwardBlock + forwardOffset;
                double distance = Math.hypot(lateral, forward);
                double bearing = Math.atan2(lateral, forward);
                double delta = Math.atan2(Math.sin(bearing - centerBearing),
                        Math.cos(bearing - centerBearing));
                minimumDistance = Math.min(minimumDistance, distance);
                maximumDistance = Math.max(maximumDistance, distance);
                minimumDelta = Math.min(minimumDelta, delta);
                maximumDelta = Math.max(maximumDelta, delta);
            }
        }
        double fill = pointGaps ? ECHO_CELL_FILL_WITH_GAPS * 2 : 1;
        double innerDistance = centerDistance + (minimumDistance - centerDistance) * fill;
        double outerDistance = centerDistance + (maximumDistance - centerDistance) * fill;
        return new PolarBlockBounds(clamp(innerDistance / Math.max(1, displayRange), 0, 1),
                clamp(outerDistance / Math.max(1, displayRange), 0, 1),
                centerBearing + minimumDelta * fill, centerBearing + maximumDelta * fill);
    }

    private static Point circularPoint(double distance, double bearing) {
        double clampedDistance = clamp(distance, 0, 1);
        return new Point(Math.sin(bearing) * clampedDistance,
                Math.cos(bearing) * clampedDistance);
    }

    private static float echoCellFill(boolean pointGaps) {
        return pointGaps ? ECHO_CELL_FILL_WITH_GAPS : ECHO_CELL_FILL_SOLID;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Point(double x, double z) {}

    public record Cell(Point innerLeft, Point innerRight, Point outerRight, Point outerLeft) {}

    private record PolarBlockBounds(double innerDistance, double outerDistance,
                                    double leftBearing, double rightBearing) {}
}
