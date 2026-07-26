package org.rassvet.create_echo_radars.content.glass;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SonarGlassFloodFillTest {
    @Test void mixedBlocksAndPanesShareFaceConnectedComponent() {
        Set<Long> glass = Set.of(0L, 1L, 2L, 10L); // Types are irrelevant; membership models either block.
        SonarGlassFloodFill.Result result = SonarGlassFloodFill.find(0, glass::contains,
                SonarGlassFloodFillTest::lineNeighbors, 4096);
        assertEquals(Set.of(0L, 1L, 2L), result.nodes());
        assertFalse(result.overflow());
    }

    @Test void splittingCreatesIndependentWindows() {
        Set<Long> glass = Set.of(0L, 1L, 3L, 4L);
        assertEquals(Set.of(0L, 1L), SonarGlassFloodFill.find(0, glass::contains,
                SonarGlassFloodFillTest::lineNeighbors, 4096).nodes());
        assertEquals(Set.of(3L, 4L), SonarGlassFloodFill.find(3, glass::contains,
                SonarGlassFloodFillTest::lineNeighbors, 4096).nodes());
    }

    @Test void limitReportsOverflowWithoutChangingGraph() {
        Set<Long> glass = new HashSet<>();
        for (long i=0;i<6;i++) glass.add(i);
        SonarGlassFloodFill.Result result = SonarGlassFloodFill.find(0, glass::contains,
                SonarGlassFloodFillTest::lineNeighbors, 4);
        assertTrue(result.overflow());
        assertEquals(4, result.nodes().size());
        assertEquals(6, glass.size());
    }

    private static long[] lineNeighbors(long node) { return new long[]{node-1,node+1}; }
}
