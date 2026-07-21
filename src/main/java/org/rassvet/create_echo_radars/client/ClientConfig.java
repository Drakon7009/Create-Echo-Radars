package org.rassvet.create_echo_radars.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<SonarPalette> PALETTE;
    private static final ModConfigSpec.DoubleValue GAIN;
    private static final ModConfigSpec.DoubleValue SPECKLE;
    private static final ModConfigSpec.BooleanValue POINT_GAPS;
    private static final ModConfigSpec.BooleanValue BLOCK_SIZED_PIXELS;
    private static final ModConfigSpec.IntValue OLD_PIXEL_LIFETIME_TICKS;
    private static final ModConfigSpec.IntValue MECHANICAL_PIXEL_LIFETIME_TICKS;
    private static final ModConfigSpec.BooleanValue CLEAR_OLD_PIXELS_WHEN_REFRESHED;
    private static final ModConfigSpec.EnumValue<SonarDebugRayMode> DEBUG_RAY_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PALETTE = builder
                .comment("Color palette used by sonar monitors.")
                .defineEnum("monitor.palette", SonarPalette.HOT);
        GAIN = builder
                .comment("Display gain applied to acoustic returns.")
                .defineInRange("monitor.gain", 1.0, 0.1, 2.0);
        SPECKLE = builder
                .comment("Strength of multiplicative speckle noise.")
                .defineInRange("monitor.speckle", 0.35, 0.0, 1.0);
        POINT_GAPS = builder
                .comment("Leave a visible gap between adjacent sonar return cells.")
                .define("monitor.pointGaps", false);
        BLOCK_SIZED_PIXELS = builder
                .comment("Render one-block-wide sonar pixels and merge overlapping returns.")
                .define("monitor.blockSizedPixels", true);
        OLD_PIXEL_LIFETIME_TICKS = builder
                .comment("Non-mechanical sonar pixel lifetime in ticks. -1 clears on a new scan; 0 replaces pixels at the sweep front.")
                .defineInRange("monitor.oldPixelLifetimeTicks", 60, -1, 200);
        MECHANICAL_PIXEL_LIFETIME_TICKS = builder
                .comment("Rotating mechanical sonar pixel lifetime in ticks. 0 automatically targets 30% brightness at the next sweep.")
                .defineInRange("monitor.mechanicalPixelLifetimeTicks", 0, 0, 400);
        CLEAR_OLD_PIXELS_WHEN_REFRESHED = builder
                .comment("Immediately clear older sonar frames when the newest screen reveal reaches 100%.")
                .define("monitor.clearOldPixelsWhenRefreshed", true);
        DEBUG_RAY_MODE = builder
                .comment("Rays shown by the sonar debug tracer: OFF, MAIN, BEFORE_BLOCK, or ALL.")
                .defineEnum("debug.rayMode", SonarDebugRayMode.ALL);
        SPEC = builder.build();
    }

    private ClientConfig() {}

    public static SonarPalette palette() {
        return PALETTE.get();
    }

    public static void setPalette(SonarPalette palette) {
        PALETTE.set(palette);
    }

    public static float gain() {
        return GAIN.get().floatValue();
    }

    public static float speckle() {
        return SPECKLE.get().floatValue();
    }

    public static boolean pointGaps() {
        return POINT_GAPS.get();
    }

    public static boolean blockSizedPixels() {
        return BLOCK_SIZED_PIXELS.get();
    }

    public static int oldPixelLifetimeTicks() {
        return OLD_PIXEL_LIFETIME_TICKS.get();
    }

    public static int mechanicalPixelLifetimeTicks() {
        return MECHANICAL_PIXEL_LIFETIME_TICKS.get();
    }

    public static boolean clearOldPixelsWhenRefreshed() {
        return CLEAR_OLD_PIXELS_WHEN_REFRESHED.get();
    }

    public static SonarDebugRayMode debugRayMode() {
        return DEBUG_RAY_MODE.get();
    }

    public static void save(SonarPalette palette, double gain, double speckle, boolean pointGaps,
                            boolean blockSizedPixels,
                            int oldPixelLifetimeTicks, int mechanicalPixelLifetimeTicks,
                            boolean clearOldPixelsWhenRefreshed) {
        PALETTE.set(palette);
        GAIN.set(gain);
        SPECKLE.set(speckle);
        POINT_GAPS.set(pointGaps);
        BLOCK_SIZED_PIXELS.set(blockSizedPixels);
        OLD_PIXEL_LIFETIME_TICKS.set(Math.max(-1, Math.min(200, oldPixelLifetimeTicks)));
        MECHANICAL_PIXEL_LIFETIME_TICKS.set(Math.max(0, Math.min(400, mechanicalPixelLifetimeTicks)));
        CLEAR_OLD_PIXELS_WHEN_REFRESHED.set(clearOldPixelsWhenRefreshed);
        SPEC.save();
    }

    public static void saveDebug(SonarDebugRayMode rayMode) {
        DEBUG_RAY_MODE.set(rayMode);
        SPEC.save();
    }
}
