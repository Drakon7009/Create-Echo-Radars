package org.rassvet.create_echo_radars.client;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.rassvet.create_echo_radars.ModNetworking;
import org.rassvet.create_echo_radars.config.ServerConfig;
import org.rassvet.create_echo_radars.config.SyncedServerConfig;
import org.rassvet.create_echo_radars.content.sonar.SonarType;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SonarConfigScreen {
    private SonarConfigScreen() {}

    public static Screen create(Screen parent) {
        Values values = new Values();
        boolean operatorAllowed = operatorAllowed();
        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("config.create_echo_radars.title"))
                .category(monitorCategory(values))
                .save(() -> save(values, operatorAllowed));

        if (operatorAllowed) {
            OperatorOptions operatorOptions = new OperatorOptions(values);
            builder.category(presetsCategory(operatorOptions));
            builder.category(sonarBeamsCategory(operatorOptions));
            builder.category(performanceCategory(operatorOptions));
            builder.category(debugCategory(values));
        }
        return builder.build().generateScreen(parent);
    }

    private static ConfigCategory monitorCategory(Values values) {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("config.create_echo_radars.category.monitor"))
                .group(OptionGroup.createBuilder()
                        .name(Component.translatable("config.create_echo_radars.group.monitor.common"))
                        .option(Option.<SonarPalette>createBuilder()
                        .name(Component.translatable("config.create_echo_radars.palette"))
                        .description(description("config.create_echo_radars.palette.description"))
                        .binding(SonarPalette.HOT, () -> values.palette, value -> values.palette = value)
                        .controller(option -> EnumControllerBuilder.create(option)
                                .enumClass(SonarPalette.class)
                                .valueFormatter(value -> Component.translatable(
                                        "config.create_echo_radars.palette." + value.name().toLowerCase())))
                        .build())
                        .option(doubleOption("config.create_echo_radars.gain", 1.0,
                        () -> values.gain, value -> values.gain = value, 0.1, 2.0, 0.05))
                        .option(doubleOption("config.create_echo_radars.speckle", 0.35,
                        () -> values.speckle, value -> values.speckle = value, 0.0, 1.0, 0.05))
                        .option(doubleOption(
                                "config.create_echo_radars.sonar_glass_minimum_distance", 8.0,
                                () -> values.sonarGlassMinimumDistance,
                                value -> values.sonarGlassMinimumDistance = value,
                                0.0, 32.0, 1.0))
                        .option(intOption(
                                "config.create_echo_radars.sonar_glass_activation_distance", 10,
                                () -> values.sonarGlassActivationDistance,
                                value -> values.sonarGlassActivationDistance = value,
                                1, 128, 1))
                        .option(Option.<SonarGlassGridStyle>createBuilder()
                                .name(Component.translatable(
                                        "config.create_echo_radars.sonar_glass_grid_style"))
                                .description(description(
                                        "config.create_echo_radars.sonar_glass_grid_style.description"))
                                .binding(SonarGlassGridStyle.WAVY_LINES,
                                        () -> values.sonarGlassGridStyle,
                                        value -> values.sonarGlassGridStyle = value)
                                .controller(option -> EnumControllerBuilder.create(option)
                                        .enumClass(SonarGlassGridStyle.class)
                                        .valueFormatter(value -> Component.translatable(
                                                "config.create_echo_radars.sonar_glass_grid_style."
                                                        + value.name().toLowerCase())))
                                .build())
                        .option(booleanOption("config.create_echo_radars.point_gaps", false,
                        () -> values.pointGaps, value -> values.pointGaps = value))
                        .option(booleanOption("config.create_echo_radars.block_sized_pixels", false,
                                () -> values.blockSizedPixels, value -> values.blockSizedPixels = value))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.translatable("config.create_echo_radars.group.monitor.regular"))
                        .option(Option.<Integer>createBuilder()
                        .name(Component.translatable("config.create_echo_radars.old_pixel_lifetime"))
                        .description(description("config.create_echo_radars.old_pixel_lifetime.description"))
                        .binding(60, () -> values.oldPixelLifetimeTicks,
                                value -> values.oldPixelLifetimeTicks = value)
                        .controller(option -> IntegerSliderControllerBuilder.create(option)
                                .range(-1, 200).step(1)
                                .valueFormatter(SonarConfigScreen::pixelLifetimeText))
                        .build())
                        .option(booleanOption("config.create_echo_radars.clear_old_pixels_when_refreshed", true,
                        () -> values.clearOldPixelsWhenRefreshed,
                                value -> values.clearOldPixelsWhenRefreshed = value))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.translatable("config.create_echo_radars.group.monitor.mechanical"))
                        .option(Option.<Integer>createBuilder()
                        .name(Component.translatable("config.create_echo_radars.mechanical_pixel_lifetime"))
                        .description(description("config.create_echo_radars.mechanical_pixel_lifetime.description"))
                        .binding(0, () -> values.mechanicalPixelLifetimeTicks,
                                value -> values.mechanicalPixelLifetimeTicks = value)
                        .controller(option -> IntegerSliderControllerBuilder.create(option)
                                .range(0, 400).step(1)
                                .valueFormatter(SonarConfigScreen::mechanicalPixelLifetimeText))
                        .build())
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.translatable("config.create_echo_radars.group.monitor.side_scan"))
                        .option(Option.<SideScanDataPosition>createBuilder()
                                .name(Component.translatable(
                                        "config.create_echo_radars.side_scan_data_position"))
                                .description(description(
                                        "config.create_echo_radars.side_scan_data_position.description"))
                                .binding(SideScanDataPosition.BOTTOM,
                                        () -> values.sideScanDataPosition,
                                        value -> values.sideScanDataPosition = value)
                                .controller(option -> EnumControllerBuilder.create(option)
                                        .enumClass(SideScanDataPosition.class)
                                        .valueFormatter(value -> Component.translatable(
                                                "config.create_echo_radars.side_scan_data_position."
                                                        + value.name().toLowerCase())))
                                .build())
                        .build())
                .build();
    }

    private static ConfigCategory presetsCategory(OperatorOptions options) {
        ConfigCategory.Builder category = ConfigCategory.createBuilder()
                .name(Component.translatable("config.create_echo_radars.category.presets"))
                .tooltip(Component.translatable("config.create_echo_radars.presets.note"));
        for (SonarConfigPreset preset : SonarConfigPreset.values()) {
            String key = "config.create_echo_radars.preset." + preset.name().toLowerCase();
            category.option(ButtonOption.createBuilder()
                    .name(Component.translatable(key))
                    .text(Component.translatable("config.create_echo_radars.preset.apply"))
                    .description(description(key + ".description"))
                    .action(screen -> options.apply(preset))
                    .build());
        }
        return category.build();
    }

    private static ConfigCategory sonarBeamsCategory(OperatorOptions options) {
        ConfigCategory.Builder category = ConfigCategory.createBuilder()
                .name(Component.translatable("config.create_echo_radars.category.sonar_beams"))
                .tooltip(Component.translatable("config.create_echo_radars.operator.note"));
        for (SonarType type : SonarType.values()) {
            category.group(OptionGroup.createBuilder()
                    .name(Component.translatable("block.create_echo_radars." + type.registryName()))
                    .option(options.horizontalBeams.get(type))
                    .option(options.verticalBeams.get(type))
                    .build());
        }
        return category.build();
    }

    private static ConfigCategory performanceCategory(OperatorOptions options) {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("config.create_echo_radars.category.performance"))
                .option(options.additionalRays)
                .option(options.refineOnlyUndetectedNeighbors)
                .option(options.hitRefinementBacktrackBlocks)
                .option(options.blocksPerTick)
                .option(options.pingPauseTicks)
                .option(options.sideScanPingPauseTicks)
                .option(options.sideScanMovementOnly)
                .option(options.maxConcurrentChunkReads)
                .option(options.traceWorkerThreads)
                .option(options.entityOcclusionCheck)
                .build();
    }

    private static ConfigCategory debugCategory(Values values) {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("config.create_echo_radars.category.debug"))
                .option(Option.<SonarDebugRayMode>createBuilder()
                        .name(Component.translatable("config.create_echo_radars.debug.ray_mode"))
                        .description(description("config.create_echo_radars.debug.ray_mode.description"))
                        .binding(SonarDebugRayMode.ALL, () -> values.debugRayMode,
                                value -> values.debugRayMode = value)
                        .controller(option -> EnumControllerBuilder.create(option)
                                .enumClass(SonarDebugRayMode.class)
                                .valueFormatter(value -> Component.translatable(
                                        "config.create_echo_radars.debug.ray_mode."
                                                + value.name().toLowerCase())))
                        .build())
                .option(booleanOption("config.create_echo_radars.debug.trace_time_10s", false,
                        () -> values.traceTimeProfiling, value -> values.traceTimeProfiling = value))
                .build();
    }

    private static Option<Integer> intOption(String key, int defaultValue, Supplier<Integer> getter,
                                             Consumer<Integer> setter, int min, int max, int step) {
        return Option.<Integer>createBuilder()
                .name(Component.translatable(key))
                .description(description(key + ".description"))
                .binding(defaultValue, getter, setter)
                .controller(option -> IntegerSliderControllerBuilder.create(option)
                        .range(min, max).step(step))
                .build();
    }

    private static Option<Double> doubleOption(String key, double defaultValue, Supplier<Double> getter,
                                               Consumer<Double> setter,
                                               double min, double max, double step) {
        return Option.<Double>createBuilder()
                .name(Component.translatable(key))
                .description(description(key + ".description"))
                .binding(defaultValue, getter, setter)
                .controller(option -> DoubleSliderControllerBuilder.create(option)
                        .range(min, max).step(step))
                .build();
    }

    private static Option<Boolean> booleanOption(String key, boolean defaultValue, Supplier<Boolean> getter,
                                                 Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable(key))
                .description(description(key + ".description"))
                .binding(defaultValue, getter, setter)
                .controller(BooleanControllerBuilder::create)
                .build();
    }

    private static OptionDescription description(String key) {
        return OptionDescription.of(Component.translatable(key));
    }

    private static Component pixelLifetimeText(int value) {
        if (value == -1) {
            return Component.translatable("config.create_echo_radars.old_pixel_lifetime.new_scan");
        }
        if (value == 0) {
            return Component.translatable("config.create_echo_radars.old_pixel_lifetime.sweep");
        }
        return Component.translatable("config.create_echo_radars.value.ticks", value);
    }

    private static Component mechanicalPixelLifetimeText(int value) {
        if (value == 0) {
            return Component.translatable("config.create_echo_radars.mechanical_pixel_lifetime.auto");
        }
        return Component.translatable("config.create_echo_radars.value.ticks", value);
    }

    private static boolean operatorAllowed() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    private static void save(Values values, boolean operatorAllowed) {
        ClientConfig.save(values.palette, values.gain, values.speckle,
                values.sonarGlassMinimumDistance,
                values.sonarGlassActivationDistance, values.sonarGlassGridStyle,
                values.pointGaps,
                values.blockSizedPixels,
                values.oldPixelLifetimeTicks, values.mechanicalPixelLifetimeTicks,
                values.clearOldPixelsWhenRefreshed, values.sideScanDataPosition);
        if (!operatorAllowed) return;
        ClientConfig.saveDebug(values.debugRayMode);
        ModNetworking.sendServerConfig(values.horizontalBeams(), values.verticalBeams(),
                values.additionalRays, values.refineOnlyUndetectedNeighbors,
                values.hitRefinementBacktrackBlocks, values.blocksPerTick, values.pingPauseTicks,
                values.sideScanPingPauseTicks,
                values.sideScanMovementOnly,
                values.maxConcurrentChunkReads, values.traceWorkerThreads,
                values.entityOcclusionCheck, values.traceTimeProfiling);
    }

    private static final class Values {
        private SonarPalette palette = ClientConfig.palette();
        private double gain = ClientConfig.gain();
        private double speckle = ClientConfig.speckle();
        private double sonarGlassMinimumDistance =
                ClientConfig.sonarGlassMinimumDistance();
        private int sonarGlassActivationDistance =
                ClientConfig.sonarGlassActivationDistance();
        private SonarGlassGridStyle sonarGlassGridStyle =
                ClientConfig.sonarGlassGridStyle();
        private boolean pointGaps = ClientConfig.pointGaps();
        private boolean blockSizedPixels = ClientConfig.blockSizedPixels();
        private int oldPixelLifetimeTicks = ClientConfig.oldPixelLifetimeTicks();
        private int mechanicalPixelLifetimeTicks = ClientConfig.mechanicalPixelLifetimeTicks();
        private boolean clearOldPixelsWhenRefreshed = ClientConfig.clearOldPixelsWhenRefreshed();
        private SideScanDataPosition sideScanDataPosition = ClientConfig.sideScanDataPosition();
        private SonarDebugRayMode debugRayMode = ClientConfig.debugRayMode();
        private final Map<SonarType, ServerConfig.BeamSettings> beams = new EnumMap<>(SonarType.class);
        private int additionalRays = SyncedServerConfig.additionalRays();
        private boolean refineOnlyUndetectedNeighbors = SyncedServerConfig.refineOnlyUndetectedNeighbors();
        private int hitRefinementBacktrackBlocks = SyncedServerConfig.hitRefinementBacktrackBlocks();
        private int blocksPerTick = SyncedServerConfig.blocksPerTick();
        private int pingPauseTicks = SyncedServerConfig.pingPauseTicks();
        private int sideScanPingPauseTicks = SyncedServerConfig.sideScanPingPauseTicks();
        private boolean sideScanMovementOnly = SyncedServerConfig.sideScanMovementOnly();
        private int maxConcurrentChunkReads = SyncedServerConfig.maxConcurrentChunkReads();
        private int traceWorkerThreads = SyncedServerConfig.traceWorkerThreads();
        private boolean entityOcclusionCheck = SyncedServerConfig.entityOcclusionCheck();
        private boolean traceTimeProfiling = SyncedServerConfig.traceTimeProfiling();

        private Values() {
            for (SonarType type : SonarType.values()) beams.put(type, SyncedServerConfig.beamSettings(type));
        }

        private void setHorizontalBeams(SonarType type, int value) {
            ServerConfig.BeamSettings current = beams.get(type);
            beams.put(type, new ServerConfig.BeamSettings(value, current.vertical()));
        }

        private void setVerticalBeams(SonarType type, int value) {
            ServerConfig.BeamSettings current = beams.get(type);
            beams.put(type, new ServerConfig.BeamSettings(current.horizontal(), value));
        }

        private int[] horizontalBeams() {
            SonarType[] types = SonarType.values();
            int[] result = new int[types.length];
            for (int i = 0; i < types.length; i++) result[i] = beams.get(types[i]).horizontal();
            return result;
        }

        private int[] verticalBeams() {
            SonarType[] types = SonarType.values();
            int[] result = new int[types.length];
            for (int i = 0; i < types.length; i++) result[i] = beams.get(types[i]).vertical();
            return result;
        }
    }

    private static final class OperatorOptions {
        private final Map<SonarType, Option<Integer>> horizontalBeams = new EnumMap<>(SonarType.class);
        private final Map<SonarType, Option<Integer>> verticalBeams = new EnumMap<>(SonarType.class);
        private final Option<AdditionalRayCount> additionalRays;
        private final Option<Boolean> refineOnlyUndetectedNeighbors;
        private final Option<Integer> hitRefinementBacktrackBlocks;
        private final Option<Integer> blocksPerTick;
        private final Option<Integer> pingPauseTicks;
        private final Option<Integer> sideScanPingPauseTicks;
        private final Option<Boolean> sideScanMovementOnly;
        private final Option<Integer> maxConcurrentChunkReads;
        private final Option<Integer> traceWorkerThreads;
        private final Option<Boolean> entityOcclusionCheck;

        private OperatorOptions(Values values) {
            for (SonarType type : SonarType.values()) {
                horizontalBeams.put(type, intOption("config.create_echo_radars.server.horizontal_beams",
                        ServerConfig.defaultHorizontalBeams(type),
                        () -> values.beams.get(type).horizontal(),
                        value -> values.setHorizontalBeams(type, value),
                        ServerConfig.minimumHorizontalBeams(type),
                        ServerConfig.maximumHorizontalBeams(type), 2));
                verticalBeams.put(type, intOption("config.create_echo_radars.server.vertical_beams",
                        ServerConfig.defaultVerticalBeams(type),
                        () -> values.beams.get(type).vertical(),
                        value -> values.setVerticalBeams(type, value),
                        ServerConfig.minimumVerticalBeams(type), 50, 1));
            }
            additionalRays = Option.<AdditionalRayCount>createBuilder()
                    .name(Component.translatable("config.create_echo_radars.server.additional_rays"))
                    .description(description("config.create_echo_radars.server.additional_rays.description"))
                    .binding(AdditionalRayCount.FOUR,
                            () -> AdditionalRayCount.fromValue(values.additionalRays),
                            value -> values.additionalRays = value.value())
                    .controller(option -> EnumControllerBuilder.create(option)
                            .enumClass(AdditionalRayCount.class)
                            .valueFormatter(value -> Component.literal(Integer.toString(value.value()))))
                    .build();
            refineOnlyUndetectedNeighbors = booleanOption(
                    "config.create_echo_radars.server.refine_only_undetected_neighbors", false,
                    () -> values.refineOnlyUndetectedNeighbors,
                    value -> values.refineOnlyUndetectedNeighbors = value);
            hitRefinementBacktrackBlocks = intOption(
                    "config.create_echo_radars.server.hit_refinement_backtrack", 5,
                    () -> values.hitRefinementBacktrackBlocks,
                    value -> values.hitRefinementBacktrackBlocks = value, 0, 16, 1);
            blocksPerTick = intOption("config.create_echo_radars.server.blocks_per_tick", 10,
                    () -> values.blocksPerTick, value -> values.blocksPerTick = value, 1, 16, 1);
            pingPauseTicks = intOption("config.create_echo_radars.server.ping_pause_ticks", 20,
                    () -> values.pingPauseTicks, value -> values.pingPauseTicks = value, 0, 200, 1);
            sideScanPingPauseTicks = intOption(
                    "config.create_echo_radars.server.side_scan_ping_pause_ticks", 20,
                    () -> values.sideScanPingPauseTicks,
                    value -> values.sideScanPingPauseTicks = value, 0, 200, 1);
            sideScanMovementOnly = booleanOption(
                    "config.create_echo_radars.server.side_scan_movement_only", false,
                    () -> values.sideScanMovementOnly,
                    value -> values.sideScanMovementOnly = value);
            maxConcurrentChunkReads = intOption("config.create_echo_radars.server.max_chunk_reads", 2,
                    () -> values.maxConcurrentChunkReads,
                    value -> values.maxConcurrentChunkReads = value, 1, 8, 1);
            traceWorkerThreads = intOption("config.create_echo_radars.server.trace_workers",
                    ServerConfig.defaultTraceWorkerThreads(),
                    () -> values.traceWorkerThreads, value -> values.traceWorkerThreads = value, 1, 8, 1);
            entityOcclusionCheck = booleanOption("config.create_echo_radars.server.entity_occlusion", false,
                    () -> values.entityOcclusionCheck, value -> values.entityOcclusionCheck = value);
        }

        private void apply(SonarConfigPreset preset) {
            for (SonarType type : SonarType.values()) {
                SonarConfigPreset.BeamSettings beams = preset.beams(type);
                horizontalBeams.get(type).requestSet(beams.horizontal());
                verticalBeams.get(type).requestSet(beams.vertical());
            }
            additionalRays.requestSet(AdditionalRayCount.fromValue(preset.additionalRays()));
            refineOnlyUndetectedNeighbors.requestSet(preset.refineOnlyUndetectedNeighbors());
            hitRefinementBacktrackBlocks.requestSet(preset.hitRefinementBacktrackBlocks());
            blocksPerTick.requestSet(preset.blocksPerTick());
            pingPauseTicks.requestSet(preset.pingPauseTicks());
            sideScanPingPauseTicks.requestSet(preset.pingPauseTicks());
            maxConcurrentChunkReads.requestSet(preset.maxConcurrentChunkReads());
            traceWorkerThreads.requestSet(preset.traceWorkerThreads());
            entityOcclusionCheck.requestSet(preset.entityOcclusionCheck());
        }
    }

    private enum AdditionalRayCount {
        FOUR(4),
        EIGHT(8),
        SIXTEEN(16);

        private final int value;

        AdditionalRayCount(int value) {
            this.value = value;
        }

        private int value() {
            return value;
        }

        private static AdditionalRayCount fromValue(int value) {
            return switch (value) {
                case 8 -> EIGHT;
                case 16 -> SIXTEEN;
                default -> FOUR;
            };
        }
    }
}
