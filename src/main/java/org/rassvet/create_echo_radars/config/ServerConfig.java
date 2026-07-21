package org.rassvet.create_echo_radars.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.rassvet.create_echo_radars.content.sonar.SonarType;

import java.util.EnumMap;
import java.util.Map;

public final class ServerConfig {
    public static final int DEFAULT_HORIZONTAL_BEAMS = 51;
    public static final int DEFAULT_VERTICAL_BEAMS = 5;
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
    private static final ModConfigSpec.IntValue MAX_CHUNK_READS;
    private static final ModConfigSpec.IntValue TRACE_WORKER_THREADS;
    private static final ModConfigSpec.BooleanValue ENTITY_OCCLUSION_CHECK;
    private static final ModConfigSpec.BooleanValue TRACE_TIME_PROFILING;

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
                    .defineInRange(path + ".horizontal", DEFAULT_HORIZONTAL_BEAMS, 11, 121));
            VERTICAL_BEAMS.put(type, builder.comment(
                            "Vertical sub-beams emitted by the " + type.registryName() + " sonar.")
                    .defineInRange(path + ".vertical", DEFAULT_VERTICAL_BEAMS, 1, 50));
        }
        ADDITIONAL_RAYS = builder.comment(
                        "Additional rays emitted around a primary hit. Allowed values: 4, 8, or 16.")
                .define("scanning.additionalRays", 4, ServerConfig::validAdditionalRays);
        REFINE_ONLY_UNDETECTED_NEIGHBORS = builder.comment(
                        "Only emit additional rays if no previously detected block is adjacent to the hit.")
                .define("scanning.refineOnlyUndetectedNeighbors", false);
        HIT_REFINEMENT_BACKTRACK_BLOCKS = builder.comment(
                        "Blocks before a hit that local refinement rays re-scan. 0 disables hit refinement.")
                .defineInRange("scanning.hitRefinementBacktrackBlocks", 5, 0, 16);
        BLOCKS_PER_TICK = builder.comment("Blocks advanced by each sub-ray per server tick.")
                .defineInRange("scanning.blocksPerTick", 10, 1, 16);
        PING_PAUSE_TICKS = builder.comment("Server ticks to wait after a completed ping before starting the next one.")
                .defineInRange("scanning.pingPauseTicks", 20, 0, 200);
        MAX_CHUNK_READS = builder.comment("Maximum asynchronous unloaded chunk NBT reads per level.")
                .defineInRange("scanning.maxConcurrentChunkReads", 2, 1, 8);
        TRACE_WORKER_THREADS = builder.comment("Worker threads used for sonar ray tracing.")
                .defineInRange("scanning.traceWorkerThreads", defaultTraceWorkerThreads(), 1, 8);
        ENTITY_OCCLUSION_CHECK = builder.comment(
                        "Hide entity tracks when a solid block blocks the direct sonar ray. Disabled by default.")
                .define("scanning.entityOcclusionCheck", false);
        TRACE_TIME_PROFILING = builder.comment(
                        "Log aggregate worker and batch trace time for all sonars every 10 seconds.")
                .define("debug.traceTime10s", false);
        SPEC = builder.build();
    }

    private ServerConfig() {}

    public static int horizontalBeams(SonarType type) {
        if (usesLegacyBeamSettings()) return odd(LEGACY_HORIZONTAL_BEAMS.get());
        int configured = HORIZONTAL_BEAMS.get(type).get();
        return odd(configured);
    }

    public static int verticalBeams(SonarType type) {
        if (usesLegacyBeamSettings()) return LEGACY_VERTICAL_BEAMS.get();
        return VERTICAL_BEAMS.get(type).get();
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

    public static int maxConcurrentChunkReads() {
        return MAX_CHUNK_READS.get();
    }

    public static int traceWorkerThreads() {
        return TRACE_WORKER_THREADS.get();
    }

    public static boolean entityOcclusionCheck() {
        return ENTITY_OCCLUSION_CHECK.get();
    }

    public static boolean traceTimeProfiling() {
        return TRACE_TIME_PROFILING.get();
    }

    public static void save(int[] horizontalBeams, int[] verticalBeams,
                            int additionalRays, boolean refineOnlyUndetectedNeighbors,
                            int hitRefinementBacktrackBlocks,
                            int blocksPerTick, int pingPauseTicks, int maxConcurrentChunkReads,
                            int traceWorkerThreads, boolean entityOcclusionCheck,
                            boolean traceTimeProfiling) {
        SonarType[] types = SonarType.values();
        if (horizontalBeams.length != types.length || verticalBeams.length != types.length) {
            throw new IllegalArgumentException("Expected beam settings for " + types.length + " sonar types");
        }
        for (int i = 0; i < types.length; i++) {
            HORIZONTAL_BEAMS.get(types[i]).set(odd(clamp(horizontalBeams[i], 11, 121)));
            VERTICAL_BEAMS.get(types[i]).set(clamp(verticalBeams[i], 1, 50));
        }
        LEGACY_HORIZONTAL_BEAMS.set(DEFAULT_HORIZONTAL_BEAMS);
        LEGACY_VERTICAL_BEAMS.set(DEFAULT_VERTICAL_BEAMS);
        ADDITIONAL_RAYS.set(normalizeAdditionalRays(additionalRays));
        REFINE_ONLY_UNDETECTED_NEIGHBORS.set(refineOnlyUndetectedNeighbors);
        HIT_REFINEMENT_BACKTRACK_BLOCKS.set(clamp(hitRefinementBacktrackBlocks, 0, 16));
        BLOCKS_PER_TICK.set(clamp(blocksPerTick, 1, 16));
        PING_PAUSE_TICKS.set(clamp(pingPauseTicks, 0, 200));
        MAX_CHUNK_READS.set(clamp(maxConcurrentChunkReads, 1, 8));
        TRACE_WORKER_THREADS.set(clamp(traceWorkerThreads, 1, 8));
        ENTITY_OCCLUSION_CHECK.set(entityOcclusionCheck);
        TRACE_TIME_PROFILING.set(traceTimeProfiling);
        SPEC.save();
    }

    public static int defaultTraceWorkerThreads() {
        return Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
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
            if (HORIZONTAL_BEAMS.get(type).get() != DEFAULT_HORIZONTAL_BEAMS
                    || VERTICAL_BEAMS.get(type).get() != DEFAULT_VERTICAL_BEAMS) {
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
