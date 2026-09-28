package org.rassvet.create_echo_radars.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.rassvet.create_echo_radars.content.sonar.SonarType;
import org.rassvet.create_echo_radars.content.sonar.SideScanGeometry;

import java.util.EnumMap;
import java.util.Map;

public final class ServerConfig {
    public static final int DEFAULT_HORIZONTAL_BEAMS = 51;
    public static final int DEFAULT_VERTICAL_BEAMS = 5;
    public static final int DEFAULT_MAXIMUM_SONAR_RANGE = 96;
    public static final int MINIMUM_SONAR_RANGE_LIMIT = 32;
    public static final int MAXIMUM_SONAR_RANGE_LIMIT = 512;
    public static final ModConfigSpec SPEC;

    private static final Map<SonarType, ModConfigSpec.IntValue> HORIZONTAL_BEAMS =
            new EnumMap<>(SonarType.class);
    private static final Map<SonarType, ModConfigSpec.IntValue> VERTICAL_BEAMS =
            new EnumMap<>(SonarType.class);
    private static final ModConfigSpec.IntValue LEGACY_HORIZONTAL_BEAMS;
    private static final ModConfigSpec.IntValue LEGACY_VERTICAL_BEAMS;
    private static final ModConfigSpec.ConfigValue<Integer> ADDITIONAL_RAYS;
    private static final ModConfigSpec.BooleanValue REFINE_ONLY_UNDETECTED_NEIGHBORS;
    private static final ModConfigSpec.IntValue HIT_REFINEMENT_BACKTRACK_BLOCKS;
    private static final ModConfigSpec.IntValue BLOCKS_PER_TICK;
    private static final ModConfigSpec.IntValue PING_PAUSE_TICKS;
    private static final ModConfigSpec.IntValue SIDE_SCAN_PING_PAUSE_TICKS;
    private static final ModConfigSpec.BooleanValue SIDE_SCAN_MOVEMENT_ONLY;
    private static final ModConfigSpec.IntValue MAX_CHUNK_READS;
    private static final ModConfigSpec.IntValue TRACE_WORKER_THREADS;
    private static final ModConfigSpec.IntValue MAXIMUM_SONAR_RANGE;
    private static final ModConfigSpec.BooleanValue ANGLE_RANGE_REDUCTION;
    private static final ModConfigSpec.BooleanValue ENTITY_OCCLUSION_CHECK;
    private static final ModConfigSpec.BooleanValue TRACE_TIME_PROFILING;
    private static final ModConfigSpec.DoubleValue TORPEDO_GUIDANCE_MAX_SEEK_DEGREES;
    private static final ModConfigSpec.DoubleValue TORPEDO_GUIDANCE_YAW_DEGREES_PER_TICK;
    private static final ModConfigSpec.DoubleValue TORPEDO_GUIDANCE_PITCH_DEGREES_PER_TICK;
    private static final ModConfigSpec.DoubleValue TORPEDO_GUIDANCE_MAX_PITCH_DEGREES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        LEGACY_HORIZONTAL_BEAMS = builder.comment(
                        "Legacy global horizontal beam count. Migrated to per-sonar values when the config screen is saved.")
                .defineInRange("scanning.horizontalBeams", DEFAULT_HORIZONTAL_BEAMS, 11, 121);
        LEGACY_VERTICAL_BEAMS = builder.comment(
                        "Legacy global vertical beam count. Migrated to per-sonar values when the config screen is saved.")
                .defineInRange("scanning.verticalBeams", DEFAULT_VERTICAL_BEAMS, 1, 50);
        for (SonarType type : SonarType.values()) {
            String path = "scanning.beams." + type.registryName();
            HORIZONTAL_BEAMS.put(type, builder.comment(
                            "Horizontal beams emitted by the " + type.registryName() + " sonar.")
                    .defineInRange(path + ".horizontal", defaultHorizontalBeams(type),
                            minimumHorizontalBeams(type), maximumHorizontalBeams(type)));
            VERTICAL_BEAMS.put(type, builder.comment(
                            "Vertical sub-beams emitted by the " + type.registryName() + " sonar.")
                    .defineInRange(path + ".vertical", defaultVerticalBeams(type),
                            minimumVerticalBeams(type), 50));
        }
        ADDITIONAL_RAYS = builder.comment(
                        "Additional rays emitted around a primary hit. Allowed values: 4, 8, or 16.")
                .define("scanning.additionalRays", 4, ServerConfig::validAdditionalRays);
        REFINE_ONLY_UNDETECTED_NEIGHBORS = builder.comment(
                        "Only emit additional rays if no previously detected block is adjacent to the hit.")
                .define("scanning.refineOnlyUndetectedNeighbors", false);
        HIT_REFINEMENT_BACKTRACK_BLOCKS = builder.comment(
                        "Minimum blocks before a hit that refinement rays re-scan; wide beam gaps increase this distance. 0 disables refinement.")
                .defineInRange("scanning.hitRefinementBacktrackBlocks", 5, 0, 16);
        BLOCKS_PER_TICK = builder.comment("Blocks advanced by each sub-ray per server tick.")
                .defineInRange("scanning.blocksPerTick", 10, 1, 16);
        PING_PAUSE_TICKS = builder.comment("Server ticks to wait after a completed ping before starting the next one.")
                .defineInRange("scanning.pingPauseTicks", 20, 0, 200);
        SIDE_SCAN_PING_PAUSE_TICKS = builder.comment(
                        "Server ticks to wait after a completed side-scan ping. Lower values update faster.")
                .defineInRange("scanning.sideScanPingPauseTicks", 20, 0, 200);
        SIDE_SCAN_MOVEMENT_ONLY = builder.comment(
                        "Only start a new side-scan sonar ping after it moves at least half a block.")
                .define("scanning.sideScanMovementOnly", false);
        MAX_CHUNK_READS = builder.comment("Maximum asynchronous unloaded chunk NBT reads per level.")
                .defineInRange("scanning.maxConcurrentChunkReads", 2, 1, 8);
        TRACE_WORKER_THREADS = builder.comment("Worker threads used for sonar ray tracing.")
                .defineInRange("scanning.traceWorkerThreads", defaultTraceWorkerThreads(), 1, 8);
        MAXIMUM_SONAR_RANGE = builder.comment(
                        "Maximum selectable sonar range in blocks before field-of-view reduction is applied.")
                .defineInRange("scanning.maximumSonarRange", DEFAULT_MAXIMUM_SONAR_RANGE,
                        MINIMUM_SONAR_RANGE_LIMIT, MAXIMUM_SONAR_RANGE_LIMIT);
        ANGLE_RANGE_REDUCTION = builder.comment(
                        "Gradually reduce maximum sonar range after the selected angle sum exceeds two thirds of this sonar's maximum angle sum. At maximum angles the range is halved.")
                .define("scanning.angleRangeReduction", true);
        ENTITY_OCCLUSION_CHECK = builder.comment(
                        "Hide entity tracks when a solid block blocks the direct sonar ray. Disabled by default.")
                .define("scanning.entityOcclusionCheck", false);
        TRACE_TIME_PROFILING = builder.comment(
                        "Log aggregate worker and batch trace time for all sonars every 10 seconds.")
                .define("debug.traceTime10s", false);
        TORPEDO_GUIDANCE_MAX_SEEK_DEGREES = builder.comment(
                        "Maximum horizontal seeker angle from the torpedo's heading when it first enters fluid.")
                .defineInRange("compat.cbcMilitarySupplement.torpedoGuidance.maxSeekDegrees",
                        180.0, 1.0, 180.0);
        TORPEDO_GUIDANCE_YAW_DEGREES_PER_TICK = builder.comment(
                        "Maximum horizontal turn applied to a CBC Military Supplement torpedo each tick.")
                .defineInRange("compat.cbcMilitarySupplement.torpedoGuidance.yawDegreesPerTick",
                        3.0, 0.0, 45.0);
        TORPEDO_GUIDANCE_PITCH_DEGREES_PER_TICK = builder.comment(
                        "Maximum depth correction applied to a CBC Military Supplement torpedo each tick.")
                .defineInRange("compat.cbcMilitarySupplement.torpedoGuidance.pitchDegreesPerTick",
                        1.0, 0.0, 10.0);
        TORPEDO_GUIDANCE_MAX_PITCH_DEGREES = builder.comment(
                        "Reference dive or climb angle at one block per tick for guided CBC Military Supplement torpedoes. Slower torpedoes receive a larger angle and faster torpedoes a smaller angle, up to an absolute 45 degree limit.")
                .defineInRange("compat.cbcMilitarySupplement.torpedoGuidance.maxPitchDegrees",
                        10.0, 0.0, 45.0);
        SPEC = builder.build();
    }

    private ServerConfig() {}

    public static int horizontalBeams(SonarType type) {
        int configured = usesLegacyBeamSettings()
                ? LEGACY_HORIZONTAL_BEAMS.get() : HORIZONTAL_BEAMS.get(type).get();
        return odd(clamp(configured, minimumHorizontalBeams(type), maximumHorizontalBeams(type)));
    }

    public static int verticalBeams(SonarType type) {
        int configured = usesLegacyBeamSettings()
                ? LEGACY_VERTICAL_BEAMS.get() : VERTICAL_BEAMS.get(type).get();
        return clamp(configured, minimumVerticalBeams(type), 50);
    }

    public static BeamSettings beamSettings(SonarType type) {
        return new BeamSettings(horizontalBeams(type), verticalBeams(type));
    }

    public static int additionalRays() {
        return normalizeAdditionalRays(ADDITIONAL_RAYS.get());
    }

    public static boolean refineOnlyUndetectedNeighbors() {
        return REFINE_ONLY_UNDETECTED_NEIGHBORS.get();
    }

    public static int hitRefinementBacktrackBlocks() {
        return HIT_REFINEMENT_BACKTRACK_BLOCKS.get();
    }

    public static int blocksPerTick() {
        return BLOCKS_PER_TICK.get();
    }

    public static int pingPauseTicks() {
        return PING_PAUSE_TICKS.get();
    }

    public static int sideScanPingPauseTicks() {
        return SIDE_SCAN_PING_PAUSE_TICKS.get();
    }

    public static boolean sideScanMovementOnly() {
        return SIDE_SCAN_MOVEMENT_ONLY.get();
    }

    public static int maxConcurrentChunkReads() {
        return MAX_CHUNK_READS.get();
    }

    public static int traceWorkerThreads() {
        return TRACE_WORKER_THREADS.get();
    }

    public static int maximumSonarRange() {
        return MAXIMUM_SONAR_RANGE.get();
    }

    public static boolean angleRangeReduction() {
        return ANGLE_RANGE_REDUCTION.get();
    }

    public static boolean entityOcclusionCheck() {
        return ENTITY_OCCLUSION_CHECK.get();
    }

    public static boolean traceTimeProfiling() {
        return TRACE_TIME_PROFILING.get();
    }

    public static double torpedoGuidanceMaxSeekDegrees() {
        return TORPEDO_GUIDANCE_MAX_SEEK_DEGREES.get();
    }

    public static double torpedoGuidanceYawDegreesPerTick() {
        return TORPEDO_GUIDANCE_YAW_DEGREES_PER_TICK.get();
    }

    public static double torpedoGuidancePitchDegreesPerTick() {
        return TORPEDO_GUIDANCE_PITCH_DEGREES_PER_TICK.get();
    }

    public static double torpedoGuidanceMaxPitchDegrees() {
        return TORPEDO_GUIDANCE_MAX_PITCH_DEGREES.get();
    }

    public static void save(int[] horizontalBeams, int[] verticalBeams,
                            int additionalRays, boolean refineOnlyUndetectedNeighbors,
                            int hitRefinementBacktrackBlocks,
                            int blocksPerTick, int pingPauseTicks, int sideScanPingPauseTicks,
                            boolean sideScanMovementOnly,
                            int maxConcurrentChunkReads,
                            int traceWorkerThreads, int maximumSonarRange,
                            boolean angleRangeReduction, boolean entityOcclusionCheck,
                            boolean traceTimeProfiling) {
        SonarType[] types = SonarType.values();
        if (horizontalBeams.length != types.length || verticalBeams.length != types.length) {
            throw new IllegalArgumentException("Expected beam settings for " + types.length + " sonar types");
        }
        for (int i = 0; i < types.length; i++) {
            HORIZONTAL_BEAMS.get(types[i]).set(odd(clamp(horizontalBeams[i],
                    minimumHorizontalBeams(types[i]), maximumHorizontalBeams(types[i]))));
            VERTICAL_BEAMS.get(types[i]).set(clamp(verticalBeams[i],
                    minimumVerticalBeams(types[i]), 50));
        }
        LEGACY_HORIZONTAL_BEAMS.set(DEFAULT_HORIZONTAL_BEAMS);
        LEGACY_VERTICAL_BEAMS.set(DEFAULT_VERTICAL_BEAMS);
        ADDITIONAL_RAYS.set(normalizeAdditionalRays(additionalRays));
        REFINE_ONLY_UNDETECTED_NEIGHBORS.set(refineOnlyUndetectedNeighbors);
        HIT_REFINEMENT_BACKTRACK_BLOCKS.set(clamp(hitRefinementBacktrackBlocks, 0, 16));
        BLOCKS_PER_TICK.set(clamp(blocksPerTick, 1, 16));
        PING_PAUSE_TICKS.set(clamp(pingPauseTicks, 0, 200));
        SIDE_SCAN_PING_PAUSE_TICKS.set(clamp(sideScanPingPauseTicks, 0, 200));
        SIDE_SCAN_MOVEMENT_ONLY.set(sideScanMovementOnly);
        MAX_CHUNK_READS.set(clamp(maxConcurrentChunkReads, 1, 8));
        TRACE_WORKER_THREADS.set(clamp(traceWorkerThreads, 1, 8));
        MAXIMUM_SONAR_RANGE.set(clamp(maximumSonarRange,
                MINIMUM_SONAR_RANGE_LIMIT, MAXIMUM_SONAR_RANGE_LIMIT));
        ANGLE_RANGE_REDUCTION.set(angleRangeReduction);
        ENTITY_OCCLUSION_CHECK.set(entityOcclusionCheck);
        TRACE_TIME_PROFILING.set(traceTimeProfiling);
        SPEC.save();
    }

    public static int defaultTraceWorkerThreads() {
        return Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }

    public static int defaultHorizontalBeams(SonarType type) {
        return type == SonarType.SIDE_SCAN_D ? SideScanGeometry.MAX_HORIZONTAL_BEAMS
                : DEFAULT_HORIZONTAL_BEAMS;
    }

    public static int minimumHorizontalBeams(SonarType type) {
        return type == SonarType.SIDE_SCAN_D ? 1 : 11;
    }

    public static int maximumHorizontalBeams(SonarType type) {
        return type == SonarType.SIDE_SCAN_D ? SideScanGeometry.MAX_HORIZONTAL_BEAMS : 121;
    }

    public static int defaultVerticalBeams(SonarType type) {
        return type == SonarType.SIDE_SCAN_D ? 9 : DEFAULT_VERTICAL_BEAMS;
    }

    public static int minimumVerticalBeams(SonarType type) {
        return type == SonarType.SIDE_SCAN_D ? 9 : 1;
    }

    private static boolean validAdditionalRays(Object value) {
        return value instanceof Integer count && (count == 4 || count == 8 || count == 16);
    }

    private static int normalizeAdditionalRays(int value) {
        if (value <= 4) return 4;
        if (value <= 8) return 8;
        return 16;
    }

    private static boolean usesLegacyBeamSettings() {
        if (LEGACY_HORIZONTAL_BEAMS.get() == DEFAULT_HORIZONTAL_BEAMS
                && LEGACY_VERTICAL_BEAMS.get() == DEFAULT_VERTICAL_BEAMS) {
            return false;
        }
        for (SonarType type : SonarType.values()) {
            if (HORIZONTAL_BEAMS.get(type).get() != defaultHorizontalBeams(type)
                    || VERTICAL_BEAMS.get(type).get() != defaultVerticalBeams(type)) {
                return false;
            }
        }
        return true;
    }

    private static int odd(int value) {
        return (value & 1) == 0 ? value + (value == 121 ? -1 : 1) : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record BeamSettings(int horizontal, int vertical) {}
}
