package org.rassvet.create_echo_radars.content.sonar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SonarTraceMetricsTest {
    @Test
    void aggregatesAllSonarsAndWorkerTasks() {
        SonarTraceMetrics metrics = new SonarTraceMetrics();
        metrics.add("overworld:10", 1_000, 700, 2, 40);
        metrics.add("nether:10", 2_000, 1_200, 3, 60);
        metrics.add("overworld:10", 500, 300, 1, 10);

        SonarTraceMetrics.Snapshot snapshot = metrics.snapshotAndReset();
        assertEquals(2, snapshot.sonars());
        assertEquals(3_500, snapshot.workerNanos());
        assertEquals(2_200, snapshot.batchWallNanos());
        assertEquals(1_200, snapshot.maxBatchWallNanos());
        assertEquals(3, snapshot.batches());
        assertEquals(6, snapshot.workerTasks());
        assertEquals(110, snapshot.rays());
    }

    @Test
    void snapshotResetsTheNextWindow() {
        SonarTraceMetrics metrics = new SonarTraceMetrics();
        metrics.add("overworld:10", 1_000, 700, 2, 40);
        metrics.snapshotAndReset();

        SonarTraceMetrics.Snapshot empty = metrics.snapshotAndReset();
        assertEquals(0, empty.sonars());
        assertEquals(0, empty.workerNanos());
        assertEquals(0, empty.batches());
        assertEquals(0, empty.rays());
    }
}
