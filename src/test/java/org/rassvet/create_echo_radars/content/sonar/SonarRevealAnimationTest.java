package org.rassvet.create_echo_radars.content.sonar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SonarRevealAnimationTest {
    @Test
    void refiningRaysAndLateSnapshotsNeverRewindTheDisplayedFront() {
        var state = new SonarRevealAnimation(0);
        state.advance(0.75f, false, 15, 0.05f);
        state.advance(0.25f, false, 16, 0.05f);
        assertEquals(0.75f, state.progress(), 1.0e-6);
        state.advance(1, true, 30, 0.05f);
        assertTrue(state.fullyRevealed());
        double age = state.age(40);
        state.advance(0.1f, false, 40, 0.05f);
        assertEquals(1, state.progress());
        assertEquals(age, state.age(40));
    }

    @Test
    void worldAndScreenRenderingDoNotAdvanceTheSameTickTwice() {
        var state = new SonarRevealAnimation(10);
        state.advance(1, false, 11.5, 0.1f);
        state.advance(1, false, 11.5, 0.1f);
        assertEquals(0.15f, state.progress(), 1.0e-6);
        state.advance(1, false, 11, 0.1f);
        state.advance(1, false, 12, 0.1f);
        assertEquals(0.2f, state.progress(), 1.0e-6);
    }

    @Test
    void completedScanSurvivesLongPeriodsWithoutRendering() {
        var state = new SonarRevealAnimation(0);
        state.advance(1, true, 20, 0.05f);
        state.advance(0.2f, false, 4000, 0.05f);
        assertEquals(1, state.progress());
        assertTrue(state.fullyRevealed());
        assertEquals(3980, state.age(4000));
    }
}
