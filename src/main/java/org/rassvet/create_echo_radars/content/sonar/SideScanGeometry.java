package org.rassvet.create_echo_radars.content.sonar;

import java.util.ArrayList;
import java.util.List;

/** Shared placement and beam geometry for the two side-scan emitters. */
public final class SideScanGeometry {
    public static final double EMITTER_SIDE_OFFSET = 0.501;
    public static final double MOVEMENT_TRIGGER_DISTANCE = 0.5;
    public static final double CENTER_YAW_DEGREES = 90;
    public static final double CENTER_PITCH_DEGREES = -45;
    public static final int MAX_HORIZONTAL_BEAMS = 5;

    private SideScanGeometry() {}

    public static List<SonarAdaptiveTracePlan.Leaf> createLeaves(int horizontalBeams,
                                                                  int verticalBeams) {
        int horizontalSamples = Math.min(MAX_HORIZONTAL_BEAMS, Math.max(1, horizontalBeams));
        List<SonarAdaptiveTracePlan.Leaf> leaves =
                new ArrayList<>(horizontalSamples * verticalBeams * 2);
        for (int horizontal = 0; horizontal < horizontalSamples; horizontal++) {
            int beam = sampledBeam(horizontal, horizontalSamples, horizontalBeams);
            for (int vertical = 0; vertical < verticalBeams; vertical++) {
                leaves.add(new SonarAdaptiveTracePlan.Leaf(beam, vertical,
                        -CENTER_YAW_DEGREES, CENTER_PITCH_DEGREES));
                leaves.add(new SonarAdaptiveTracePlan.Leaf(beam, vertical,
                        CENTER_YAW_DEGREES, CENTER_PITCH_DEGREES));
            }
        }
        return List.copyOf(leaves);
    }

    public static double emitterSideOffset(SonarAdaptiveTracePlan.Leaf leaf) {
        return leaf.bearingOffset() < 0 ? -EMITTER_SIDE_OFFSET : EMITTER_SIDE_OFFSET;
    }

    public static boolean readyForNextPing(boolean movementOnly, boolean previousOriginKnown,
                                           double movementDistance) {
        return !movementOnly || !previousOriginKnown
                || movementDistance >= MOVEMENT_TRIGGER_DISTANCE;
    }

    private static int sampledBeam(int sample, int sampleCount, int beamCount) {
        if (sampleCount <= 1 || beamCount <= 1) return Math.max(0, beamCount / 2);
        return (int) Math.round(sample * (beamCount - 1) / (double) (sampleCount - 1));
    }
}
