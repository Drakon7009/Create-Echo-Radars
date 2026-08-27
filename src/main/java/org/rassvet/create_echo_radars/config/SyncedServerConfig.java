package org.rassvet.create_echo_radars.config;

import org.rassvet.create_echo_radars.content.sonar.SonarType;

/**
 * The server configuration mirrored to a connected client.  A dedicated server and its client
 * have different {@link ServerConfig} instances, so client rendering must read this snapshot
 * instead of the client's local server TOML.
 */
public final class SyncedServerConfig {
    private static volatile Snapshot current = Snapshot.defaults();

    private SyncedServerConfig() {}

    public static ServerConfig.BeamSettings beamSettings(SonarType type) {
        return new ServerConfig.BeamSettings(current.horizontalBeams[type.ordinal()],
                current.verticalBeams[type.ordinal()]);
    }

    public static int horizontalBeams(SonarType type) {
        return current.horizontalBeams[type.ordinal()];
    }

    public static int verticalBeams(SonarType type) {
        return current.verticalBeams[type.ordinal()];
    }

    public static int additionalRays() {
        return current.additionalRays;
    }

    public static boolean refineOnlyUndetectedNeighbors() {
        return current.refineOnlyUndetectedNeighbors;
    }

    public static int hitRefinementBacktrackBlocks() {
        return current.hitRefinementBacktrackBlocks;
    }

    public static int blocksPerTick() {
        return current.blocksPerTick;
    }

    public static int pingPauseTicks() {
        return current.pingPauseTicks;
    }

    public static int sideScanPingPauseTicks() {
        return current.sideScanPingPauseTicks;
    }

    public static boolean sideScanMovementOnly() {
        return current.sideScanMovementOnly;
    }

    public static int maxConcurrentChunkReads() {
        return current.maxConcurrentChunkReads;
    }

    public static int traceWorkerThreads() {
        return current.traceWorkerThreads;
    }

    public static int maximumSonarRange() {
        return current.maximumSonarRange;
    }

    public static boolean angleRangeReduction() {
        return current.angleRangeReduction;
    }

    public static boolean entityOcclusionCheck() {
        return current.entityOcclusionCheck;
    }

    public static boolean traceTimeProfiling() {
        return current.traceTimeProfiling;
    }

    public static void apply(int[] horizontalBeams, int[] verticalBeams,
                             int additionalRays, boolean refineOnlyUndetectedNeighbors,
                             int hitRefinementBacktrackBlocks, int blocksPerTick, int pingPauseTicks,
                             int sideScanPingPauseTicks,
                             boolean sideScanMovementOnly,
                             int maxConcurrentChunkReads, int traceWorkerThreads,
                             int maximumSonarRange, boolean angleRangeReduction,
                             boolean entityOcclusionCheck, boolean traceTimeProfiling) {
        current = new Snapshot(horizontalBeams, verticalBeams, additionalRays,
                refineOnlyUndetectedNeighbors, hitRefinementBacktrackBlocks, blocksPerTick,
                pingPauseTicks, sideScanPingPauseTicks, sideScanMovementOnly,
                maxConcurrentChunkReads, traceWorkerThreads,
                maximumSonarRange, angleRangeReduction,
                entityOcclusionCheck, traceTimeProfiling);
    }

    public static Snapshot snapshotFromServer() {
        SonarType[] types = SonarType.values();
        int[] horizontalBeams = new int[types.length];
        int[] verticalBeams = new int[types.length];
        for (int i = 0; i < types.length; i++) {
            ServerConfig.BeamSettings beams = ServerConfig.beamSettings(types[i]);
            horizontalBeams[i] = beams.horizontal();
            verticalBeams[i] = beams.vertical();
        }
        return new Snapshot(horizontalBeams, verticalBeams, ServerConfig.additionalRays(),
                ServerConfig.refineOnlyUndetectedNeighbors(), ServerConfig.hitRefinementBacktrackBlocks(),
                ServerConfig.blocksPerTick(), ServerConfig.pingPauseTicks(),
                ServerConfig.sideScanPingPauseTicks(),
                ServerConfig.sideScanMovementOnly(),
                ServerConfig.maxConcurrentChunkReads(), ServerConfig.traceWorkerThreads(),
                ServerConfig.maximumSonarRange(), ServerConfig.angleRangeReduction(),
                ServerConfig.entityOcclusionCheck(), ServerConfig.traceTimeProfiling());
    }

    public record Snapshot(int[] horizontalBeams, int[] verticalBeams,
                           int additionalRays, boolean refineOnlyUndetectedNeighbors,
                           int hitRefinementBacktrackBlocks, int blocksPerTick, int pingPauseTicks,
                           int sideScanPingPauseTicks,
                           boolean sideScanMovementOnly,
                           int maxConcurrentChunkReads, int traceWorkerThreads,
                           int maximumSonarRange, boolean angleRangeReduction,
                           boolean entityOcclusionCheck, boolean traceTimeProfiling) {
        public Snapshot {
            int expected = SonarType.values().length;
            if (horizontalBeams.length != expected || verticalBeams.length != expected) {
                throw new IllegalArgumentException("Expected beam settings for " + expected + " sonar types");
            }
            horizontalBeams = horizontalBeams.clone();
            verticalBeams = verticalBeams.clone();
        }

        public static Snapshot defaults() {
            int count = SonarType.values().length;
            int[] horizontalBeams = new int[count];
            int[] verticalBeams = new int[count];
            for (int i = 0; i < count; i++) {
                SonarType type = SonarType.values()[i];
                horizontalBeams[i] = ServerConfig.defaultHorizontalBeams(type);
                verticalBeams[i] = ServerConfig.defaultVerticalBeams(type);
            }
            return new Snapshot(horizontalBeams, verticalBeams, 4, false, 5, 10, 20, 20, false,
                    2, ServerConfig.defaultTraceWorkerThreads(),
                    ServerConfig.DEFAULT_MAXIMUM_SONAR_RANGE, true, false, false);
        }
    }
}
