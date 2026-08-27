package org.rassvet.create_echo_radars.content.sonar;

import java.util.ArrayList;
import java.util.List;

/** Shared placement and beam geometry for the two side-scan emitters. */
public final class SideScanGeometry {
    public static final double EMITTER_FORWARD_OFFSET = 0.501;
    public static final double MOVEMENT_TRIGGER_DISTANCE = 0.5;
    /** Signed display bearing used to keep the two waterfall halves separate. */
    public static final double CENTER_YAW_DEGREES = 90;
    public static final double CENTER_PITCH_DEGREES = -45;
    public static final int MIN_TILT_DEGREES = -15;
    public static final int MAX_TILT_DEGREES = 15;
    /** The model's transducers are on its front/rear faces, 90 degrees from display bearing. */
    public static final double MODEL_YAW_OFFSET_DEGREES = -90;
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

    public static double physicalYaw(double displayBearing) {
        return displayBearing + MODEL_YAW_OFFSET_DEGREES;
    }

    public static double rayYaw(SonarAdaptiveTracePlan.Settings settings,
                                SonarAdaptiveTracePlan.Leaf leaf) {
        return physicalYaw(SonarAdaptiveTracePlan.bearing(leaf, settings));
    }

    public static double rayPitch(SonarAdaptiveTracePlan.Settings settings,
                                  SonarAdaptiveTracePlan.Leaf leaf, int tiltAngle) {
        return centerPitch(tiltAngle) + localPitch(settings, leaf);
    }

    public static SonarOrientation beamOrientation(SonarOrientation blockOrientation,
                                                   double displayBearing, int tiltAngle) {
        double sign = displayBearing < 0 ? -1 : 1;
        SonarOrientation outward = new SonarOrientation(
                blockOrientation.forward().scale(sign),
                blockOrientation.right().scale(sign),
                blockOrientation.up());
        PitchBasis basis = pitchBasis(tiltAngle);
        return new SonarOrientation(
                outward.forward().scale(basis.forwardOutward())
                        .add(outward.up().scale(basis.forwardUp())),
                outward.right(),
                outward.forward().scale(basis.upOutward())
                        .add(outward.up().scale(basis.upUp())));
    }

    public static net.minecraft.world.phys.Vec3 rayDirection(
            SonarOrientation blockOrientation, SonarAdaptiveTracePlan.Settings settings,
            SonarAdaptiveTracePlan.Leaf leaf, int tiltAngle) {
        double displayCenter = leaf.bearingOffset() < 0
                ? -CENTER_YAW_DEGREES : CENTER_YAW_DEGREES;
        return beamOrientation(blockOrientation, displayCenter, tiltAngle).direction(
                SonarAdaptiveTracePlan.bearing(leaf, settings) - displayCenter,
                localPitch(settings, leaf));
    }

    public static double emitterForwardOffset(SonarAdaptiveTracePlan.Leaf leaf) {
        return leaf.bearingOffset() < 0 ? -EMITTER_FORWARD_OFFSET : EMITTER_FORWARD_OFFSET;
    }

    /** Apply tilt inside each outward-facing beam instead of rotating the whole block basis. */
    public static double centerPitch(int tiltAngle) {
        return CENTER_PITCH_DEGREES + clampTilt(tiltAngle);
    }

    public static int clampTilt(int tiltAngle) {
        return Math.max(MIN_TILT_DEGREES, Math.min(MAX_TILT_DEGREES, tiltAngle));
    }

    public static PitchBasis pitchBasis(int tiltAngle) {
        double pitch = Math.toRadians(centerPitch(tiltAngle));
        double cosine = Math.cos(pitch);
        double sine = Math.sin(pitch);
        return new PitchBasis(cosine, sine, -sine, cosine);
    }

    private static double localPitch(SonarAdaptiveTracePlan.Settings settings,
                                     SonarAdaptiveTracePlan.Leaf leaf) {
        return SonarAdaptiveTracePlan.pitch(leaf, settings) - CENTER_PITCH_DEGREES;
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

    public record PitchBasis(double forwardOutward, double forwardUp,
                             double upOutward, double upUp) {}
}
