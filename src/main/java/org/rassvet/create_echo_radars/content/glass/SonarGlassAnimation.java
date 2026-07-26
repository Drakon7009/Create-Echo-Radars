package org.rassvet.create_echo_radars.content.glass;

public final class SonarGlassAnimation {
    public static final int CYCLE_TICKS = 100;
    public static final int REVEAL_TICKS = 80;
    public static final int OLD_DIM_TICKS = 10;
    public static final int DISCONNECT_FADE_TICKS = 20;
    public static final int PULSE_TICKS = 12;
    public static final float FRONT_WIDTH = 0.08f;

    private SonarGlassAnimation() {}

    public static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) return value < edge0 ? 0 : 1;
        float x = Math.max(0, Math.min(1, (value - edge0) / (edge1 - edge0)));
        return x * x * (3 - 2 * x);
    }

    public static float front(long ageTicks) {
        return smoothstep(0, 1, Math.min(REVEAL_TICKS, Math.max(0, ageTicks)) / (float) REVEAL_TICKS);
    }

    public static float newAlpha(float normalizedDistance, long ageTicks) {
        float front = front(ageTicks);
        if (front >= 1) return 1;
        return 1 - smoothstep(front, Math.min(1, front + FRONT_WIDTH), normalizedDistance);
    }

    public static float oldAlpha(float normalizedDistance, long ageTicks) {
        float dim = 1 - 0.7f * Math.min(1, Math.max(0, ageTicks) / (float) OLD_DIM_TICKS);
        return dim * (1 - newAlpha(normalizedDistance, ageTicks));
    }

    public static float disconnectAlpha(long ageTicks) {
        return 1 - smoothstep(0, DISCONNECT_FADE_TICKS, ageTicks);
    }
}
