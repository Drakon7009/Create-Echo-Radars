package org.rassvet.create_echo_radars.client;

import org.rassvet.create_echo_radars.content.sonar.SonarType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central preset values. Edit the entries here to tune a preset without touching the YACL screen logic.
 */
public enum SonarConfigPreset {
    PERFORMANCE(
            Map.of(
                    SonarType.ECHO_SOUNDER_A, new BeamSettings(21, 1),
                    SonarType.MECHANICAL_IMAGING_C, new BeamSettings(3, 21),
                    SonarType.SIDE_SCAN_D, new BeamSettings(5, 9),
                    SonarType.FORWARD_LOOKING_F, new BeamSettings(31, 3)),
            4, true, 3, 16, 40, 1, 2, false),
    BALANCED(
            Map.of(
                    SonarType.ECHO_SOUNDER_A, new BeamSettings(51, 5),
                    SonarType.MECHANICAL_IMAGING_C, new BeamSettings(5, 51),
                    SonarType.SIDE_SCAN_D, new BeamSettings(5, 17),
                    SonarType.FORWARD_LOOKING_F, new BeamSettings(51, 5)),
            8, false, 5, 10, 20, 2, 4, false),
    QUALITY(
            Map.of(
                    SonarType.ECHO_SOUNDER_A, new BeamSettings(81, 9),
                    SonarType.MECHANICAL_IMAGING_C, new BeamSettings(9, 81),
                    SonarType.SIDE_SCAN_D, new BeamSettings(5, 33),
                    SonarType.FORWARD_LOOKING_F, new BeamSettings(101, 9)),
            16, false, 8, 6, 10, 4, 8, true);

    private final Map<SonarType, BeamSettings> beams;
    private final int additionalRays;
    private final boolean refineOnlyUndetectedNeighbors;
    private final int hitRefinementBacktrackBlocks;
    private final int blocksPerTick;
    private final int pingPauseTicks;
    private final int maxConcurrentChunkReads;
    private final int traceWorkerThreads;
    private final boolean entityOcclusionCheck;

    SonarConfigPreset(Map<SonarType, BeamSettings> beams,
                      int additionalRays, boolean refineOnlyUndetectedNeighbors,
                      int hitRefinementBacktrackBlocks, int blocksPerTick,
                      int pingPauseTicks, int maxConcurrentChunkReads,
                      int traceWorkerThreads, boolean entityOcclusionCheck) {
        this.beams = new EnumMap<>(beams);
        this.additionalRays = additionalRays;
        this.refineOnlyUndetectedNeighbors = refineOnlyUndetectedNeighbors;
        this.hitRefinementBacktrackBlocks = hitRefinementBacktrackBlocks;
        this.blocksPerTick = blocksPerTick;
        this.pingPauseTicks = pingPauseTicks;
        this.maxConcurrentChunkReads = maxConcurrentChunkReads;
        this.traceWorkerThreads = traceWorkerThreads;
        this.entityOcclusionCheck = entityOcclusionCheck;
    }

    public BeamSettings beams(SonarType type) {
        return beams.get(type);
    }

    public int additionalRays() {
        return additionalRays;
    }

    public boolean refineOnlyUndetectedNeighbors() {
        return refineOnlyUndetectedNeighbors;
    }

    public int hitRefinementBacktrackBlocks() {
        return hitRefinementBacktrackBlocks;
    }

    public int blocksPerTick() {
        return blocksPerTick;
    }

    public int pingPauseTicks() {
        return pingPauseTicks;
    }

    public int maxConcurrentChunkReads() {
        return maxConcurrentChunkReads;
    }

    public int traceWorkerThreads() {
        return traceWorkerThreads;
    }

    public boolean entityOcclusionCheck() {
        return entityOcclusionCheck;
    }

    public record BeamSettings(int horizontal, int vertical) {}
}
