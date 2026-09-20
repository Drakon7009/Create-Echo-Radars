package org.rassvet.create_echo_radars.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SonarRenderLodTest {
    @Test
    void preservesFullDetailNearDisplay() {
        assertEquals(SonarRenderLod.FULL, SonarRenderLod.forDistance(16, 1));
    }

    @Test
    void reducesDetailForSmallDistantDisplays() {
        assertEquals(SonarRenderLod.MEDIUM, SonarRenderLod.forDistance(24, 1));
        assertEquals(SonarRenderLod.FAR, SonarRenderLod.forDistance(40, 1));
        assertEquals(SonarRenderLod.DISTANT, SonarRenderLod.forDistance(56, 1));
    }

    @Test
    void keepsLargeDisplaysDetailedFartherAway() {
        assertEquals(SonarRenderLod.FULL, SonarRenderLod.forDistance(48, 3));
        assertEquals(SonarRenderLod.MEDIUM, SonarRenderLod.forDistance(64, 3));
    }
}
