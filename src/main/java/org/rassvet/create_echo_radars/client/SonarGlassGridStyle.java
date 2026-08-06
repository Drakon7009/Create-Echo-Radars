package org.rassvet.create_echo_radars.client;

/** Client-selected procedural pattern projected through sonar glass. */
public enum SonarGlassGridStyle {
    TRIANGLE_MESH(0),
    WAVY_LINES(1),
    ISOMETRIC_CUBES(2);

    private final int shaderId;

    SonarGlassGridStyle(int shaderId) {
        this.shaderId = shaderId;
    }

    public int shaderId() {
        return shaderId;
    }
}
