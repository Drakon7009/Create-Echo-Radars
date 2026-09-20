package org.rassvet.create_echo_radars.content.summator;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Resolves the short-lived position moves emitted while Sable relocates blocks. */
public final class SummatorLinkRemap {
    private SummatorLinkRemap() {}

    public record Move(long destination, long movedAt, long expiresAt) {}

    public static long resolve(long start, Map<Long, Move> moves, long now) {
        long current = start;
        long previousMoveTime = Long.MIN_VALUE;
        Set<Long> visited = new HashSet<>();
        while (visited.add(current)) {
            Move move = moves.get(current);
            if (move == null || move.expiresAt() < now
                    || move.movedAt() <= previousMoveTime) break;
            current = move.destination();
            previousMoveTime = move.movedAt();
        }
        return current;
    }
}
