package org.rassvet.create_echo_radars.content.glass;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarOrientation;
import org.rassvet.create_echo_radars.content.sonar.SonarType;

/**
 * Compact server-to-client state required by the sonar-glass depth shader.
 *
 * <p>Unlike a monitor snapshot, this intentionally contains no scan frames or
 * sonar returns. The glass renders the scanner volume, not measured echoes.</p>
 */
public record SonarGlassState(Vec3 origin, Vec3 forward, Vec3 right, Vec3 up,
                              int range, int horizontalSector, int verticalSector,
                              SonarType sonarType) {
    public SonarGlassState {
        range = Math.max(1, range);
        horizontalSector = Math.max(1, horizontalSector);
        verticalSector = Math.max(1, verticalSector);
        sonarType = sonarType == null ? SonarType.FORWARD_LOOKING_F : sonarType;
        forward = forward.normalize();
        right = right.normalize();
        up = up.normalize();
    }

    public static SonarGlassState from(SonarBlockEntity sonar) {
        SonarOrientation orientation = sonar.displayOrientation();
        return new SonarGlassState(sonar.displayOrigin(),
                orientation.forward(), orientation.right(), orientation.up(),
                sonar.getSonarRange(), sonar.getHorizontalSector(),
                sonar.getVerticalSector(), sonar.getSonarType());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        putVec(tag, "Origin", origin);
        putVec(tag, "Forward", forward);
        putVec(tag, "Right", right);
        putVec(tag, "Up", up);
        tag.putInt("Range", range);
        tag.putInt("HorizontalSector", horizontalSector);
        tag.putInt("VerticalSector", verticalSector);
        tag.putString("SonarType", sonarType.name());
        return tag;
    }

    public static SonarGlassState load(CompoundTag tag) {
        SonarType sonarType = SonarType.FORWARD_LOOKING_F;
        if (tag.contains("SonarType", Tag.TAG_STRING)) {
            try {
                sonarType = SonarType.valueOf(tag.getString("SonarType"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new SonarGlassState(
                getVec(tag, "Origin"),
                getVec(tag, "Forward"),
                getVec(tag, "Right"),
                getVec(tag, "Up"),
                tag.getInt("Range"),
                tag.getInt("HorizontalSector"),
                tag.getInt("VerticalSector"),
                sonarType);
    }

    private static void putVec(CompoundTag parent, String key, Vec3 vector) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", vector.x);
        tag.putDouble("Y", vector.y);
        tag.putDouble("Z", vector.z);
        parent.put(key, tag);
    }

    private static Vec3 getVec(CompoundTag parent, String key) {
        CompoundTag tag = parent.getCompound(key);
        return new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
    }
}
