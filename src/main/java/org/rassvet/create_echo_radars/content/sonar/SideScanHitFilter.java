package org.rassvet.create_echo_radars.content.sonar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Decides which side-scan primary hits are isolated enough to refine. */
public final class SideScanHitFilter {
    private SideScanHitFilter() {}

    public static boolean[] refinementsAllowed(List<Cell> hits) {
        Map<Cell, Integer> counts = new HashMap<>();
        for (Cell hit : hits) counts.merge(hit, 1, Integer::sum);

        boolean[] allowed = new boolean[hits.size()];
        for (int i = 0; i < hits.size(); i++) {
            Cell hit = hits.get(i);
            allowed[i] = counts.get(hit) == 1
                    && !counts.containsKey(new Cell(hit.x, hit.y - 1, hit.z))
                    && !counts.containsKey(new Cell(hit.x, hit.y + 1, hit.z));
        }
        return allowed;
    }

    public record Cell(int x, int y, int z) {}
}
