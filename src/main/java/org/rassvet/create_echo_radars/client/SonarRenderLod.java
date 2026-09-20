package org.rassvet.create_echo_radars.client;

/**
 * Distance-aware detail budget for in-world monitor rendering. Large multi-block
 * displays keep their detail farther away because their projected size is larger.
 */
public enum SonarRenderLod {
    FULL(256, 1, true),
    MEDIUM(128, 2, false),
    FAR(64, 4, false),
    DISTANT(32, 8, false);

    private final int rasterResolution;
    private final int sampleStride;
    private final boolean detailedLabels;

    SonarRenderLod(int rasterResolution, int sampleStride, boolean detailedLabels) {
        this.rasterResolution = rasterResolution;
        this.sampleStride = sampleStride;
        this.detailedLabels = detailedLabels;
    }

    public int rasterResolution() {
        return rasterResolution;
    }

    public int sampleStride() {
        return sampleStride;
    }

    public boolean detailedLabels() {
        return detailedLabels;
    }

    public static SonarRenderLod forDistance(double distance, int displaySize) {
        double effectiveDistance = Math.max(0, distance) / Math.max(1, displaySize);
        if (effectiveDistance <= 16) return FULL;
        if (effectiveDistance <= 32) return MEDIUM;
        if (effectiveDistance <= 48) return FAR;
        return DISTANT;
    }
}
