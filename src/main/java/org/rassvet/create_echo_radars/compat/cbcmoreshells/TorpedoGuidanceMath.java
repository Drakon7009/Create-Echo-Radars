package org.rassvet.create_echo_radars.compat.cbcmoreshells;

/** Pure guidance math kept separate from Minecraft state for regression tests. */
public final class TorpedoGuidanceMath {
    private static final double EPSILON = 1.0e-9;
    private static final double DIVE_SPEED_EXPONENT = 2.56;
    private static final double ABSOLUTE_MAX_PITCH_DEGREES = 45.0;
    private static final double MAX_FORCE_COMPENSATION_DEGREES = 5.0;

    private TorpedoGuidanceMath() {}

    public static Velocity steer(Velocity velocity, Velocity toTarget,
                                 double yawDegreesPerTick, double pitchDegreesPerTick,
                                 double maxPitchDegrees) {
        double speed = velocity.length();
        if (speed < EPSILON) return velocity;

        double currentYaw = yawDegrees(velocity.x, velocity.z);
        double targetHorizontal = Math.hypot(toTarget.x, toTarget.z);
        double desiredYaw = targetHorizontal < EPSILON
                ? currentYaw : yawDegrees(toTarget.x, toTarget.z);
        double nextYaw = currentYaw + clamp(wrapDegrees(desiredYaw - currentYaw),
                -Math.max(0.0, yawDegreesPerTick), Math.max(0.0, yawDegreesPerTick));

        double currentPitch = pitchDegrees(velocity);
        double pitchLimit = Math.max(0.0, maxPitchDegrees);
        double desiredPitch = Math.toDegrees(Math.atan2(toTarget.y, targetHorizontal));
        desiredPitch = clamp(desiredPitch, -pitchLimit, pitchLimit);
        double pitchStep = Math.max(0.0, pitchDegreesPerTick);
        double nextPitch = currentPitch + clamp(desiredPitch - currentPitch, -pitchStep, pitchStep);

        return direction(nextYaw, nextPitch, speed);
    }

    /**
     * Steers with a pitch command which is independent of the physical pitch.
     * CBCMS applies buoyancy after every guidance tick; deriving the next step
     * from that disturbed velocity would otherwise erase most of a slow
     * torpedo's downward correction.
     */
    public static Steering steerWithPersistentPitch(Velocity velocity, Velocity toTarget,
                                                     double yawDegreesPerTick,
                                                     double pitchDegreesPerTick,
                                                     double maxPitchDegrees,
                                                     double previousCommandPitchDegrees,
                                                     double previousAppliedPitchDegrees) {
        double speed = velocity.length();
        double physicalPitch = pitchDegrees(velocity);
        if (speed < EPSILON) {
            double stoppedPitch = Double.isFinite(previousCommandPitchDegrees)
                    ? previousCommandPitchDegrees : physicalPitch;
            return new Steering(velocity, stoppedPitch, physicalPitch);
        }

        double currentYaw = yawDegrees(velocity.x, velocity.z);
        double targetHorizontal = Math.hypot(toTarget.x, toTarget.z);
        double desiredYaw = targetHorizontal < EPSILON
                ? currentYaw : yawDegrees(toTarget.x, toTarget.z);
        double nextYaw = currentYaw + clamp(wrapDegrees(desiredYaw - currentYaw),
                -Math.max(0.0, yawDegreesPerTick), Math.max(0.0, yawDegreesPerTick));

        double pitchLimit = clamp(maxPitchDegrees, 0.0, ABSOLUTE_MAX_PITCH_DEGREES);
        double desiredPitch = Math.toDegrees(Math.atan2(toTarget.y, targetHorizontal));
        desiredPitch = clamp(desiredPitch, -pitchLimit, pitchLimit);
        double commandPitch = Double.isFinite(previousCommandPitchDegrees)
                ? clamp(previousCommandPitchDegrees, -pitchLimit, pitchLimit)
                : clamp(physicalPitch, -pitchLimit, pitchLimit);
        double pitchStep = Math.max(0.0, pitchDegreesPerTick);
        double nextCommandPitch = commandPitch
                + clamp(desiredPitch - commandPitch, -pitchStep, pitchStep);

        // Measure only the angle that CBCMS forces changed since our previous
        // output. Comparing physical pitch directly with the newly advanced
        // command also counts this tick's command step as an error, doubles the
        // correction and makes the torpedo oscillate.
        double forceDisturbance = Double.isFinite(previousAppliedPitchDegrees)
                ? clamp(physicalPitch - previousAppliedPitchDegrees,
                        -MAX_FORCE_COMPENSATION_DEGREES, MAX_FORCE_COMPENSATION_DEGREES)
                : 0.0;
        double appliedPitchLimit = Math.min(ABSOLUTE_MAX_PITCH_DEGREES,
                pitchLimit + MAX_FORCE_COMPENSATION_DEGREES);
        double compensatedPitch = clamp(nextCommandPitch - forceDisturbance,
                -appliedPitchLimit, appliedPitchLimit);
        return new Steering(direction(nextYaw, compensatedPitch, speed),
                nextCommandPitch, compensatedPitch);
    }

    public static double speedScaledMaxPitch(double speed, double referencePitchDegrees) {
        double referencePitch = clamp(referencePitchDegrees, 0.0, ABSOLUTE_MAX_PITCH_DEGREES);
        if (referencePitch == 0.0) return 0.0;
        double safeSpeed = Math.max(0.05, speed);
        return clamp(referencePitch / Math.pow(safeSpeed, DIVE_SPEED_EXPONENT),
                0.0, ABSOLUTE_MAX_PITCH_DEGREES);
    }

    public static Velocity flattenUpward(Velocity velocity) {
        if (velocity.y <= 0.0) return velocity;
        double speed = velocity.length();
        double horizontal = Math.hypot(velocity.x, velocity.z);
        if (horizontal < EPSILON) return new Velocity(0.0, 0.0, speed);
        double scale = speed / horizontal;
        return new Velocity(velocity.x * scale, 0.0, velocity.z * scale);
    }

    public static boolean shouldFlattenForSurface(Velocity velocity,
                                                  boolean projectedImmersed,
                                                  boolean projectedLevelImmersed) {
        return velocity.y > 0.0 && !projectedImmersed && projectedLevelImmersed;
    }

    public static boolean isHorizontallyAhead(Velocity velocity, Velocity toTarget) {
        return velocity.x * toTarget.x + velocity.z * toTarget.z > EPSILON;
    }

    public static boolean hasPassedTarget(boolean wasAhead, double previousDistanceSquared,
                                          Velocity velocity, Velocity toTarget) {
        if (!wasAhead || !Double.isFinite(previousDistanceSquared)
                || velocity.x * velocity.x + velocity.z * velocity.z < EPSILON) {
            return false;
        }
        return !isHorizontallyAhead(velocity, toTarget)
                && horizontalDistanceSquared(toTarget) > previousDistanceSquared + EPSILON;
    }

    public static double horizontalDistanceSquared(Velocity vector) {
        return vector.x * vector.x + vector.z * vector.z;
    }

    public static double yawDegrees(double x, double z) {
        return Math.abs(x) < EPSILON && Math.abs(z) < EPSILON
                ? 0.0 : Math.toDegrees(Math.atan2(-x, z));
    }

    public static double pitchDegrees(Velocity velocity) {
        return Math.toDegrees(Math.atan2(velocity.y,
                Math.hypot(velocity.x, velocity.z)));
    }

    public static double wrapDegrees(double degrees) {
        degrees %= 360.0;
        if (degrees >= 180.0) degrees -= 360.0;
        if (degrees < -180.0) degrees += 360.0;
        return degrees;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static Velocity direction(double yawDegrees, double pitchDegrees, double speed) {
        double yawRadians = Math.toRadians(yawDegrees);
        double pitchRadians = Math.toRadians(pitchDegrees);
        double horizontalSpeed = speed * Math.cos(pitchRadians);
        return new Velocity(
                -Math.sin(yawRadians) * horizontalSpeed,
                Math.sin(pitchRadians) * speed,
                Math.cos(yawRadians) * horizontalSpeed);
    }

    public record Steering(Velocity velocity, double commandPitchDegrees,
                           double appliedPitchDegrees) {}

    public record Velocity(double x, double y, double z) {
        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }
}
