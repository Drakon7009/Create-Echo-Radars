package org.rassvet.create_echo_radars.content.sonar;

import net.minecraft.server.MinecraftServer;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.config.ServerConfig;

import java.util.Map;
import java.util.WeakHashMap;

final class SonarTraceProfiler {
    private static final long WINDOW_NANOS = 10_000_000_000L;
    private static final Object LOCK = new Object();
    private static final Map<MinecraftServer, ServerWindow> WINDOWS = new WeakHashMap<>();

    private SonarTraceProfiler() {}

    static void record(MinecraftServer server, String sonarId, long workerNanos,
                       long batchWallNanos, int workerTasks, int rays) {
        if (!ServerConfig.traceTimeProfiling()) return;
        synchronized (LOCK) {
            long now = System.nanoTime();
            ServerWindow window = WINDOWS.computeIfAbsent(server, ignored -> new ServerWindow(now));
            if (!window.enabled) {
                window.enabled = true;
                window.reset(now);
            }
            window.metrics.add(sonarId, workerNanos, batchWallNanos, workerTasks, rays);
        }
    }

    static void tick(MinecraftServer server) {
        SonarTraceMetrics.Snapshot snapshot = null;
        synchronized (LOCK) {
            long now = System.nanoTime();
            ServerWindow window = WINDOWS.computeIfAbsent(server, ignored -> new ServerWindow(now));
            if (!ServerConfig.traceTimeProfiling()) {
                if (window.enabled) window.reset(now);
                window.enabled = false;
                return;
            }
            if (!window.enabled) {
                window.enabled = true;
                window.reset(now);
                return;
            }
            if (now - window.startedNanos < WINDOW_NANOS) return;
            snapshot = window.metrics.snapshotAndReset();
            window.startedNanos = now;
        }
        CreateEchoRadars.LOGGER.info(
                "[sonar-trace-profile] all sonars last 10s: workerTotal={} ms, batchWallTotal={} ms, maxBatch={} ms, sonars={}, batches={}, workerTasks={}, rays={}",
                millis(snapshot.workerNanos()), millis(snapshot.batchWallNanos()),
                millis(snapshot.maxBatchWallNanos()), snapshot.sonars(), snapshot.batches(),
                snapshot.workerTasks(), snapshot.rays());
    }

    private static double millis(long nanos) {
        return Math.round(nanos / 1_000.0) / 1000.0;
    }

    private static final class ServerWindow {
        private final SonarTraceMetrics metrics = new SonarTraceMetrics();
        private long startedNanos;
        private boolean enabled;

        private ServerWindow(long startedNanos) {
            this.startedNanos = startedNanos;
        }

        private void reset(long now) {
            metrics.snapshotAndReset();
            startedNanos = now;
        }
    }
}
