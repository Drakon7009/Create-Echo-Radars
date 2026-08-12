package org.rassvet.create_echo_radars.compat.cbcmoreshells;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorpedoGuidanceMathTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void turnsAcrossYawWrapUsingShortestDirection() {
        TorpedoGuidanceMath.Velocity velocity = direction(179.0, 0.0, 2.0);
        TorpedoGuidanceMath.Velocity target = direction(-179.0, 0.0, 1.0);

        TorpedoGuidanceMath.Velocity result = TorpedoGuidanceMath.steer(
                velocity, target, 3.0, 0.0, 10.0);

        assertEquals(-179.0, TorpedoGuidanceMath.yawDegrees(result.x(), result.z()), 1.0e-7);
    }

    @Test
    void limitsHorizontalTurnRate() {
        TorpedoGuidanceMath.Velocity result = TorpedoGuidanceMath.steer(
                direction(0.0, 0.0, 2.0), direction(90.0, 0.0, 1.0),
                3.0, 1.0, 10.0);

        assertEquals(3.0, TorpedoGuidanceMath.yawDegrees(result.x(), result.z()), 1.0e-7);
    }

    @Test
    void limitsDepthCorrectionAndCommandedPitch() {
        TorpedoGuidanceMath.Velocity result = TorpedoGuidanceMath.steer(
                direction(0.0, 0.0, 2.0), new TorpedoGuidanceMath.Velocity(0.0, 10.0, 1.0),
                3.0, 1.0, 10.0);

        assertEquals(1.0, pitchDegrees(result), 1.0e-7);
        for (int i = 0; i < 20; i++) {
            result = TorpedoGuidanceMath.steer(result,
                    new TorpedoGuidanceMath.Velocity(0.0, 10.0, 1.0),
                    3.0, 1.0, 10.0);
        }
        assertEquals(10.0, pitchDegrees(result), 1.0e-7);
    }

    @Test
    void preservesSpeedWhileSteering() {
        TorpedoGuidanceMath.Velocity velocity = new TorpedoGuidanceMath.Velocity(1.25, -0.3, 2.5);
        TorpedoGuidanceMath.Velocity result = TorpedoGuidanceMath.steer(velocity,
                new TorpedoGuidanceMath.Velocity(-5.0, 3.0, 2.0), 3.0, 1.0, 10.0);

        assertEquals(velocity.length(), result.length(), EPSILON);
    }

    @Test
    void slowerTorpedoesReceiveMoreDiveAuthorityAndFasterOnesDiveSlower() {
        double slowSpeed = 0.678;
        double fastSpeed = 1.48;
        double slowPitch = TorpedoGuidanceMath.speedScaledMaxPitch(slowSpeed, 10.0);
        double fastPitch = TorpedoGuidanceMath.speedScaledMaxPitch(fastSpeed, 10.0);

        assertEquals(27.0, slowPitch, 0.15);
        assertTrue(fastPitch < 4.0);
        assertTrue(slowSpeed * Math.sin(Math.toRadians(slowPitch))
                > fastSpeed * Math.sin(Math.toRadians(fastPitch)));
    }

    @Test
    void slowLongRangeCanDiveSixtyBlocksOverOneHundredTwentyEightHorizontalBlocks() {
        double cruiseSpeed = 0.678;
        TorpedoGuidanceMath.Velocity position =
                new TorpedoGuidanceMath.Velocity(0.0, 0.0, 0.0);
        TorpedoGuidanceMath.Velocity target =
                new TorpedoGuidanceMath.Velocity(0.0, -60.0, 128.0);
        TorpedoGuidanceMath.Velocity velocity = direction(0.0, 0.0, cruiseSpeed);
        double pitchCommand = Double.NaN;
        double appliedPitch = Double.NaN;
        double pitchLimit = TorpedoGuidanceMath.speedScaledMaxPitch(cruiseSpeed, 10.0);
        double maximumAppliedDive = 0.0;
        double maximumPhysicalDive = 0.0;
        double previousPhysicalPitch = 0.0;
        int significantPitchReversals = 0;
        double previousPitchChange = 0.0;

        for (int tick = 0; tick < 400 && position.z() < 128.0; tick++) {
            // Exact underwater force model used by CBCMS 2.1.4 Slow Long Range:
            // thrust drag toward 0.678 speed, 0.09 buoyancy on -0.03 gravity,
            // and 0.16 vertical damping. CBC moves by v + 0.5F, then stores v + F.
            TorpedoGuidanceMath.Velocity force = slowLongRangeForce(velocity, cruiseSpeed);
            position = add(position, add(velocity, scale(force, 0.5)));
            velocity = add(velocity, force);
            double physicalPitch = pitchDegrees(velocity);
            maximumPhysicalDive = Math.max(maximumPhysicalDive, -physicalPitch);
            double pitchChange = physicalPitch - previousPhysicalPitch;
            if (tick > 2 && pitchChange * previousPitchChange < 0.0
                    && Math.abs(pitchChange) > 0.05
                    && Math.abs(previousPitchChange) > 0.05) significantPitchReversals++;
            if (Math.abs(pitchChange) > 1.0e-6) previousPitchChange = pitchChange;
            previousPhysicalPitch = physicalPitch;

            TorpedoGuidanceMath.Velocity toTarget = new TorpedoGuidanceMath.Velocity(
                    target.x() - position.x(), target.y() - position.y(),
                    target.z() - position.z());
            TorpedoGuidanceMath.Steering steering =
                    TorpedoGuidanceMath.steerWithPersistentPitch(
                            velocity, toTarget, 3.0, 1.0,
                            pitchLimit,
                            pitchCommand, appliedPitch);
            velocity = steering.velocity();
            pitchCommand = steering.commandPitchDegrees();
            appliedPitch = steering.appliedPitchDegrees();
            maximumAppliedDive = Math.max(maximumAppliedDive, -appliedPitch);
        }

        assertTrue(position.z() >= 128.0);
        assertEquals(-60.0, position.y(), 1.0);
        assertTrue(maximumPhysicalDive < 28.0);
        assertTrue(maximumAppliedDive <= pitchLimit + 5.0 + EPSILON,
                "maxApplied=" + maximumAppliedDive);
        assertEquals(0, significantPitchReversals);
    }

    @Test
    void persistentPitchCompensatesAngleLostToBuoyancy() {
        TorpedoGuidanceMath.Steering steering =
                TorpedoGuidanceMath.steerWithPersistentPitch(
                        direction(0.0, -3.0, 0.678),
                        new TorpedoGuidanceMath.Velocity(0.0, -60.0, 128.0),
                        3.0, 1.0, 27.0, -5.0, -4.0);

        assertEquals(-6.0, steering.commandPitchDegrees(), EPSILON);
        assertEquals(-7.0, steering.appliedPitchDegrees(), EPSILON);
        assertEquals(-7.0, pitchDegrees(steering.velocity()), EPSILON);
    }

    @Test
    void surfaceGuardRemovesClimbWithoutChangingSpeed() {
        TorpedoGuidanceMath.Velocity velocity = new TorpedoGuidanceMath.Velocity(3.0, 4.0, 0.0);
        assertTrue(TorpedoGuidanceMath.shouldFlattenForSurface(velocity, false, true));
        TorpedoGuidanceMath.Velocity result = TorpedoGuidanceMath.flattenUpward(velocity);

        assertEquals(0.0, result.y(), EPSILON);
        assertEquals(velocity.length(), result.length(), EPSILON);
        assertTrue(result.x() > 0.0);
    }

    @Test
    void surfaceGuardDoesNotTriggerUnderwaterOrAtShoreline() {
        TorpedoGuidanceMath.Velocity climbing = new TorpedoGuidanceMath.Velocity(1.0, 0.2, 1.0);
        assertFalse(TorpedoGuidanceMath.shouldFlattenForSurface(climbing, true, true));
        assertFalse(TorpedoGuidanceMath.shouldFlattenForSurface(climbing, false, false));
    }

    @Test
    void detectsTargetPassedAfterItWasAheadAndDistanceStartsGrowing() {
        TorpedoGuidanceMath.Velocity velocity = new TorpedoGuidanceMath.Velocity(0.0, 0.0, 1.0);
        TorpedoGuidanceMath.Velocity targetBehind = new TorpedoGuidanceMath.Velocity(0.0, 2.0, -2.0);

        assertTrue(TorpedoGuidanceMath.hasPassedTarget(true, 1.0, velocity, targetBehind));
    }

    @Test
    void keepsGuidingUntilAPreviouslyAheadTargetIsActuallyPassed() {
        TorpedoGuidanceMath.Velocity velocity = new TorpedoGuidanceMath.Velocity(0.0, 0.0, 1.0);
        TorpedoGuidanceMath.Velocity targetAhead = new TorpedoGuidanceMath.Velocity(0.0, 0.0, 2.0);
        TorpedoGuidanceMath.Velocity targetBehindButCloser =
                new TorpedoGuidanceMath.Velocity(0.0, 0.0, -0.5);

        assertTrue(TorpedoGuidanceMath.isHorizontallyAhead(velocity, targetAhead));
        assertFalse(TorpedoGuidanceMath.hasPassedTarget(true, 9.0, velocity, targetAhead));
        assertFalse(TorpedoGuidanceMath.hasPassedTarget(true, 1.0, velocity, targetBehindButCloser));
        assertFalse(TorpedoGuidanceMath.hasPassedTarget(false, 1.0, velocity,
                new TorpedoGuidanceMath.Velocity(0.0, 0.0, -2.0)));
    }

    private static TorpedoGuidanceMath.Velocity direction(double yaw, double pitch, double speed) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double horizontal = speed * Math.cos(pitchRadians);
        return new TorpedoGuidanceMath.Velocity(-Math.sin(yawRadians) * horizontal,
                Math.sin(pitchRadians) * speed, Math.cos(yawRadians) * horizontal);
    }

    private static double pitchDegrees(TorpedoGuidanceMath.Velocity velocity) {
        return Math.toDegrees(Math.atan2(velocity.y(), Math.hypot(velocity.x(), velocity.z())));
    }

    private static TorpedoGuidanceMath.Velocity slowLongRangeForce(
            TorpedoGuidanceMath.Velocity velocity, double cruiseSpeed) {
        double speed = velocity.length();
        TorpedoGuidanceMath.Velocity drag = speed < EPSILON
                ? new TorpedoGuidanceMath.Velocity(0.0, 0.0, 0.0)
                : scale(velocity, -0.07 * (speed - cruiseSpeed) / speed);
        return add(drag, new TorpedoGuidanceMath.Velocity(
                0.0, 0.0027 - 0.16 * velocity.y(), 0.0));
    }

    private static TorpedoGuidanceMath.Velocity add(TorpedoGuidanceMath.Velocity left,
                                                     TorpedoGuidanceMath.Velocity right) {
        return new TorpedoGuidanceMath.Velocity(
                left.x() + right.x(), left.y() + right.y(), left.z() + right.z());
    }

    private static TorpedoGuidanceMath.Velocity scale(
            TorpedoGuidanceMath.Velocity velocity, double factor) {
        return new TorpedoGuidanceMath.Velocity(
                velocity.x() * factor, velocity.y() * factor, velocity.z() * factor);
    }
}
