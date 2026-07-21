package org.rassvet.create_echo_radars.content.sonar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarTypeAndRotationTest {
    @Test
    void typeDefaultsMatchTheFourDisplayGeometries() {
        assertEquals(10, SonarType.ECHO_SOUNDER_A.defaultHorizontalAngle());
        assertEquals(60, SonarType.MECHANICAL_IMAGING_C.defaultVerticalAngle());
        assertEquals(3, SonarType.SIDE_SCAN_D.defaultHorizontalAngle());
        assertEquals(20, SonarType.FORWARD_LOOKING_F.defaultVerticalAngle());
    }

    @Test
    void shaftSpeedIsDirectUntilTheRadarBearingLimit() {
        assertEquals(4.8f, SonarRotation.angularSpeed(16, 256), 1.0e-5f);
        assertEquals(19.2f, SonarRotation.angularSpeed(64, 256), 1.0e-5f);
        assertEquals(19.2f, SonarRotation.angularSpeed(256, 256), 1.0e-5f);
        assertEquals(-19.2f, SonarRotation.angularSpeed(-256, 256), 1.0e-5f);
    }

    @Test
    void rotationWrapsInBothDirections() {
        assertEquals(9.2f, SonarRotation.advance(350, 19.2f), 1.0e-4f);
        assertEquals(350.8f, SonarRotation.advance(10, -19.2f), 1.0e-4f);
    }

    @Test
    void mechanicalScanUsesEverySecondDegreeAcrossWrap() {
        assertEquals(358, SonarRotation.scanStep(359.7f, 2), 1.0e-5f);
        assertEquals(0, SonarRotation.nearestScanStep(359.7f, 2), 1.0e-5f);
        assertEquals(358, SonarRotation.nearestScanStep(358.4f, 2), 1.0e-5f);
        assertEquals(java.util.List.of(0f, 2f, 4f),
                SonarRotation.crossedScanSteps(358, 4.8f, 6.8f, 2));
        assertEquals(java.util.List.of(358f, 356f),
                SonarRotation.crossedScanSteps(0, 355.9f, -4.1f, 2));
    }

    @Test
    void mechanicalScanPrefetchesInTheRotationDirection() {
        assertEquals(40, SonarRotation.prefetchAngle(0, 5, 2, 8, 120), 1.0e-5f);
        assertEquals(320, SonarRotation.prefetchAngle(0, -5, 2, 8, 120), 1.0e-5f);
        assertEquals(120, SonarRotation.prefetchAngle(0, 20, 2, 8, 120), 1.0e-5f);
        assertEquals(115.2f, SonarRotation.prefetchAngle(0, 19.2f, 2, 6, 120), 1.0e-4f);
    }

    @Test
    void mechanicalUpdateCadenceAcceleratesWithShaftSpeed() {
        assertEquals(5, SonarRotation.mechanicalUpdateIntervalTicks(0));
        assertEquals(5, SonarRotation.mechanicalUpdateIntervalTicks(4));
        assertEquals(4, SonarRotation.mechanicalUpdateIntervalTicks(4.8f));
        assertEquals(2, SonarRotation.mechanicalUpdateIntervalTicks(9.6f));
        assertEquals(1, SonarRotation.mechanicalUpdateIntervalTicks(19.2f));
        assertEquals(1, SonarRotation.mechanicalUpdateIntervalTicks(-19.2f));
    }

    @Test
    void mechanicalRayPlanUsesOneHorizontalRayPerSectorAndEveryVerticalBeam() {
        SonarAdaptiveTracePlan.Settings settings = SonarAdaptiveTracePlan.settings(
                128, 120, 60, 8, 3, 4, 0);
        java.util.List<Float> angles = java.util.List.of(358f, 0f, 2f);
        java.util.List<SonarAdaptiveTracePlan.Leaf> leaves =
                MechanicalScanPlan.createLeaves(angles, 358, settings);

        assertEquals(angles.size() * settings.verticalBeams(), leaves.size());
        for (int angleIndex = 0; angleIndex < angles.size(); angleIndex++) {
            for (int vertical = 0; vertical < settings.verticalBeams(); vertical++) {
                SonarAdaptiveTracePlan.Leaf leaf = leaves.get(
                        angleIndex * settings.verticalBeams() + vertical);
                float absoluteBearing = SonarRotation.wrap((float)
                        (SonarAdaptiveTracePlan.bearing(leaf, settings) + 358));
                assertEquals(angles.get(angleIndex), absoluteBearing, 1.0e-4f);
                assertEquals(vertical, leaf.vertical());
                assertEquals(SonarRotation.MECHANICAL_SCAN_STEP_DEGREES,
                        leaf.angularResolutionDegrees(), 1.0e-5);
            }
        }
    }

    @Test
    void prefetchedMechanicalRayKeepsTheSameWorldBearingAcrossBatches() {
        SonarAdaptiveTracePlan.Settings settings = SonarAdaptiveTracePlan.settings(
                128, 120, 60, 8, 1, 4, 0);
        SonarAdaptiveTracePlan.Leaf firstBatch = MechanicalScanPlan.createLeaves(
                java.util.List.of(90f), 0, settings).getFirst();
        SonarAdaptiveTracePlan.Leaf secondBatch = MechanicalScanPlan.createLeaves(
                java.util.List.of(90f), 40, settings).getFirst();

        float firstBearing = MechanicalScanPlan.absoluteBearing(firstBatch, 0, settings);
        float secondBearing = MechanicalScanPlan.absoluteBearing(secondBatch, 40, settings);
        assertEquals(90, firstBearing, 1.0e-5f);
        assertEquals(firstBearing, secondBearing, 1.0e-5f);
    }

    @Test
    void sweepCrossingWorksAcrossWrapAndInReverse() {
        assertTrue(SonarRotation.crossedAngle(358, 4, 0, 6));
        assertTrue(SonarRotation.crossedAngle(2, 356, 0, -6));
        org.junit.jupiter.api.Assertions.assertFalse(
                SonarRotation.crossedAngle(10, 20, 30, 10));
        org.junit.jupiter.api.Assertions.assertFalse(
                SonarRotation.crossedAngle(10, 20, 10, 10));
    }

    @Test
    void mechanicalEmitterClearsItsOwnBlockAtEveryAngle() {
        assertEquals(0.6, SonarRotation.mechanicalEmitterDistance(0), 1.0e-6);
        double diagonal = SonarRotation.mechanicalEmitterDistance(45);
        assertTrue(diagonal > 0.84);
        assertTrue(diagonal * Math.cos(Math.toRadians(45)) >= 0.599);
        assertTrue(diagonal * Math.sin(Math.toRadians(45)) >= 0.599);
        assertEquals(SonarRotation.mechanicalEmitterDistance(45),
                SonarRotation.mechanicalEmitterDistance(225), 1.0e-6);
        for (int angle = 0; angle < 360; angle++) {
            double radians = Math.toRadians(angle);
            double distance = SonarRotation.mechanicalEmitterDistance(angle);
            double x = Math.abs(Math.cos(radians) * distance);
            double z = Math.abs(Math.sin(radians) * distance);
            assertTrue(Math.max(x, z) >= 0.599,
                    "emitter did not clear its block safely at " + angle);
        }
    }

    @Test
    void mechanicalPixelsFadeInInsteadOfPoppingAsACompleteBatch() {
        assertEquals(0, SonarRotation.mechanicalPixelAlpha(0,
                0, 0, 10), 1.0e-5f);
        float duringFade = SonarRotation.mechanicalPixelAlpha(0,
                0, 0.5, 10);
        assertTrue(duringFade > 0 && duringFade < 1);
        float afterFadeIn = SonarRotation.mechanicalPixelAlpha(0,
                0, 1, 10);
        assertEquals(1, afterFadeIn, 1.0e-5f);
    }

    @Test
    void automaticMechanicalPixelsReachThirtyPercentAtTheNextSweep() {
        assertEquals(0.3f, SonarRotation.mechanicalPixelAlpha(0,
                0, 36, 10), 1.0e-5f);
        assertEquals(0.3f, SonarRotation.mechanicalPixelAlpha(0,
                0, 36, -10), 1.0e-5f);
        assertEquals(36, 360 / 10f, 1.0e-5f);
        assertTrue(SonarRotation.mechanicalAutomaticLifetimeTicks(10) > 36);
    }

    @Test
    void automaticMechanicalFadeAdaptsToRotationSpeed() {
        assertEquals(0.3f, SonarRotation.mechanicalPixelAlpha(0,
                100, 118, 20), 1.0e-5f);
        assertEquals(0.3f, SonarRotation.mechanicalPixelAlpha(0,
                100, 172, 5), 1.0e-5f);
    }

    @Test
    void manualMechanicalPixelLifetimeIsIndependentFromSweepPeriod() {
        float halfwayThroughLifetime = SonarRotation.mechanicalPixelAlpha(20,
                0, 10, 10);
        assertTrue(halfwayThroughLifetime > 0 && halfwayThroughLifetime < 1);
        assertEquals(0, SonarRotation.mechanicalPixelAlpha(20,
                0, 20, 10), 1.0e-5f);
    }

    @Test
    void configuredMechanicalPixelsContinuouslyLoseBrightnessWithAge() {
        float fresh = SonarRotation.mechanicalPixelAlpha(100, 0, 2, 5);
        float middleAged = SonarRotation.mechanicalPixelAlpha(100, 0, 50, 5);
        float old = SonarRotation.mechanicalPixelAlpha(100, 0, 90, 5);

        assertEquals(1, fresh, 1.0e-5f);
        assertTrue(fresh > middleAged);
        assertTrue(middleAged > old);
        assertTrue(old > 0);
    }

    @Test
    void automaticMechanicalPixelsRemainWhileSweepIsStopped() {
        assertEquals(1, SonarRotation.mechanicalPixelAlpha(0,
                0, 10_000, 0), 1.0e-5f);
    }

    @Test
    void mechanicalDisplayCellsMeetAtAngularAndRadialEdges() {
        SonarDisplayProjection.Cell first = SonarDisplayProjection.circularCell(
                10, 64, 20, 2, false);
        SonarDisplayProjection.Cell nextAngle = SonarDisplayProjection.circularCell(
                10, 64, 22, 2, false);
        SonarDisplayProjection.Cell nextRange = SonarDisplayProjection.circularCell(
                11, 64, 20, 2, false);

        assertEquals(first.outerRight().x(), nextAngle.outerLeft().x(), 1.0e-6);
        assertEquals(first.outerRight().z(), nextAngle.outerLeft().z(), 1.0e-6);
        assertEquals(first.outerLeft().x(), nextRange.innerLeft().x(), 1.0e-6);
        assertEquals(first.outerLeft().z(), nextRange.innerLeft().z(), 1.0e-6);
    }

    @Test
    void verticalSectorControlsBeamPitchAndResolution() {
        SonarAdaptiveTracePlan.Settings settings = SonarAdaptiveTracePlan.settings(
                128, 30, 60, 3, 3, 4, 0);
        assertEquals(-30, SonarAdaptiveTracePlan.basePitch(0, settings), 1.0e-5);
        assertEquals(0, SonarAdaptiveTracePlan.basePitch(1, settings), 1.0e-5);
        assertEquals(30, SonarAdaptiveTracePlan.basePitch(2, settings), 1.0e-5);
        assertEquals(30, SonarAdaptiveTracePlan.baseVerticalAngularResolutionDegrees(settings), 1.0e-5);
    }
}
