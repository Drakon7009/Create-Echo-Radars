package org.rassvet.create_echo_radars.client;

public enum SonarPalette {
    HOT(
            new Rgb(0.08f, 0.005f, 0.002f),
            new Stop(0, 0.16f, 0.005f, 0),
            new Stop(0.42f, 0.78f, 0.02f, 0),
            new Stop(0.72f, 1, 0.42f, 0),
            new Stop(1, 1, 1, 0.78f)),
    GREEN(
            new Rgb(0.002f, 0.035f, 0.012f),
            new Stop(0, 0, 0.12f, 0.025f),
            new Stop(0.45f, 0.01f, 0.55f, 0.08f),
            new Stop(0.78f, 0.25f, 1, 0.35f),
            new Stop(1, 0.82f, 1, 0.84f)),
    BLUE(
            new Rgb(0.002f, 0.018f, 0.055f),
            new Stop(0, 0, 0.08f, 0.22f),
            new Stop(0.45f, 0, 0.35f, 0.82f),
            new Stop(0.78f, 0.1f, 0.88f, 1),
            new Stop(1, 0.86f, 1, 1));

    private final Rgb background;
    private final Stop[] stops;

    SonarPalette(Rgb background, Stop... stops) {
        this.background = background;
        this.stops = stops;
    }

    public Rgb background() {
        return background;
    }

    public Rgb color(float intensity) {
        float value = Math.max(0, Math.min(1, intensity));
        Stop lower = stops[0];
        for (int i = 1; i < stops.length; i++) {
            Stop upper = stops[i];
            if (value <= upper.position) {
                float t = (value - lower.position) / (upper.position - lower.position);
                return new Rgb(
                        lerp(lower.red, upper.red, t),
                        lerp(lower.green, upper.green, t),
                        lerp(lower.blue, upper.blue, t));
            }
            lower = upper;
        }
        return new Rgb(lower.red, lower.green, lower.blue);
    }

    private static float lerp(float from, float to, float value) {
        return from + (to - from) * value;
    }

    public record Rgb(float red, float green, float blue) {
        public int argb(int alpha) {
            int r = Math.round(red * 255);
            int g = Math.round(green * 255);
            int b = Math.round(blue * 255);
            return (alpha & 0xff) << 24 | r << 16 | g << 8 | b;
        }
    }

    private record Stop(float position, float red, float green, float blue) {}
}
