package org.rassvet.create_echo_radars.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IrisWaterFogPatcherTest {
    private static final String HELPERS = """
            float GetWaterFog(float distance) { return distance; }
            void DoWaterFog(inout vec3 fog) {
                if (isEyeInWater == 1) {
                    fog *= waterFogColor;
                }
            }
            void OtherFog(inout vec3 result) {
                if (isEyeInWater == 1) {
                    result *= 0.5;
                }
            }
            """;
    private static final String SOURCE = "#version 330 compatibility\n" + HELPERS + """
            void main() {
                vec3 color = vec3(1.0);
                float lViewPos1 = 10.0;
                float z0 = 0.5;
                if (isEyeInWater == 1) {
                    if (z0 == 1.0) color.rgb = waterFogColor;
                    vec3 underwaterMult = vec3(0.80, 0.87, 0.97);
                    color *= underwaterMult;
                }
            }
            """;

    @Test
    void complementaryDoesNotInjectMainLocalsIntoIncludedFogHelpers() {
        // The real pack's expanded helper functions produced C1503 for color.
        var result = IrisWaterFogPatcher.patch("composite1", SOURCE);
        assertTrue(result.patched());
        assertTrue(result.source().contains(HELPERS));
        assertEquals(1, result.source().split("if \\(cerVirtualWater\\(\\)\\) color", -1).length - 1);
        assertEquals(result.source(), IrisWaterFogPatcher.patch("composite1", result.source()).source());
    }

    @Test
    void changedComplementaryCompositionUsesFallback() {
        String source = SOURCE.replace("if (z0 == 1.0) color.rgb = waterFogColor;", "color *= 0.5;");
        var result = IrisWaterFogPatcher.patch("composite1", source);
        assertFalse(result.patched());
        assertEquals(source, result.source());
    }

    @Test
    void unrelatedProgramsStayUntouched() {
        assertFalse(IrisWaterFogPatcher.patch("gbuffers_water", SOURCE).patched());
        assertFalse(IrisWaterFogPatcher.patch("composite1", "void main() {}").patched());
    }
}
