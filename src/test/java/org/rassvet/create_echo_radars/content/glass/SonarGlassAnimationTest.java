package org.rassvet.create_echo_radars.content.glass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarGlassAnimationTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void fixedCycleDoesNotUseSonarTimingSettings() {
        long connectedAt = 37;
        assertEquals(37, connectedAt);
        assertEquals(137, connectedAt + SonarGlassAnimation.CYCLE_TICKS);
        assertEquals(237, connectedAt + 2L * SonarGlassAnimation.CYCLE_TICKS);
        assertEquals(100, SonarGlassAnimation.CYCLE_TICKS);
    }

    @Test
    void frontUsesEightyTicksThenHolds() {
        assertEquals(0, SonarGlassAnimation.front(0), EPSILON);
        assertEquals(.5f, SonarGlassAnimation.front(40), EPSILON);
        assertEquals(1, SonarGlassAnimation.front(80), EPSILON);
        assertEquals(1, SonarGlassAnimation.front(100), EPSILON);
    }

    @Test
    void softBandIsEightPercentOfRange() {
        float alphaNearBand = SonarGlassAnimation.newAlpha(.50f, 40);
        float alphaMiddleBand = SonarGlassAnimation.newAlpha(.54f, 40);
        float alphaFarBand = SonarGlassAnimation.newAlpha(.58f, 40);
        assertEquals(1, alphaNearBand, EPSILON);
        assertTrue(alphaMiddleBand > 0 && alphaMiddleBand < 1);
        assertEquals(0, alphaFarBand, EPSILON);
    }

    @Test
    void oldFrameDimsAndIsReplacedByFront() {
        assertEquals(1, SonarGlassAnimation.oldAlpha(.9f, 0), EPSILON);
        assertEquals(.3f, SonarGlassAnimation.oldAlpha(.9f, 10), EPSILON);
        assertEquals(0, SonarGlassAnimation.oldAlpha(.2f, 40), EPSILON);
    }

    @Test
    void disconnectFadesOverTwentyTicks() {
        assertEquals(1, SonarGlassAnimation.disconnectAlpha(0), EPSILON);
        assertEquals(.5f, SonarGlassAnimation.disconnectAlpha(10), EPSILON);
        assertEquals(0, SonarGlassAnimation.disconnectAlpha(20), EPSILON);
    }
}
