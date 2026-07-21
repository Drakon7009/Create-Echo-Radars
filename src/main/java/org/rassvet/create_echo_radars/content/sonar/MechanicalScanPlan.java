package org.rassvet.create_echo_radars.content.sonar;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Builds the narrow, sector-aligned ray plan used by a rotating mechanical sonar. */
public final class MechanicalScanPlan {
    private MechanicalScanPlan() {}

    public static List<SonarAdaptiveTracePlan.Leaf> createLeaves(
            List<Float> sampleAngles, float mechanicalAngle,
            SonarAdaptiveTracePlan.Settings settings) {
        int centerBeam = Math.max(0, settings.horizontalBeams() / 2);
        double centerBearing = SonarAdaptiveTracePlan.baseBearing(centerBeam, settings);
        List<SonarAdaptiveTracePlan.Leaf> leaves = new ArrayList<>(
                sampleAngles.size() * settings.verticalBeams());
        for (float sampleAngle : sampleAngles) {
            float signedOffset = SonarRotation.wrap(sampleAngle - mechanicalAngle);
            if (signedOffset > 180) signedOffset -= 360;
            double bearingOffset = signedOffset - centerBearing;
            for (int vertical = 0; vertical < settings.verticalBeams(); vertical++) {
                leaves.add(new SonarAdaptiveTracePlan.Leaf(centerBeam, vertical,
                        bearingOffset, 0, false, 0, 0,
                        SonarRotation.MECHANICAL_SCAN_STEP_DEGREES));
            }
        }
        return List.copyOf(leaves);
    }

    static float absoluteBearing(SonarAdaptiveTracePlan.Leaf leaf, float mechanicalAngle,
                                 SonarAdaptiveTracePlan.Settings settings) {
        return SonarRotation.wrap((float) (mechanicalAngle
                + SonarAdaptiveTracePlan.bearing(leaf, settings)));
    }

    static SonarOrientation sampleOrientation(SonarOrientation displayOrientation,
                                              float absoluteBearing, int tiltAngle) {
        SonarOrientation unTilted = new SonarOrientation(
                displayOrientation.direction(absoluteBearing, 0),
                displayOrientation.direction(absoluteBearing + 90, 0),
                displayOrientation.up());
        return SonarBlockEntity.applyTilt(unTilted, tiltAngle);
    }

    static Vec3 emitterOrigin(Vec3 rotationCenter, SonarOrientation displayOrientation,
                              float absoluteBearing) {
        Vec3 radialDirection = displayOrientation.direction(absoluteBearing, 0);
        return rotationCenter.add(radialDirection.scale(
                SonarRotation.mechanicalEmitterDistance(absoluteBearing)));
    }
}
