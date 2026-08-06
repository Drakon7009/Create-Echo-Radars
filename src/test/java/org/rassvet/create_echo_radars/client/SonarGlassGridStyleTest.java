package org.rassvet.create_echo_radars.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarGlassGridStyleTest {
    @Test
    void shaderIdsRemainStable() {
        assertEquals(0, SonarGlassGridStyle.TRIANGLE_MESH.shaderId());
        assertEquals(1, SonarGlassGridStyle.WAVY_LINES.shaderId());
        assertEquals(2, SonarGlassGridStyle.ISOMETRIC_CUBES.shaderId());
    }

    @Test
    void bothDepthShadersImplementEveryGridStyle() throws IOException {
        assertShaderContract(
                "/assets/create_echo_radars/shaders/core/sonar_glass_depth_fallback.fsh");
        assertShaderContract(
                "/assets/create_echo_radars/pinwheel/shaders/program/sonar_glass_depth.fsh");
    }

    @Test
    void vanillaShaderDefaultsToTheLegacyWavyGrid() throws IOException {
        String definition = resourceText(
                "/assets/create_echo_radars/shaders/core/sonar_glass_depth_fallback.json");
        assertTrue(definition.contains("\"name\": \"GridStyle\""));
        assertTrue(definition.contains("\"type\": \"int\""));
        assertTrue(definition.contains("\"values\": [ 1 ]"));
    }

    private static void assertShaderContract(String resource) throws IOException {
        String shader = resourceText(resource);
        assertTrue(shader.contains("uniform int GridStyle;"));
        assertTrue(shader.contains("if (GridStyle == 0) return triangleMesh(worldUv);"));
        assertTrue(shader.contains("if (GridStyle == 2) return isometricCubes(worldUv);"));
        assertTrue(shader.contains("return wavyLines(worldUv);"));
    }

    private static String resourceText(String resource) throws IOException {
        try (InputStream stream = SonarGlassGridStyleTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing test resource " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
