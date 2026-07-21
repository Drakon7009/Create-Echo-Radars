package org.rassvet.create_echo_radars.content.sonar;

public record SonarMonitorDimensions(int width, int height) {
    public SonarMonitorDimensions {
        width = Math.max(1, width);
        height = Math.max(1, height);
    }

    public int max() {
        return Math.max(width, height);
    }

    public int min() {
        return Math.min(width, height);
    }

    public int area() {
        return width * height;
    }
}
