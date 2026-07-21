package org.rassvet.create_echo_radars.content.sonar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NearbyBlockTrackerTest {
    @Test
    void detectsSameAndAdjacentBlocksButNotDistantBlocks() {
        NearbyBlockTracker tracker = new NearbyBlockTracker();

        assertFalse(tracker.hasNearbyAndRecord(10, 20, 30));
        assertTrue(tracker.hasNearbyAndRecord(11, 21, 31));
        assertFalse(tracker.hasNearbyAndRecord(13, 21, 31));
        assertTrue(tracker.hasNearbyAndRecord(13, 21, 31));
    }

    @Test
    void clearStartsANewDetectionPass() {
        NearbyBlockTracker tracker = new NearbyBlockTracker();
        tracker.hasNearbyAndRecord(0, 0, 0);
        tracker.clear();
        assertFalse(tracker.hasNearbyAndRecord(0, 0, 0));
    }
}
