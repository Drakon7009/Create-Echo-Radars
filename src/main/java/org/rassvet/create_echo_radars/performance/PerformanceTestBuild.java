package org.rassvet.create_echo_radars.performance;

public final class PerformanceTestBuild {
    private static final String MARKER = "/META-INF/create_echo_radars_performance_test";
    private static final boolean ENABLED = PerformanceTestBuild.class.getResource(MARKER) != null;

    private PerformanceTestBuild() {}

    public static boolean enabled() {
        return ENABLED;
    }
}
