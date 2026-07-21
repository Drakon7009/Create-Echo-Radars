package org.rassvet.create_echo_radars.content.sonar;

import java.util.HashSet;
import java.util.Set;

public final class NearbyBlockTracker {
    private final Set<Cell> detected = new HashSet<>();

    public boolean hasNearbyAndRecord(int x, int y, int z) {
        boolean nearby = false;
        for (int dx = -1; dx <= 1 && !nearby; dx++) {
            for (int dy = -1; dy <= 1 && !nearby; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (detected.contains(new Cell(x + dx, y + dy, z + dz))) {
                        nearby = true;
                        break;
                    }
                }
            }
        }
        detected.add(new Cell(x, y, z));
        return nearby;
    }

    public void clear() {
        detected.clear();
    }

    private record Cell(int x, int y, int z) {}
}
