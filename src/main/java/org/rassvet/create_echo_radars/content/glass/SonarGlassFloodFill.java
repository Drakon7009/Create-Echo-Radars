package org.rassvet.create_echo_radars.content.glass;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

final class SonarGlassFloodFill {
    private SonarGlassFloodFill() {}

    static Result find(long start, LongPredicate included, LongFunction<long[]> neighbors, int limit) {
        if (!included.test(start)) return new Result(Set.of(), false);
        ArrayDeque<Long> queue = new ArrayDeque<>();
        LinkedHashSet<Long> found = new LinkedHashSet<>();
        queue.add(start);
        found.add(start);
        while (!queue.isEmpty()) {
            long current = queue.removeFirst();
            for (long next : neighbors.apply(current)) {
                if (found.contains(next) || !included.test(next)) continue;
                if (found.size() == limit) return new Result(Set.copyOf(found), true);
                found.add(next);
                queue.addLast(next);
            }
        }
        return new Result(Set.copyOf(found), false);
    }

    record Result(Set<Long> nodes, boolean overflow) {}
}
