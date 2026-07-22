package org.rassvet.create_echo_radars.client;

import org.junit.jupiter.api.Test;
import org.rassvet.create_echo_radars.content.sonar.SonarType;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarConfigPresetTest {
    @Test
    void everyPresetContainsValidSettingsForEverySonar() {
        for (SonarConfigPreset preset : SonarConfigPreset.values()) {
            for (SonarType type : SonarType.values()) {
                SonarConfigPreset.BeamSettings beams = preset.beams(type);
                assertNotNull(beams, () -> preset + " has no settings for " + type);
                int minimumHorizontal = type == SonarType.SIDE_SCAN_D ? 1 : 11;
                int maximumHorizontal = type == SonarType.SIDE_SCAN_D ? 5 : 121;
                int minimumVertical = type == SonarType.SIDE_SCAN_D ? 9 : 1;
                assertTrue(beams.horizontal() >= minimumHorizontal
                        && beams.horizontal() <= maximumHorizontal);
                assertTrue((beams.horizontal() & 1) == 1, "Horizontal beam count must be odd");
                assertTrue(beams.vertical() >= minimumVertical && beams.vertical() <= 50);
            }
            assertTrue(preset.additionalRays() == 4
                    || preset.additionalRays() == 8
                    || preset.additionalRays() == 16);
            assertTrue(preset.hitRefinementBacktrackBlocks() >= 0
                    && preset.hitRefinementBacktrackBlocks() <= 16);
            assertTrue(preset.blocksPerTick() >= 1 && preset.blocksPerTick() <= 16);
            assertTrue(preset.pingPauseTicks() >= 0 && preset.pingPauseTicks() <= 200);
            assertTrue(preset.maxConcurrentChunkReads() >= 1 && preset.maxConcurrentChunkReads() <= 8);
            assertTrue(preset.traceWorkerThreads() >= 1 && preset.traceWorkerThreads() <= 8);
        }
    }
}
