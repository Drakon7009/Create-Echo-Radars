package org.rassvet.create_echo_radars.content.sonar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public record SonarMonitorSnapshot(List<SonarFrame> frames, int range, int displayRange,
                                   int horizontalSector, int verticalSector, int horizontalBeams,
                                   SonarType sonarType, float scanAngle, float scanAngularSpeed,
                                   long scanAngleTick,
                                   boolean autoHeight, boolean mirrorDisplay,
                                   Vec3 origin, Vec3 forward, Vec3 right, Vec3 up) {
    public SonarMonitorSnapshot {
        frames = List.copyOf(frames);
        range = Math.max(1, range);
        displayRange = Math.max(1, Math.min(range, displayRange));
        horizontalSector = Math.max(1, horizontalSector);
        verticalSector = Math.max(1, verticalSector);
        horizontalBeams = Math.max(1, horizontalBeams);
        sonarType = sonarType == null ? SonarType.FORWARD_LOOKING_F : sonarType;
        forward = forward.normalize();
        right = right.normalize();
        up = up.normalize();
    }

    public SonarMonitorSnapshot(List<SonarFrame> frames, int range, int displayRange,
                                int horizontalSector, int verticalSector, int horizontalBeams,
                                SonarType sonarType, float scanAngle, float scanAngularSpeed,
                                long scanAngleTick, boolean autoHeight,
                                Vec3 origin, Vec3 forward, Vec3 right, Vec3 up) {
        this(frames, range, displayRange, horizontalSector, verticalSector, horizontalBeams,
                sonarType, scanAngle, scanAngularSpeed, scanAngleTick, autoHeight, false,
                origin, forward, right, up);
    }

    public SonarMonitorSnapshot(List<SonarFrame> frames, int range,
                                int horizontalSector, Vec3 origin, Vec3 forward,
                                Vec3 right, Vec3 up) {
        this(frames, range, range, horizontalSector, 20, inferHorizontalBeams(frames),
                SonarType.FORWARD_LOOKING_F, 0, 0, 0, false, false,
                origin, forward, right, up);
    }

    public SonarMonitorSnapshot(List<SonarFrame> frames, int range,
                                int horizontalSector, Vec3 origin, Vec3 forward) {
        this(frames, range, range, horizontalSector, 20, inferHorizontalBeams(frames),
                SonarType.FORWARD_LOOKING_F, 0, 0, 0, false, false, origin, forward,
                SonarOrientation.flatFromForward(forward).right(),
                SonarOrientation.flatFromForward(forward).up());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Range", range);
        tag.putInt("DisplayRange", displayRange);
        tag.putInt("Sector", horizontalSector);
        tag.putInt("VerticalSector", verticalSector);
        tag.putInt("HorizontalBeams", horizontalBeams);
        tag.putString("SonarType", sonarType.name());
        tag.putFloat("ScanAngle", scanAngle);
        tag.putFloat("ScanAngularSpeed", scanAngularSpeed);
        tag.putLong("ScanAngleTick", scanAngleTick);
        tag.putBoolean("AutoHeight", autoHeight);
        tag.putBoolean("MirrorDisplay", mirrorDisplay);
        putVec(tag, "Origin", origin);
        putVec(tag, "Forward", forward);
        putVec(tag, "Right", right);
        putVec(tag, "Up", up);
        ListTag frameTags = new ListTag();
        for (SonarFrame frame : frames) frameTags.add(frame.save());
        tag.put("Frames", frameTags);
        return tag;
    }

    public Vec3 displayOrigin() {
        return origin;
    }

    public int effectiveDisplayRange() {
        return sonarType == SonarType.MECHANICAL_IMAGING_C
                ? range : (autoHeight ? displayRange : range);
    }

    public SonarOrientation displayOrientation() {
        return new SonarOrientation(forward, right, up);
    }

    public static SonarMonitorSnapshot load(CompoundTag tag) {
        List<SonarFrame> frames = new ArrayList<>();
        ListTag list = tag.getList("Frames", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) frames.add(SonarFrame.load(list.getCompound(i)));
        Vec3 forward = getVec(tag, "Forward");
        SonarOrientation fallback = SonarOrientation.flatFromForward(forward);
        Vec3 right = tag.contains("Right", Tag.TAG_COMPOUND) ? getVec(tag, "Right") : fallback.right();
        Vec3 up = tag.contains("Up", Tag.TAG_COMPOUND) ? getVec(tag, "Up") : fallback.up();
        int horizontalBeams = tag.contains("HorizontalBeams", Tag.TAG_INT)
                ? tag.getInt("HorizontalBeams") : inferHorizontalBeams(frames);
        int verticalSector = tag.contains("VerticalSector", Tag.TAG_INT)
                ? tag.getInt("VerticalSector") : 20;
        SonarType sonarType = SonarType.FORWARD_LOOKING_F;
        if (tag.contains("SonarType", Tag.TAG_STRING)) {
            try {
                sonarType = SonarType.valueOf(tag.getString("SonarType"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        boolean autoHeight = tag.contains("AutoHeight") && tag.getBoolean("AutoHeight");
        int range = Math.max(1, tag.getInt("Range"));
        int displayRange = tag.contains("DisplayRange", Tag.TAG_INT)
                ? tag.getInt("DisplayRange") : range;
        return new SonarMonitorSnapshot(frames, range, displayRange,
                Math.max(1, tag.getInt("Sector")), Math.max(1, verticalSector),
                Math.max(1, horizontalBeams), sonarType, tag.getFloat("ScanAngle"),
                tag.getFloat("ScanAngularSpeed"), tag.getLong("ScanAngleTick"), autoHeight, tag.getBoolean("MirrorDisplay"),
                getVec(tag, "Origin"), forward, right, up);
    }

    private static int inferHorizontalBeams(List<SonarFrame> frames) {
        int maxBeam = -1;
        for (SonarFrame frame : frames) {
            for (SonarReturn sonarReturn : frame.returns()) {
                maxBeam = Math.max(maxBeam, sonarReturn.beam());
            }
        }
        return Math.max(1, maxBeam + 1);
    }

    private static void putVec(CompoundTag parent, String key, Vec3 vec) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", vec.x);
        tag.putDouble("Y", vec.y);
        tag.putDouble("Z", vec.z);
        parent.put(key, tag);
    }

    private static Vec3 getVec(CompoundTag parent, String key) {
        CompoundTag tag = parent.getCompound(key);
        return new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
    }
}
