package org.rassvet.create_echo_radars.content.sonar;

public enum SonarType {
    ECHO_SOUNDER_A("echo_sounder", 10, 10),
    MECHANICAL_IMAGING_C("mechanical_scanning_sonar", 3, 60),
    SIDE_SCAN_D("side_scan_sonar", 3, 60),
    FORWARD_LOOKING_F("sonar", 120, 20);

    private final String registryName;
    private final int defaultHorizontalAngle;
    private final int defaultVerticalAngle;

    SonarType(String registryName, int defaultHorizontalAngle, int defaultVerticalAngle) {
        this.registryName = registryName;
        this.defaultHorizontalAngle = defaultHorizontalAngle;
        this.defaultVerticalAngle = defaultVerticalAngle;
    }

    public String registryName() {
        return registryName;
    }

    public int defaultHorizontalAngle() {
        return defaultHorizontalAngle;
    }

    public int defaultVerticalAngle() {
        return defaultVerticalAngle;
    }
}
