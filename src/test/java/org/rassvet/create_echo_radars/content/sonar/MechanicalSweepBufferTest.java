package org.rassvet.create_echo_radars.content.sonar;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class MechanicalSweepBufferTest {
    @Test
    void reusesFlattenedVisibleValuesUntilDisplayChanges() {
        MechanicalSweepBuffer<String, String> buffer = buffer();
        buffer.replaceSector(0, Map.of("zero", "zero"), 0);

        var first = buffer.visibleValues();
        assertSame(first, buffer.visibleValues());

        buffer.replaceSector(2, Map.of("two", "two"), 0);
        var second = buffer.visibleValues();
        assertNotSame(first, second);
        assertSame(second, buffer.visibleValues());
    }

    @Test
    void forwardSweepClearsOnlyCrossedSectorsAndAtomicallyReplacesPendingData() {
        MechanicalSweepBuffer<String, String> buffer = buffer();
        buffer.replaceSector(0, Map.of("old-0", "old-0"), 0);
        buffer.replaceSector(2, Map.of("old-2", "old-2"), 0);
        buffer.replaceSector(4, Map.of("old-4", "old-4"), 0);
        buffer.queue(2, 2, Map.of("new-2", "new-2"));

        buffer.advanceSweep(359, 4, 0);
        buffer.advanceSweep(3, 4, 1);

        assertFalse(buffer.hasVisibleSector(0));
        assertTrue(buffer.hasVisibleSector(2));
        assertTrue(buffer.hasVisibleSector(4));
        Set<String> values = visibleValues(buffer);
        assertFalse(values.contains("old-2"));
        assertTrue(values.contains("new-2"));
        assertTrue(values.contains("old-4"));

        buffer.advanceSweep(5, 4, 1.5);
        assertTrue(buffer.hasVisibleSector(2));
        assertTrue(visibleValues(buffer).contains("new-2"));
    }

    @Test
    void reverseSweepCrossesWrapAndEmptyPendingSectorClearsOldData() {
        MechanicalSweepBuffer<String, String> buffer = buffer();
        buffer.replaceSector(358, Map.of("old", "old"), 0);
        buffer.replaceSector(180, Map.of("untouched", "untouched"), 0);
        buffer.queue(3, 358, Map.of());

        buffer.advanceSweep(1, -4, 0);
        buffer.advanceSweep(357, -4, 1);

        assertFalse(buffer.hasVisibleSector(358));
        assertTrue(buffer.hasVisibleSector(180));
    }

    @Test
    void newerEpochDoesNotClearUnrelatedVisibleSectors() {
        MechanicalSweepBuffer<String, String> buffer = buffer();
        buffer.replaceSector(40, Map.of("forty", "forty"), 0);
        buffer.replaceSector(220, Map.of("two-twenty", "two-twenty"), 0);
        buffer.advanceSweep(0, 2, 0);

        for (long epoch = 1; epoch <= 5; epoch++) {
            buffer.queue(epoch, 20, Map.of("epoch", "epoch-" + epoch));
        }
        buffer.advanceSweep(22, 2, 11);

        assertTrue(buffer.hasVisibleSector(20));
        assertTrue(buffer.hasVisibleSector(40));
        assertTrue(buffer.hasVisibleSector(220));
        assertEquals(3, buffer.visibleSectorCount());
        assertTrue(visibleValues(buffer).contains("epoch-5"));
    }

    @Test
    void lateSectorUpdatesOnlyItsOwnPosition() {
        MechanicalSweepBuffer<String, String> buffer = buffer();
        buffer.replaceSector(90, Map.of("ninety", "ninety"), 0);
        buffer.queue(7, 0, Map.of("late", "late"));

        buffer.advanceSweep(200, 5, 40);

        assertTrue(buffer.hasVisibleSector(0));
        assertTrue(buffer.hasVisibleSector(90));
        assertEquals(2, buffer.visibleSectorCount());
    }

    @Test
    void prefetchedSectorWaitsForTheSweepAndActivatesAtTheCrossing() {
        MechanicalSweepBuffer<String, String> buffer = buffer();
        buffer.advanceSweep(0, 10, 0);
        buffer.queue(1, 40, Map.of("ahead", "ahead"));

        buffer.advanceSweep(30, 10, 3);
        assertFalse(buffer.hasVisibleSector(40));

        buffer.advanceSweep(50, 10, 5);
        assertTrue(buffer.hasVisibleSector(40));
        MechanicalSweepBuffer.VisibleValue<String> value =
                buffer.visibleValues().iterator().next();
        assertEquals(4, value.activatedTick(), 1.0e-5);
        float halfwayVisible = SonarRotation.mechanicalPixelAlpha(
                0, value.activatedTick(), 4.5, 10);
        assertTrue(halfwayVisible > 0 && halfwayVisible < 1);
    }

    @Test
    void backwardNetworkCorrectionDoesNotLookLikeAFullRevolution() {
        MechanicalSweepBuffer<String, String> buffer = buffer();
        buffer.replaceSector(0, Map.of("zero", "zero"), 0);
        buffer.replaceSector(90, Map.of("ninety", "ninety"), 0);
        buffer.replaceSector(180, Map.of("one-eighty", "one-eighty"), 0);

        buffer.advanceSweep(100, 4.8f, 10);
        buffer.advanceSweep(99, 4.8f, 10.1);

        assertEquals(3, buffer.visibleSectorCount());
        assertEquals(Set.of("zero", "ninety", "one-eighty"), visibleValues(buffer));
    }

    @Test
    void trackLatchKeepsUnsweptPositionAndRemovesMissingSweptPosition() {
        Map<String, Integer> tracks = new HashMap<>();
        tracks.put("moving", 10);
        tracks.put("stationary", 20);

        MechanicalTrackLatch.clearMissingSweptTracks(tracks, Set.of("stationary"),
                position -> position == 10);

        assertFalse(tracks.containsKey("moving"));
        assertEquals(20, tracks.get("stationary"));

        tracks.put("moving", 40);
        MechanicalTrackLatch.clearMissingSweptTracks(tracks, Set.of(), position -> position == 60);
        assertEquals(40, tracks.get("moving"));
    }

    @Test
    void completedFrameWindowKeepsEveryBatchBetweenFiveTickSynchronizations() {
        MechanicalFrameWindow<String> window = new MechanicalFrameWindow<>(20, 32);
        window.add(1, "sector-0");
        window.add(2, "sector-2");
        window.add(3, "sector-4");
        window.add(4, "sector-6");

        assertEquals(java.util.List.of("sector-0", "sector-2", "sector-4", "sector-6"),
                window.values());
        assertFalse(window.prune(21));
        assertTrue(window.prune(22));
        assertEquals(java.util.List.of("sector-2", "sector-4", "sector-6"), window.values());
    }

    private static MechanicalSweepBuffer<String, String> buffer() {
        return new MechanicalSweepBuffer<>(2, 120);
    }

    private static Set<String> visibleValues(MechanicalSweepBuffer<String, String> buffer) {
        Set<String> result = new HashSet<>();
        for (MechanicalSweepBuffer.VisibleValue<String> value : buffer.visibleValues()) {
            result.add(value.value());
        }
        return result;
    }
}
