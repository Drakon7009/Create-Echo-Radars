package org.rassvet.create_echo_radars.content.sonar;

import net.minecraft.nbt.CompoundTag;

public record SonarReturn(int beam, int rangeBin, float bearingDegrees,
                          float elevationDegrees, float normalizedDistance, float intensity,
                          float angularResolutionDegrees,
                          float verticalAngularResolutionDegrees) {
    public SonarReturn(int beam, int rangeBin, float bearingDegrees,
                       float normalizedDistance, float intensity) {
        this(beam, rangeBin, bearingDegrees, 0, normalizedDistance, intensity, 0, 0);
    }

    public SonarReturn(int beam, int rangeBin, float bearingDegrees,
                       float normalizedDistance, float intensity, float angularResolutionDegrees) {
        this(beam, rangeBin, bearingDegrees, 0, normalizedDistance, intensity,
                angularResolutionDegrees, angularResolutionDegrees);
    }

    public SonarReturn {
        intensity = Math.max(0, Math.min(1, intensity));
        normalizedDistance = Math.max(0, Math.min(1, normalizedDistance));
        angularResolutionDegrees = Math.max(0, angularResolutionDegrees);
        verticalAngularResolutionDegrees = Math.max(0, verticalAngularResolutionDegrees);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Beam", beam);
        tag.putInt("Bin", rangeBin);
        tag.putFloat("Bearing", bearingDegrees);
        tag.putFloat("Elevation", elevationDegrees);
        tag.putFloat("Distance", normalizedDistance);
        tag.putFloat("Intensity", intensity);
        tag.putFloat("AngularResolution", angularResolutionDegrees);
        tag.putFloat("VerticalAngularResolution", verticalAngularResolutionDegrees);
        return tag;
    }

    public static SonarReturn load(CompoundTag tag) {
        return new SonarReturn(tag.getInt("Beam"), tag.getInt("Bin"),
                tag.getFloat("Bearing"), tag.getFloat("Elevation"),
                tag.getFloat("Distance"), tag.getFloat("Intensity"),
                tag.getFloat("AngularResolution"),
                tag.contains("VerticalAngularResolution")
                        ? tag.getFloat("VerticalAngularResolution") : tag.getFloat("AngularResolution"));
    }
}
