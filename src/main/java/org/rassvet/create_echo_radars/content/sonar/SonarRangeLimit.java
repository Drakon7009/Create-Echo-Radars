package org.rassvet.create_echo_radars.content.sonar;

/** Calculates the range available for a sonar's currently selected field of view. */
public final class SonarRangeLimit {
    private static final double REDUCTION_START = 2.0 / 3.0;
    private static final double MINIMUM_RANGE_SCALE = 0.5;

    private SonarRangeLimit() {}

    public static int effectiveMaximumRange(SonarType type, int horizontalAngle, int verticalAngle,
                                            int configuredMaximumRange, boolean angleReductionEnabled) {
        if (!angleReductionEnabled) return configuredMaximumRange;

        int maximumAngleSum = type.maximumHorizontalAngle() + type.maximumVerticalAngle();
        int selectedAngleSum = clamp(horizontalAngle, type.minimumHorizontalAngle(),
                type.maximumHorizontalAngle())
                + clamp(verticalAngle, type.minimumVerticalAngle(), type.maximumVerticalAngle());
        double usedAngleFraction = selectedAngleSum / (double) maximumAngleSum;
        if (usedAngleFraction <= REDUCTION_START) return configuredMaximumRange;

        double reductionProgress = (usedAngleFraction - REDUCTION_START)
                / (1.0 - REDUCTION_START);
        double rangeScale = 1.0 - reductionProgress * (1.0 - MINIMUM_RANGE_SCALE);
        return Math.max(1, (int) Math.round(configuredMaximumRange * rangeScale));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
