package org.rassvet.create_echo_radars.content.sonar;

public final class SonarPlacement {
    private SonarPlacement() {}

    /** Ceiling mounting selects the inverted pose; Shift deliberately reverses that selection. */
    public static boolean isUpsideDown(int clickedFaceStepY, boolean shiftDown) {
        return (clickedFaceStepY < 0) ^ shiftDown;
    }
}
