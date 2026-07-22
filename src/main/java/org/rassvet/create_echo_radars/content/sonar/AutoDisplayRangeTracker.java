package org.rassvet.create_echo_radars.content.sonar;

import java.util.ArrayDeque;
import java.util.Deque;

/** Keeps automatic monitor scaling stable across several completed pings. */
final class AutoDisplayRangeTracker {
    private final int historySize;
    private final Deque<Integer> detectedRanges = new ArrayDeque<>();
    private int currentRange;

    AutoDisplayRangeTracker(int historySize, int initialRange) {
        this.historySize = Math.max(1, historySize);
        currentRange = Math.max(1, initialRange);
    }

    int update(int detectedRange, int configuredRange) {
        int safeConfiguredRange = Math.max(1, configuredRange);
        if (detectedRange <= 0) return Math.min(currentRange, safeConfiguredRange);

        detectedRanges.addLast(Math.min(detectedRange, safeConfiguredRange));
        while (detectedRanges.size() > historySize) detectedRanges.removeFirst();
        currentRange = 1;
        for (int range : detectedRanges) currentRange = Math.max(currentRange, range);
        return currentRange;
    }
}
