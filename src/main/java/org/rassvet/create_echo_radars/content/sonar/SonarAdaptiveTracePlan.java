package org.rassvet.create_echo_radars.content.sonar;

import java.util.ArrayList;
import java.util.List;

public final class SonarAdaptiveTracePlan {
    private SonarAdaptiveTracePlan() {}

    public static Settings settings(int range, int sector, int horizontalBeams, int verticalBeams,
                                    int additionalRays, int hitRefinementBacktrackBlocks) {
        return settings(range, sector, 20, horizontalBeams, verticalBeams,
                additionalRays, hitRefinementBacktrackBlocks);
    }

    public static Settings settings(int range, int sector, int verticalSector,
                                    int horizontalBeams, int verticalBeams,
                                    int additionalRays, int hitRefinementBacktrackBlocks) {
        if (additionalRays != 4 && additionalRays != 8 && additionalRays != 16) {
            throw new IllegalArgumentException("additionalRays must be 4, 8, or 16");
        }
        return new Settings(range, sector, verticalSector, horizontalBeams, verticalBeams,
                additionalRays, hitRefinementBacktrackBlocks);
    }

    public static double baseBearing(int beam, Settings settings) {
        return -settings.sector / 2.0
                + settings.sector * beam / (double) Math.max(1, settings.horizontalBeams - 1);
    }

    public static double basePitch(int vertical, Settings settings) {
        if (settings.verticalBeams == 1) return 0;
        return -settings.verticalSector / 2.0
                + settings.verticalSector
                * vertical / (double) (settings.verticalBeams - 1);
    }

    public static double bearing(Leaf leaf, Settings settings) {
        return baseBearing(leaf.beam, settings) + leaf.bearingOffset;
    }

    public static double pitch(Leaf leaf, Settings settings) {
        return basePitch(leaf.vertical, settings) + leaf.pitchOffset;
    }

    public static double nextTraceEnd(double distance, Settings settings, Leaf leaf, int blocksPerTick) {
        double limit = leaf.refinement ? leaf.refinementEndDistance : settings.range;
        return Math.min(limit, distance + blocksPerTick);
    }

    public static List<Leaf> refinementsForHit(Leaf leaf, double hitDistance, Settings settings) {
        return refinementsForHit(leaf, hitDistance, settings, 0, 0);
    }

    public static List<Leaf> sideScanRefinementsForHit(Leaf leaf, double hitDistance,
                                                       Settings settings) {
        double bearingCenter = leaf.bearingOffset < 0
                ? -SideScanGeometry.CENTER_YAW_DEGREES
                : SideScanGeometry.CENTER_YAW_DEGREES;
        return refinementsForHit(leaf, hitDistance, settings, bearingCenter,
                SideScanGeometry.CENTER_PITCH_DEGREES);
    }

    private static List<Leaf> refinementsForHit(Leaf leaf, double hitDistance, Settings settings,
                                                double bearingCenter, double pitchCenter) {
        if (leaf.refinement || settings.hitRefinementBacktrackBlocks <= 0) return List.of();
        double currentBearing = bearing(leaf, settings);
        double currentPitch = pitch(leaf, settings);
        // Each primary ray owns the angular cell halfway to its neighbours.
        // A fixed world-space offset clusters refinements around distant hits.
        double horizontalRadius = horizontalStep(settings) * 0.5;
        double verticalRadius = verticalStep(settings) * 0.5;
        double minimumBearing = bearingCenter - settings.sector / 2.0;
        double maximumBearing = bearingCenter + settings.sector / 2.0;
        double minimumPitch = pitchCenter - settings.verticalSector / 2.0;
        double maximumPitch = pitchCenter + settings.verticalSector / 2.0;
        double negativeBearing = Math.min(horizontalRadius,
                Math.max(0, currentBearing - minimumBearing));
        double positiveBearing = Math.min(horizontalRadius,
                Math.max(0, maximumBearing - currentBearing));
        double negativePitch = Math.min(verticalRadius,
                Math.max(0, currentPitch - minimumPitch));
        double positivePitch = Math.min(verticalRadius,
                Math.max(0, maximumPitch - currentPitch));

        List<Leaf> refinements = new ArrayList<>(settings.additionalRays);
        for (int i = 0; i < settings.additionalRays; i++) {
            double horizontalSample = (i + 0.5) / settings.additionalRays;
            double verticalSample = radicalInverseBase2(i + 1);
            double bearingOffset = lerp(-negativeBearing, positiveBearing, horizontalSample);
            double pitchOffset = lerp(-negativePitch, positivePitch, verticalSample);
            double angularOffset = Math.min(89, Math.hypot(bearingOffset, pitchOffset));
            double lateralSeparation = hitDistance * Math.tan(Math.toRadians(angularOffset));
            double backtrack = Math.max(settings.hitRefinementBacktrackBlocks, lateralSeparation);
            double start = Math.max(0, hitDistance - backtrack);
            double resolution = (negativeBearing + positiveBearing) / settings.additionalRays;
            refinements.add(refinementLeaf(leaf, leaf.bearingOffset + bearingOffset,
                    leaf.pitchOffset + pitchOffset, start, resolution, settings));
        }
        return List.copyOf(refinements);
    }

    public static float baseAngularResolutionDegrees(Settings settings) {
        return (float) horizontalStep(settings);
    }

    public static float baseVerticalAngularResolutionDegrees(Settings settings) {
        return (float) verticalStep(settings);
    }

    private static Leaf refinementLeaf(Leaf source, double bearingOffset, double pitchOffset,
                                       double start, double resolution, Settings settings) {
        return new Leaf(source.beam, source.vertical, bearingOffset, pitchOffset,
                true, start, settings.range, resolution);
    }

    private static double radicalInverseBase2(int value) {
        int reversed = Integer.reverse(value);
        return (reversed & 0xffffffffL) / 4294967296.0;
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static double horizontalStep(Settings settings) {
        return settings.sector / (double) Math.max(1, settings.horizontalBeams - 1);
    }

    private static double verticalStep(Settings settings) {
        if (settings.verticalBeams == 1) {
            return settings.verticalSector;
        }
        return settings.verticalSector
                / (double) (settings.verticalBeams - 1);
    }

    public record Settings(int range, int sector, int verticalSector,
                           int horizontalBeams, int verticalBeams,
                           int additionalRays, int hitRefinementBacktrackBlocks) {}

    public record Leaf(int beam, int vertical, double bearingOffset, double pitchOffset,
                       boolean refinement, double refinementStartDistance,
                       double refinementEndDistance, double angularResolutionDegrees) {
        public Leaf(int beam, int vertical, double bearingOffset, double pitchOffset) {
            this(beam, vertical, bearingOffset, pitchOffset, false, 0, 0, 0);
        }
    }
}
