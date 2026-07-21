package org.rassvet.create_echo_radars.content.sonar;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Caches one entity-visibility ray by the block cells containing its endpoints.
 * The age limit keeps block placement/removal from leaving a stale result forever.
 */
public final class SonarEntityVisibilityCache {
    private final long maximumAgeTicks;
    private final Map<String, Entry> entries = new HashMap<>();

    public SonarEntityVisibilityCache(long maximumAgeTicks) {
        this.maximumAgeTicks = Math.max(1, maximumAgeTicks);
    }

    public boolean resolve(String entityId, Cell sonarCell, Cell entityCell, long gameTime,
                           BooleanSupplier visibilityRay) {
        Entry cached = entries.get(entityId);
        if (cached != null && cached.sonarCell.equals(sonarCell)
                && cached.entityCell.equals(entityCell)
                && gameTime - cached.checkedTick < maximumAgeTicks) {
            entries.put(entityId, cached.accessedAt(gameTime));
            return cached.visible;
        }
        boolean visible = visibilityRay.getAsBoolean();
        entries.put(entityId, new Entry(sonarCell, entityCell, visible, gameTime, gameTime));
        return visible;
    }

    public void prune(long gameTime) {
        entries.entrySet().removeIf(entry ->
                gameTime - entry.getValue().lastAccessTick >= maximumAgeTicks * 2);
    }

    public void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    public record Cell(int x, int y, int z) {}

    private record Entry(Cell sonarCell, Cell entityCell, boolean visible,
                         long checkedTick, long lastAccessTick) {
        private Entry accessedAt(long gameTime) {
            return new Entry(sonarCell, entityCell, visible, checkedTick, gameTime);
        }
    }
}
