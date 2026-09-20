package org.rassvet.create_echo_radars.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PerformanceTestBuildTest {
    @Test
    void normalTestClasspathDoesNotEnablePerformanceCommands() {
        assertFalse(PerformanceTestBuild.enabled());
    }
}
