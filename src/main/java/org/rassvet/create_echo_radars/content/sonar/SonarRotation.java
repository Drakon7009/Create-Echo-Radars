package org.rassvet.create_echo_radars.content.sonar;

import java.util.ArrayList;
import java.util.List;

public final class SonarRotation {
    public static final float MECHANICAL_SCAN_STEP_DEGREES = 2.0f;
    private static final float RPM_TO_DEGREES_PER_TICK = 360f / 60f / 20f;
    private static final float MINIMUM_ANGULAR_SPEED = 1.0e-5f;
    private static final float MECHANICAL_REVEAL_DEGREES = 2.0f;
    private static final float AUTOMATIC_REVISIT_ALPHA = 0.3f;

    private SonarRotation() {
    }

    public static float angularSpeed(float rpm, float createMaximumRpm) {
        float requested = rpm * RPM_TO_DEGREES_PER_TICK;
        float maximum = Math.max(0, createMaximumRpm) * RPM_TO_DEGREES_PER_TICK / 4f;
        return Math.max(-maximum, Math.min(maximum, requested));
    }

    public static float advance(float angle, float angularSpeed) {
        return wrap(angle + angularSpeed);
    }

    public static float wrap(float angle) {
        float wrapped = angle % 360f;
        return wrapped < 0 ? wrapped + 360f : wrapped;
    }

    public static float scanStep(float angle, float stepDegrees) {
        float step = Math.max(0.001f, Math.abs(stepDegrees));
        return wrap((float) Math.floor(wrap(angle) / step) * step);
    }

    public static float nearestScanStep(float angle, float stepDegrees) {
        float step = Math.max(0.001f, Math.abs(stepDegrees));
        return wrap(Math.round(wrap(angle) / step) * step);
    }

    public static float prefetchAngle(float angle, float angularSpeed,
                                      float minimumDegrees, float lookAheadTicks,
                                      float maximumDegrees) {
        if (Math.abs(angularSpeed) < MINIMUM_ANGULAR_SPEED) return wrap(angle);
        float lookAhead = Math.max(Math.abs(minimumDegrees),
                Math.abs(angularSpeed) * Math.max(0, lookAheadTicks));
        lookAhead = Math.min(Math.max(Math.abs(minimumDegrees), maximumDegrees), lookAhead);
        return advance(angle, Math.copySign(lookAhead, angularSpeed));
    }

    public static float directedDistance(float fromAngle, float toAngle, float angularSpeed) {
        if (angularSpeed > MINIMUM_ANGULAR_SPEED) return wrap(toAngle - fromAngle);
        if (angularSpeed < -MINIMUM_ANGULAR_SPEED) return wrap(fromAngle - toAngle);
        return 0;
    }

    public static boolean crossedAngle(float previousAngle, float currentAngle,
                                       float targetAngle, float angularSpeed) {
        float travelled = directedDistance(previousAngle, currentAngle, angularSpeed);
        if (travelled <= 1.0e-4f) return false;
        float targetDistance = directedDistance(previousAngle, targetAngle, angularSpeed);
        return targetDistance > 1.0e-4f && targetDistance <= travelled + 1.0e-4f;
    }

    public static double mechanicalEmitterDistance(float angle) {
        double radians = Math.toRadians(wrap(angle));
        double dominantAxis = Math.max(Math.abs(Math.cos(radians)), Math.abs(Math.sin(radians)));
        return 0.6 / Math.max(1.0e-6, dominantAxis);
    }

    public static List<Float> crossedScanSteps(float previousStep, float currentAngle,
                                               float angularSpeed, float stepDegrees) {
        float step = Math.max(0.001f, Math.abs(stepDegrees));
        if (Math.abs(angularSpeed) < 1.0e-5f) return List.of();
        boolean forward = angularSpeed > 0;
        float distance = forward ? wrap(currentAngle - previousStep) : wrap(previousStep - currentAngle);
        int maximumSteps = Math.max(1, (int) Math.ceil(360f / step));
        int count = Math.min(maximumSteps, (int) Math.floor((distance + 1.0e-4f) / step));
        if (count <= 0) return List.of();
        List<Float> result = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            result.add(wrap(previousStep + (forward ? i * step : -i * step)));
        }
        return List.copyOf(result);
    }

    public static float mechanicalPixelAlpha(int configuredLifetimeTicks, double firstSeenTick,
                                             double currentTick, float angularSpeed) {
        double age = Math.max(0, currentTick - firstSeenTick);
        double speed = Math.abs(angularSpeed);
        double lifetime;
        double fadeInTicks;
        if (configuredLifetimeTicks == 0) {
            if (speed < MINIMUM_ANGULAR_SPEED) return 1;
            double revolutionTicks = 360 / speed;
            fadeInTicks = automaticFadeInTicks(speed, revolutionTicks);
            lifetime = fadeInTicks + (revolutionTicks - fadeInTicks)
                    / (1 - AUTOMATIC_REVISIT_ALPHA);
        } else {
            lifetime = Math.max(1, configuredLifetimeTicks);
            fadeInTicks = Math.min(2, Math.max(0.25, lifetime * 0.1));
        }

        double remaining = lifetime - age;
        if (remaining <= 0) return 0;
        if (age < fadeInTicks) {
            return smoothStep((float) (age / fadeInTicks));
        }
        double fadeOutTicks = Math.max(1.0e-6, lifetime - fadeInTicks);
        return (float) Math.min(1, remaining / fadeOutTicks);
    }

    public static double mechanicalAutomaticLifetimeTicks(float angularSpeed) {
        double speed = Math.abs(angularSpeed);
        if (speed < MINIMUM_ANGULAR_SPEED) return Double.POSITIVE_INFINITY;
        double revolutionTicks = 360 / speed;
        double fadeInTicks = automaticFadeInTicks(speed, revolutionTicks);
        return fadeInTicks + (revolutionTicks - fadeInTicks)
                / (1 - AUTOMATIC_REVISIT_ALPHA);
    }

    private static double automaticFadeInTicks(double speed, double revolutionTicks) {
        double revealTicks = Math.max(0.25,
                Math.min(2, MECHANICAL_REVEAL_DEGREES / speed));
        return Math.min(revealTicks, revolutionTicks * 0.1);
    }

    private static float smoothStep(float value) {
        float clamped = Math.max(0, Math.min(1, value));
        return clamped * clamped * (3 - 2 * clamped);
    }
}
