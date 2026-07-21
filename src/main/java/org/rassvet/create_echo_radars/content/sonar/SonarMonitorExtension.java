package org.rassvet.create_echo_radars.content.sonar;

public interface SonarMonitorExtension {
    SonarMonitorSnapshot createEchoRadars$getSonarSnapshot();

    void createEchoRadars$setSonarSnapshot(SonarMonitorSnapshot snapshot);

    SonarMonitorDimensions createEchoRadars$getMonitorDimensions();

    void createEchoRadars$setMonitorDimensions(int width, int height);
}
