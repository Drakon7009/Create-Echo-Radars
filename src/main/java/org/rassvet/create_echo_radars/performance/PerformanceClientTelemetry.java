package org.rassvet.create_echo_radars.performance;

import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;

public final class PerformanceClientTelemetry {
    private static final long REPORT_INTERVAL_NANOS = 1_000_000_000L;
    private static final long[] FRAME_TIMES = new long[4096];
    private static boolean active;
    private static int stageId;
    private static long windowStarted;
    private static long previousFrame;
    private static int frameCount;

    private PerformanceClientTelemetry() {}

    public static void control(boolean enabled, int newStageId, String ignoredLabel) {
        active = enabled;
        stageId = newStageId;
        windowStarted = System.nanoTime();
        previousFrame = 0;
        frameCount = 0;
    }

    public static void onFrame(RenderFrameEvent.Post ignored) {
        if (!active) return;
        long now = System.nanoTime();
        if (previousFrame != 0 && frameCount < FRAME_TIMES.length) {
            FRAME_TIMES[frameCount++] = now - previousFrame;
        }
        previousFrame = now;
        long elapsed = now - windowStarted;
        if (elapsed < REPORT_INTERVAL_NANOS || frameCount == 0) return;

        long[] sorted = Arrays.copyOf(FRAME_TIMES, frameCount);
        Arrays.sort(sorted);
        int p95Index = Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.95) - 1);
        PacketDistributor.sendToServer(new PerformanceNetworking.ClientMetricsPayload(
                stageId, frameCount, elapsed, sorted[p95Index], sorted[sorted.length - 1]));
        windowStarted = now;
        previousFrame = now;
        frameCount = 0;
    }
}
