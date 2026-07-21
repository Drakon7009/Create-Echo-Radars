package org.rassvet.create_echo_radars.content.sonar;

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
}
