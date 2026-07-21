package org.rassvet.create_echo_radars.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarPaletteTest {
    @Test
    void paletteEndpointsAreBoundedAndBecomeBrighter() {
        for (SonarPalette palette : SonarPalette.values()) {
            SonarPalette.Rgb dark = palette.color(-1);
            SonarPalette.Rgb bright = palette.color(2);
            assertBounded(dark);
            assertBounded(bright);
            assertTrue(luminance(bright) > luminance(dark));
        }
    }

    private static void assertBounded(SonarPalette.Rgb color) {
        assertTrue(color.red() >= 0 && color.red() <= 1);
        assertTrue(color.green() >= 0 && color.green() <= 1);
        assertTrue(color.blue() >= 0 && color.blue() <= 1);
    }

    private static float luminance(SonarPalette.Rgb color) {
        return color.red() * 0.2126f + color.green() * 0.7152f + color.blue() * 0.0722f;
    }
}
