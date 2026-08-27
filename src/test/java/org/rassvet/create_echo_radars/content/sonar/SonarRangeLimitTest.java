package org.rassvet.create_echo_radars.content.sonar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SonarRangeLimitTest {
    @Test
    void keepsFullRangeAtAndBelowTwoThirdsOfMaximumAngleSum() {
        assertEquals(96, SonarRangeLimit.effectiveMaximumRange(
                SonarType.ECHO_SOUNDER_A, 40, 40, 96, true));
        assertEquals(96, SonarRangeLimit.effectiveMaximumRange(
                SonarType.FORWARD_LOOKING_F, 100, 20, 96, true));
    }

    @Test
    void reducesRangeLinearlyAfterThreshold() {
        assertEquals(72, SonarRangeLimit.effectiveMaximumRange(
                SonarType.ECHO_SOUNDER_A, 60, 40, 96, true));
        assertEquals(84, SonarRangeLimit.effectiveMaximumRange(
                SonarType.FORWARD_LOOKING_F, 120, 30, 96, true));
    }

    @Test
    void halvesRangeAtMaximumAngles() {
        for (SonarType type : SonarType.values()) {
            assertEquals(48, SonarRangeLimit.effectiveMaximumRange(type,
                    type.maximumHorizontalAngle(), type.maximumVerticalAngle(), 96, true));
        }
    }

    @Test
    void disabledReductionAlwaysKeepsConfiguredMaximum() {
        assertEquals(180, SonarRangeLimit.effectiveMaximumRange(
                SonarType.FORWARD_LOOKING_F, 120, 80, 180, false));
    }

    @Test
    void clampsAnglesToSonarSpecificLimits() {
        assertEquals(48, SonarRangeLimit.effectiveMaximumRange(
                SonarType.MECHANICAL_IMAGING_C, 999, 999, 96, true));
    }
}
