package org.rassvet.create_echo_radars.content.sonar;

import java.util.HashSet;
import java.util.Set;

/** Pure accumulator for one global trace profiling window. */
public final class SonarTraceMetrics {
    private final Set<String> sonars = new HashSet<>();
    private long workerNanos;
    private long batchWallNanos;
    private long maxBatchWallNanos;
    private long batches;
    private long workerTasks;
    private long rays;

    public void add(String sonarId, long workerNanos, long batchWallNanos,
                    int workerTasks, int rays) {
        sonars.add(sonarId);
        this.workerNanos += Math.max(0, workerNanos);
        this.batchWallNanos += Math.max(0, batchWallNanos);
        this.maxBatchWallNanos = Math.max(this.maxBatchWallNanos, Math.max(0, batchWallNanos));
        this.batches++;
        this.workerTasks += Math.max(0, workerTasks);
        this.rays += Math.max(0, rays);
    }

    public Snapshot snapshotAndReset() {
        Snapshot snapshot = new Snapshot(sonars.size(), workerNanos, batchWallNanos,
                maxBatchWallNanos, batches, workerTasks, rays);
        sonars.clear();
        workerNanos = 0;
        batchWallNanos = 0;
        maxBatchWallNanos = 0;
        batches = 0;
        workerTasks = 0;
        rays = 0;
        return snapshot;
    }

    public record Snapshot(int sonars, long workerNanos, long batchWallNanos,
                           long maxBatchWallNanos, long batches, long workerTasks, long rays) {}
}
