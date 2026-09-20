package org.rassvet.create_echo_radars.content.summator;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SummatorLinkRemapTest {
    @Test
    void resolvesOneMove() {
        assertEquals(20, SummatorLinkRemap.resolve(10,
                Map.of(10L, new SummatorLinkRemap.Move(20, 40, 100)), 50));
    }

    @Test
    void followsRepeatedSableMoves() {
        Map<Long, SummatorLinkRemap.Move> moves = Map.of(
                10L, new SummatorLinkRemap.Move(20, 40, 100),
                20L, new SummatorLinkRemap.Move(30, 45, 100));
        assertEquals(30, SummatorLinkRemap.resolve(10, moves, 50));
    }

    @Test
    void doesNotConfuseAdjacentBlocksMovedInTheSameAssembly() {
        Map<Long, SummatorLinkRemap.Move> moves = Map.of(
                10L, new SummatorLinkRemap.Move(20, 40, 100),
                20L, new SummatorLinkRemap.Move(30, 40, 100));
        assertEquals(20, SummatorLinkRemap.resolve(10, moves, 50));
    }

    @Test
    void ignoresExpiredMove() {
        assertEquals(10, SummatorLinkRemap.resolve(10,
                Map.of(10L, new SummatorLinkRemap.Move(20, 40, 49)), 50));
    }

    @Test
    void stopsOnCycle() {
        Map<Long, SummatorLinkRemap.Move> moves = Map.of(
                10L, new SummatorLinkRemap.Move(20, 40, 100),
                20L, new SummatorLinkRemap.Move(10, 45, 100));
        assertEquals(10, SummatorLinkRemap.resolve(10, moves, 50));
    }
}
