package org.rassvet.create_echo_radars.content.sonar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public record SonarFrame(long epoch, long startedTick, long completedTick,
                         boolean complete, float revealProgress,
                         List<SonarReturn> returns, List<Float> scanAngles) {
    private static final String RETURNS_PACKED = "ReturnsPacked";
    private static final String RETURN_STRIDE_KEY = "ReturnStride";
    private static final int LEGACY_RETURN_STRIDE = 6;
    private static final int RETURN_STRIDE = 8;

    public SonarFrame {
        revealProgress = Math.max(0, Math.min(1, revealProgress));
        returns = List.copyOf(returns);
        scanAngles = List.copyOf(scanAngles);
    }

    public SonarFrame(long epoch, long startedTick, long completedTick,
                      boolean complete, float revealProgress,
                      List<SonarReturn> returns) {
        this(epoch, startedTick, completedTick, complete, revealProgress, returns, List.of());
    }

    public SonarFrame(long epoch, long startedTick, long completedTick,
                      boolean complete, List<SonarReturn> returns) {
        this(epoch, startedTick, completedTick, complete,
                complete || completedTick > 0 ? 1 : 0, returns, List.of());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Epoch", epoch);
        tag.putLong("Started", startedTick);
        tag.putLong("Completed", completedTick);
        tag.putBoolean("Complete", complete);
        tag.putFloat("Reveal", revealProgress);
        int[] packed = new int[returns.size() * RETURN_STRIDE];
        int offset = 0;
        for (SonarReturn sonarReturn : returns) {
            packed[offset++] = sonarReturn.beam();
            packed[offset++] = sonarReturn.rangeBin();
            packed[offset++] = Float.floatToRawIntBits(sonarReturn.bearingDegrees());
            packed[offset++] = Float.floatToRawIntBits(sonarReturn.elevationDegrees());
            packed[offset++] = Float.floatToRawIntBits(sonarReturn.normalizedDistance());
            packed[offset++] = Float.floatToRawIntBits(sonarReturn.intensity());
            packed[offset++] = Float.floatToRawIntBits(sonarReturn.angularResolutionDegrees());
            packed[offset++] = Float.floatToRawIntBits(sonarReturn.verticalAngularResolutionDegrees());
        }
        tag.putIntArray(RETURNS_PACKED, packed);
        tag.putInt(RETURN_STRIDE_KEY, RETURN_STRIDE);
        int[] packedAngles = new int[scanAngles.size()];
        for (int index = 0; index < scanAngles.size(); index++) {
            packedAngles[index] = Math.round(scanAngles.get(index) * 1000);
        }
        tag.putIntArray("ScanAngles", packedAngles);
        return tag;
    }

    public static SonarFrame load(CompoundTag tag) {
        List<SonarReturn> returns = new ArrayList<>();
        int[] packed = tag.contains(RETURNS_PACKED, Tag.TAG_INT_ARRAY)
                ? tag.getIntArray(RETURNS_PACKED) : null;
        int stride = tag.contains(RETURN_STRIDE_KEY, Tag.TAG_INT)
                ? tag.getInt(RETURN_STRIDE_KEY) : LEGACY_RETURN_STRIDE;
        if (packed != null && stride == RETURN_STRIDE && packed.length % RETURN_STRIDE == 0) {
            for (int offset = 0; offset < packed.length; offset += RETURN_STRIDE) {
                returns.add(new SonarReturn(packed[offset], packed[offset + 1],
                        Float.intBitsToFloat(packed[offset + 2]),
                        Float.intBitsToFloat(packed[offset + 3]),
                        Float.intBitsToFloat(packed[offset + 4]),
                        Float.intBitsToFloat(packed[offset + 5]),
                        Float.intBitsToFloat(packed[offset + 6]),
                        Float.intBitsToFloat(packed[offset + 7])));
            }
        } else if (packed != null && packed.length % LEGACY_RETURN_STRIDE == 0) {
            for (int offset = 0; offset < packed.length; offset += LEGACY_RETURN_STRIDE) {
                returns.add(new SonarReturn(packed[offset], packed[offset + 1],
                        Float.intBitsToFloat(packed[offset + 2]),
                        Float.intBitsToFloat(packed[offset + 3]),
                        Float.intBitsToFloat(packed[offset + 4]),
                        Float.intBitsToFloat(packed[offset + 5])));
            }
        } else {
            ListTag list = tag.getList("Returns", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) returns.add(SonarReturn.load(list.getCompound(i)));
        }
        float reveal = tag.contains("Reveal", Tag.TAG_FLOAT)
                ? tag.getFloat("Reveal") : (tag.getLong("Completed") > 0 ? 1 : 0);
        int[] packedAngles = tag.contains("ScanAngles", Tag.TAG_INT_ARRAY)
                ? tag.getIntArray("ScanAngles") : new int[0];
        List<Float> scanAngles = new ArrayList<>(packedAngles.length);
        for (int packedAngle : packedAngles) scanAngles.add(packedAngle / 1000f);
        return new SonarFrame(tag.getLong("Epoch"), tag.getLong("Started"),
                tag.getLong("Completed"), tag.getBoolean("Complete"), reveal, returns, scanAngles);
    }
}
