package org.rassvet.create_echo_radars.content.sonar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SideScanHitFilterTest {
    @Test
    void duplicateHitsOnOneBlockSuppressEveryRefinement() {
        List<SideScanHitFilter.Cell> hits = List.of(
                new SideScanHitFilter.Cell(4, 10, 7),
                new SideScanHitFilter.Cell(4, 10, 7),
                new SideScanHitFilter.Cell(4, 10, 7));

        assertArrayEquals(new boolean[]{false, false, false},
                SideScanHitFilter.refinementsAllowed(hits));
    }

    @Test
    void verticalNeighboursSuppressEachOtherInBothDirections() {
        List<SideScanHitFilter.Cell> hits = List.of(
                new SideScanHitFilter.Cell(4, 10, 7),
                new SideScanHitFilter.Cell(4, 11, 7));

        assertArrayEquals(new boolean[]{false, false},
                SideScanHitFilter.refinementsAllowed(hits));
    }

    @Test
    void horizontalNeighboursRemainEligible() {
        List<SideScanHitFilter.Cell> hits = List.of(
                new SideScanHitFilter.Cell(4, 10, 7),
                new SideScanHitFilter.Cell(5, 10, 7));

        assertArrayEquals(new boolean[]{true, true},
                SideScanHitFilter.refinementsAllowed(hits));
    }
}
