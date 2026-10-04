package org.rassvet.create_echo_radars.content.sonar;

/** Persistent display timing for one scan, owned by its monitor history. */
public final class SonarRevealAnimation {
    private float progress;
    private double lastTick;
    private double fullyRevealedAt = Double.NaN;

    public SonarRevealAnimation(double now) {
        lastTick = now;
    }

    public void advance(float cap, boolean completed, double now, float speed) {
        double elapsed = Math.max(0, now - lastTick);
        lastTick = Math.max(lastTick, now);
        if (fullyRevealed()) return;
        progress = SonarDisplayLayout.advanceRevealProgress(progress,
                completed ? 1 : cap, elapsed, speed);
        if (completed && progress >= 1) {
            progress = 1;
            fullyRevealedAt = now;
        }
    }

    public float progress() {
        return progress;
    }

    public boolean fullyRevealed() {
        return !Double.isNaN(fullyRevealedAt);
    }

    public double age(double now) {
        return fullyRevealed() ? Math.max(0, now - fullyRevealedAt) : 0;
    }
}
