package org.rassvet.create_echo_radars.content.sonar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Sector-addressed phosphor buffer for a rotating mechanical sonar display.
 * Pending data is prepared ahead of the sweep and replaces one sector
 * atomically when the sweep crosses it. Advancing to a new scan epoch never
 * clears unrelated sectors.
 */
public final class MechanicalSweepBuffer<K, V> {
    private static final float MINIMUM_ANGULAR_SPEED = 1.0e-5f;

    private final float stepDegrees;
    private final float prefetchArcDegrees;
    private final int sectorCount;
    private final Map<Integer, PendingSector<K, V>> pending = new HashMap<>();
    private final Map<Integer, Map<K, VisibleValue<V>>> visible = new HashMap<>();
    private float lastSweep = Float.NaN;
    private double lastAdvanceTick = Double.NaN;

    public MechanicalSweepBuffer(float stepDegrees, float prefetchArcDegrees) {
        this.stepDegrees = Math.max(0.001f, Math.abs(stepDegrees));
        this.prefetchArcDegrees = Math.max(this.stepDegrees, Math.min(359, prefetchArcDegrees));
        this.sectorCount = Math.max(1, Math.round(360f / this.stepDegrees));
    }

    public void queue(long epoch, float angle, Map<K, V> values) {
        int sector = sectorIndex(angle);
        PendingSector<K, V> existing = pending.get(sector);
        if (existing == null || epoch >= existing.epoch()) {
            pending.put(sector, new PendingSector<>(epoch, sectorAngle(sector), Map.copyOf(values)));
        }
    }

    public void advanceSweep(float sweepAngle, float angularSpeed, double currentTick) {
        float currentSweep = SonarRotation.wrap(sweepAngle);
        if (Float.isNaN(lastSweep)) {
            lastSweep = currentSweep;
            lastAdvanceTick = currentTick;
            activateLateSectors(currentSweep, angularSpeed, currentTick);
            return;
        }

        float previousSweep = lastSweep;
        float travelled = SonarRotation.directedDistance(previousSweep, currentSweep, angularSpeed);
        double elapsedTicks = Double.isNaN(lastAdvanceTick)
                ? 0 : Math.max(0, currentTick - lastAdvanceTick);
        float plausibleTravel = (float) (Math.abs(angularSpeed) * elapsedTicks
                + stepDegrees * 2);
        if (travelled > Math.max(stepDegrees * 2, plausibleTravel)) {
            // A fresh network snapshot may correct the extrapolated sweep a
            // fraction backwards. Directed angular distance represents that
            // as almost 360 degrees; rebasing avoids a false full-screen pass.
            lastSweep = currentSweep;
            lastAdvanceTick = currentTick;
            activateLateSectors(currentSweep, angularSpeed, currentTick);
            return;
        }
        if (travelled > 1.0e-4f) {
            visible.entrySet().removeIf(entry -> SonarRotation.crossedAngle(
                    previousSweep, currentSweep, sectorAngle(entry.getKey()), angularSpeed));
            Iterator<Map.Entry<Integer, PendingSector<K, V>>> iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                PendingSector<K, V> sector = iterator.next().getValue();
                if (!SonarRotation.crossedAngle(previousSweep, currentSweep,
                        sector.angle(), angularSpeed)) continue;
                float targetDistance = SonarRotation.directedDistance(previousSweep,
                        sector.angle(), angularSpeed);
                double ticksSinceCrossing = (travelled - targetDistance)
                        / Math.max(MINIMUM_ANGULAR_SPEED, Math.abs(angularSpeed));
                replaceSector(sector.angle(), sector.values(),
                        currentTick - Math.max(0, ticksSinceCrossing));
                iterator.remove();
            }
        }
        lastSweep = currentSweep;
        lastAdvanceTick = currentTick;
        activateLateSectors(currentSweep, angularSpeed, currentTick);
    }

    public void replaceSector(float angle, Map<K, V> values, double activatedTick) {
        int sector = sectorIndex(angle);
        if (values.isEmpty()) {
            visible.remove(sector);
            return;
        }
        Map<K, VisibleValue<V>> replacement = new HashMap<>();
        values.forEach((key, value) -> replacement.put(key, new VisibleValue<>(value, activatedTick)));
        visible.put(sector, Map.copyOf(replacement));
    }

    public Collection<VisibleValue<V>> visibleValues() {
        List<VisibleValue<V>> result = new ArrayList<>();
        for (Map<K, VisibleValue<V>> sector : visible.values()) result.addAll(sector.values());
        return List.copyOf(result);
    }

    public int visibleSectorCount() {
        return visible.size();
    }

    public boolean hasVisibleSector(float angle) {
        return visible.containsKey(sectorIndex(angle));
    }

    public void reset() {
        pending.clear();
        visible.clear();
        lastSweep = Float.NaN;
        lastAdvanceTick = Double.NaN;
    }

    private void activateLateSectors(float sweepAngle, float angularSpeed, double currentTick) {
        if (Math.abs(angularSpeed) < MINIMUM_ANGULAR_SPEED) return;
        Iterator<Map.Entry<Integer, PendingSector<K, V>>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingSector<K, V> sector = iterator.next().getValue();
            float aheadOfSweep = SonarRotation.directedDistance(
                    sweepAngle, sector.angle(), angularSpeed);
            if (aheadOfSweep <= prefetchArcDegrees + 1.0e-4f) continue;
            float behindSweep = SonarRotation.directedDistance(
                    sector.angle(), sweepAngle, angularSpeed);
            replaceSector(sector.angle(), sector.values(),
                    currentTick - behindSweep / Math.abs(angularSpeed));
            iterator.remove();
        }
    }

    private int sectorIndex(float angle) {
        return Math.floorMod(Math.round(SonarRotation.wrap(angle) / stepDegrees), sectorCount);
    }

    private float sectorAngle(int sector) {
        return SonarRotation.wrap(sector * stepDegrees);
    }

    public record VisibleValue<V>(V value, double activatedTick) {}

    private record PendingSector<K, V>(long epoch, float angle, Map<K, V> values) {}
}
