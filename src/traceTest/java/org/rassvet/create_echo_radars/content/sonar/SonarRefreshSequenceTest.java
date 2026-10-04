package org.rassvet.create_echo_radars.content.sonar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SonarRefreshSequenceTest {
    @Test
    void serverCompletionKeepsTheSameSweepAndItsPreviousImage() {
        SonarFrame old = frame(1, true);
        SonarFrame scanning = frame(2, false);
        var before = SonarRefreshSequence.select(List.of(old, scanning), 1);
        var after = SonarRefreshSequence.select(List.of(old, frame(2, true)), 1);

        assertEquals(before.refreshing().epoch(), after.refreshing().epoch());
        assertFalse(after.pending(old));
        // Server completion must not remove the faded previous image while
        // the client is still displaying the replacement sweep.
        assertEquals(0.3f, SonarDisplayLayout.forwardFrameAlpha(60, 200, false));
    }

    @Test
    void fasterServerScansWaitForTheDisplayedSweep() {
        SonarFrame old = frame(1, true);
        SonarFrame refreshing = frame(2, true);
        SonarFrame queued = frame(3, true);
        SonarFrame scanning = frame(4, false);
        var sequence = SonarRefreshSequence.select(List.of(scanning, queued, old, refreshing), 1);

        assertSame(refreshing, sequence.refreshing());
        assertFalse(sequence.pending(refreshing));
        assertTrue(sequence.pending(queued));
        assertTrue(sequence.pending(scanning));

        var next = SonarRefreshSequence.select(List.of(old, refreshing, queued, scanning), 2);
        assertSame(queued, next.refreshing());
        assertFalse(next.pending(refreshing));
    }

    @Test
    void finishedSweepRemainsVisibleDuringThePingPause() {
        SonarFrame completed = frame(2, true);
        var sequence = SonarRefreshSequence.select(List.of(frame(1, true), completed), 2);
        assertNull(sequence.refreshing());
        assertFalse(sequence.pending(completed));
    }

    @Test
    void firstScanHasNoPreviousImageToErase() {
        SonarFrame scanning = frame(1, false);
        var sequence = SonarRefreshSequence.select(List.of(scanning), Long.MIN_VALUE);
        assertSame(scanning, sequence.refreshing());
        assertFalse(sequence.pending(scanning));
    }

    private static SonarFrame frame(long epoch, boolean completed) {
        return new SonarFrame(epoch, epoch * 10, completed ? epoch * 10 + 5 : 0,
                completed, completed ? 1 : 0.5f, List.of());
    }
}
