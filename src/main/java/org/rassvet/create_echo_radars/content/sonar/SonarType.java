package org.rassvet.create_echo_radars.content.sonar;

public enum SonarType {
    ECHO_SOUNDER_A("echo_sounder", 10, 10, 60, 60),
    MECHANICAL_IMAGING_C("mechanical_scanning_sonar", 3, 60, 3, 80),
    SIDE_SCAN_D("side_scan_sonar", 3, 60, 3, 80),
    FORWARD_LOOKING_F("sonar", 120, 20, 120, 80);

    private final String registryName;
    private final int defaultHorizontalAngle;
    private final int defaultVerticalAngle;
    private final int maximumHorizontalAngle;
    private final int maximumVerticalAngle;

    SonarType(String registryName, int defaultHorizontalAngle, int defaultVerticalAngle,
              int maximumHorizontalAngle, int maximumVerticalAngle) {
        this.registryName = registryName;
        this.defaultHorizontalAngle = defaultHorizontalAngle;
        this.defaultVerticalAngle = defaultVerticalAngle;
        this.maximumHorizontalAngle = maximumHorizontalAngle;
        this.maximumVerticalAngle = maximumVerticalAngle;
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

    public int maximumHorizontalAngle() {
        return maximumHorizontalAngle;
    }

    public int minimumHorizontalAngle() {
        return 1;
    }

    public int maximumVerticalAngle() {
        return maximumVerticalAngle;
    }

    public int minimumVerticalAngle() {
        return 1;
    }
}
