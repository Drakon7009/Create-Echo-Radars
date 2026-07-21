package org.rassvet.create_echo_radars.content.sonar;

import java.util.ArrayList;
import java.util.List;

final class SonarTracePlan {
    private SonarTracePlan() {}

    static List<Range> splitRanges(int itemCount, int maxRanges) {
        if (itemCount <= 0) return List.of();
        int ranges = Math.max(1, Math.min(itemCount, maxRanges));
        List<Range> result = new ArrayList<>(ranges);
        int base = itemCount / ranges;
        int extra = itemCount % ranges;
        int start = 0;
        for (int i = 0; i < ranges; i++) {
            int length = base + (i < extra ? 1 : 0);
            result.add(new Range(start, start + length));
            start += length;
        }
        return result;
    }

    static boolean isCurrentBatch(long currentEpoch, long currentBatchId,
                                  long resultEpoch, long resultBatchId) {
        return currentEpoch == resultEpoch && currentBatchId == resultBatchId;
    }

    record Range(int startInclusive, int endExclusive) {}
}
