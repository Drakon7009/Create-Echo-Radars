package org.rassvet.create_echo_radars.content.sonar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Keeps every recently completed mechanical scan batch long enough for the
 * monitor synchronization to observe it.
 */
public final class MechanicalFrameWindow<T> {
    private final long retentionTicks;
    private final int maximumSize;
    private final Deque<Entry<T>> entries = new ArrayDeque<>();

    public MechanicalFrameWindow(long retentionTicks, int maximumSize) {
        this.retentionTicks = Math.max(1, retentionTicks);
        this.maximumSize = Math.max(1, maximumSize);
    }

    public void add(long completedTick, T value) {
        entries.addLast(new Entry<>(completedTick, value));
        while (entries.size() > maximumSize) entries.removeFirst();
    }

    public boolean prune(long currentTick) {
        boolean changed = false;
        while (!entries.isEmpty()
                && currentTick - entries.peekFirst().completedTick() > retentionTicks) {
            entries.removeFirst();
            changed = true;
        }
        return changed;
    }

    public List<T> values() {
        List<T> values = new ArrayList<>(entries.size());
        for (Entry<T> entry : entries) values.add(entry.value());
        return List.copyOf(values);
    }

    private record Entry<T>(long completedTick, T value) {}
}
