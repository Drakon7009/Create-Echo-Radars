package org.rassvet.create_echo_radars.content.sonar;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Keeps the last detected track position until the sweep revisits it. */
public final class MechanicalTrackLatch {
    private MechanicalTrackLatch() {}

    public static <K, V> void clearMissingSweptTracks(Map<K, V> tracks, Set<K> seenInSweep,
                                                       Predicate<V> insideCurrentSweep) {
        tracks.entrySet().removeIf(entry -> !seenInSweep.contains(entry.getKey())
                && insideCurrentSweep.test(entry.getValue()));
    }
}
