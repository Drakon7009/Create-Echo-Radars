package org.rassvet.create_echo_radars.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<SonarPalette> PALETTE;
    private static final ModConfigSpec.DoubleValue GAIN;
    private static final ModConfigSpec.DoubleValue SPECKLE;
    private static final ModConfigSpec.DoubleValue SONAR_GLASS_MINIMUM_DISTANCE;
    private static final ModConfigSpec.IntValue SONAR_GLASS_ACTIVATION_DISTANCE;
    private static final ModConfigSpec.EnumValue<SonarGlassGridStyle> SONAR_GLASS_GRID_STYLE;
    private static final ModConfigSpec.BooleanValue POINT_GAPS;
    private static final ModConfigSpec.BooleanValue BLOCK_SIZED_PIXELS;
    private static final ModConfigSpec.IntValue OLD_PIXEL_LIFETIME_TICKS;
    private static final ModConfigSpec.IntValue MECHANICAL_PIXEL_LIFETIME_TICKS;
    private static final ModConfigSpec.BooleanValue CLEAR_OLD_PIXELS_WHEN_REFRESHED;
    private static final ModConfigSpec.EnumValue<SideScanDataPosition> SIDE_SCAN_DATA_POSITION;
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
        SONAR_GLASS_MINIMUM_DISTANCE = builder
                .comment("Minimum distance in blocks from the camera at which the sonar-glass grid can be rendered.")
                .defineInRange("sonarGlass.minimumRenderDistance", 8.0, 0.0, 32.0);
        SONAR_GLASS_ACTIVATION_DISTANCE = builder
                .comment("Maximum distance in blocks from the camera to connected sonar glass at which its display is active.")
                .defineInRange("sonarGlass.activationDistance", 10, 1, 128);
        SONAR_GLASS_GRID_STYLE = builder
                .comment("Procedural grid projected through active sonar glass.")
                .defineEnum("sonarGlass.gridStyle", SonarGlassGridStyle.WAVY_LINES);
        POINT_GAPS = builder
                .comment("Leave a visible gap between adjacent sonar return cells.")
                .define("monitor.pointGaps", false);
        BLOCK_SIZED_PIXELS = builder
                .comment("Render one-block-wide sonar pixels and merge overlapping returns.")
                .define("monitor.blockSizedPixels", true);
        OLD_PIXEL_LIFETIME_TICKS = builder
                .comment("Non-mechanical sonar fade time in ticks. Forward sonar retains a 30% afterimage ahead of the next sweep and clears old pixels behind it. For scrolling history, -1 clears on a new scan and 0 replaces at the sweep front.")
                .defineInRange("monitor.oldPixelLifetimeTicks", 60, -1, 200);
        MECHANICAL_PIXEL_LIFETIME_TICKS = builder
                .comment("Rotating mechanical sonar pixel lifetime in ticks. 0 automatically targets 30% brightness at the next sweep.")
                .defineInRange("monitor.mechanicalPixelLifetimeTicks", 0, 0, 400);
        CLEAR_OLD_PIXELS_WHEN_REFRESHED = builder
                .comment("Immediately clear older sonar frames when the newest screen reveal reaches 100%.")
                .define("monitor.clearOldPixelsWhenRefreshed", true);
        SIDE_SCAN_DATA_POSITION = builder
                .comment("Screen edge where new side-scan history frames appear.")
                .defineEnum("monitor.sideScanDataPosition", SideScanDataPosition.BOTTOM);
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

    public static float sonarGlassMinimumDistance() {
        return SONAR_GLASS_MINIMUM_DISTANCE.get().floatValue();
    }

    public static int sonarGlassActivationDistance() {
        return SONAR_GLASS_ACTIVATION_DISTANCE.get();
    }

    public static SonarGlassGridStyle sonarGlassGridStyle() {
        return SONAR_GLASS_GRID_STYLE.get();
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

    public static SideScanDataPosition sideScanDataPosition() {
        return SIDE_SCAN_DATA_POSITION.get();
    }

    public static SonarDebugRayMode debugRayMode() {
        return DEBUG_RAY_MODE.get();
    }

    public static void save(SonarPalette palette, double gain, double speckle,
                            double sonarGlassMinimumDistance,
                            int sonarGlassActivationDistance,
                            SonarGlassGridStyle sonarGlassGridStyle,
                            boolean pointGaps,
                            boolean blockSizedPixels,
                            int oldPixelLifetimeTicks, int mechanicalPixelLifetimeTicks,
                            boolean clearOldPixelsWhenRefreshed,
                            SideScanDataPosition sideScanDataPosition) {
        PALETTE.set(palette);
        GAIN.set(gain);
        SPECKLE.set(speckle);
        SONAR_GLASS_MINIMUM_DISTANCE.set(Math.max(0.0,
                Math.min(32.0, sonarGlassMinimumDistance)));
        SONAR_GLASS_ACTIVATION_DISTANCE.set(Math.max(1,
                Math.min(128, sonarGlassActivationDistance)));
        SONAR_GLASS_GRID_STYLE.set(sonarGlassGridStyle);
        POINT_GAPS.set(pointGaps);
        BLOCK_SIZED_PIXELS.set(blockSizedPixels);
        OLD_PIXEL_LIFETIME_TICKS.set(Math.max(-1, Math.min(200, oldPixelLifetimeTicks)));
        MECHANICAL_PIXEL_LIFETIME_TICKS.set(Math.max(0, Math.min(400, mechanicalPixelLifetimeTicks)));
        CLEAR_OLD_PIXELS_WHEN_REFRESHED.set(clearOldPixelsWhenRefreshed);
        SIDE_SCAN_DATA_POSITION.set(sideScanDataPosition);
        SPEC.save();
    }

    public static void saveDebug(SonarDebugRayMode rayMode) {
        DEBUG_RAY_MODE.set(rayMode);
        SPEC.save();
    }
}
