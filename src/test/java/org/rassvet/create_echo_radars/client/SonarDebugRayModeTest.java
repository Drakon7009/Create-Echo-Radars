package org.rassvet.create_echo_radars.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarDebugRayModeTest {
    @Test
    void offSkipsAllRayTracing() {
        assertFalse(SonarDebugRayMode.OFF.tracesAnything());
        assertFalse(SonarDebugRayMode.OFF.shows(false));
        assertFalse(SonarDebugRayMode.OFF.shows(true));
    }

    @Test
    void mainShowsOnlyBaseRays() {
        assertTrue(SonarDebugRayMode.MAIN.tracesAnything());
        assertTrue(SonarDebugRayMode.MAIN.shows(false));
        assertFalse(SonarDebugRayMode.MAIN.shows(true));
        assertFalse(SonarDebugRayMode.MAIN.tracesRefinements());
    }

    @Test
    void beforeBlockShowsOnlyRefinementRays() {
        assertTrue(SonarDebugRayMode.BEFORE_BLOCK.tracesAnything());
        assertFalse(SonarDebugRayMode.BEFORE_BLOCK.shows(false));
        assertTrue(SonarDebugRayMode.BEFORE_BLOCK.shows(true));
        assertTrue(SonarDebugRayMode.BEFORE_BLOCK.tracesRefinements());
    }

    @Test
    void allShowsBaseAndRefinementRays() {
        assertTrue(SonarDebugRayMode.ALL.shows(false));
        assertTrue(SonarDebugRayMode.ALL.shows(true));
    }
}
