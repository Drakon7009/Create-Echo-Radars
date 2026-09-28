package org.rassvet.create_echo_radars.content.sonar;

import com.happysg.radar.block.behavior.networks.config.DetectionConfig;

public interface SonarMonitorExtension {
    SonarMonitorSnapshot createEchoRadars$getSonarSnapshot();

    void createEchoRadars$setSonarSnapshot(SonarMonitorSnapshot snapshot);

    boolean createEchoRadars$isSyntheticSnapshot();

    void createEchoRadars$setSyntheticSnapshot(SonarMonitorSnapshot snapshot);

    SonarMonitorDimensions createEchoRadars$getMonitorDimensions();

    void createEchoRadars$setMonitorDimensions(int width, int height);

    DetectionConfig createEchoRadars$getDetectionConfig();
}
