package org.rassvet.create_echo_radars.content.sonar;

import org.rassvet.create_echo_radars.config.ServerConfig;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class SonarTraceExecutor {
    private static final Object LOCK = new Object();
    private static ExecutorService executor;
    private static int threadCount;

    private SonarTraceExecutor() {}

    static ExecutorService get() {
        int configured = ServerConfig.traceWorkerThreads();
        synchronized (LOCK) {
            if (executor == null || threadCount != configured) {
                if (executor != null) executor.shutdownNow();
                threadCount = configured;
                executor = Executors.newFixedThreadPool(configured, new SonarThreadFactory());
            }
            return executor;
        }
    }

    static List<Range> splitRanges(int itemCount, int maxRanges) {
        return SonarTracePlan.splitRanges(itemCount, maxRanges).stream()
                .map(range -> new Range(range.startInclusive(), range.endExclusive()))
                .toList();
    }

    record Range(int startInclusive, int endExclusive) {}

    record BatchResult(long epoch, long batchId, List<RayResult> rays) {
        boolean matches(long currentEpoch, long currentBatchId) {
            return SonarTracePlan.isCurrentBatch(currentEpoch, currentBatchId, epoch, batchId);
        }
    }

    record RayResult(int rayIndex, double distance, boolean hit, boolean airBoundary,
                     double incidence, net.minecraft.world.level.block.state.BlockState state) {}

    private static final class SonarThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "CreateEchoRadars-Sonar-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
