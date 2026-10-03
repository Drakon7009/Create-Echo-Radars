package org.rassvet.create_echo_radars.content.sonar;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SonarMonitorMirrorTest {
    @Test
    void monitorSnapshotKeepsMirrorSettingForEverySonarType() {
        for (SonarType type : SonarType.values()) {
            for (boolean mirrored : new boolean[]{false, true}) {
                SonarMonitorSnapshot source = new SonarMonitorSnapshot(List.of(), 128, 96,
                        type.defaultHorizontalAngle(), type.defaultVerticalAngle(), 64,
                        type, 37, 2, 1234, true, mirrored,
                        new Vec3(5, 10, 15), new Vec3(0, 0, 1),
                        new Vec3(1, 0, 0), new Vec3(0, 1, 0));
                SonarMonitorSnapshot loaded = SonarMonitorSnapshot.load(source.save());
                assertEquals(source, loaded);
                assertEquals(source.displayOrientation(), loaded.displayOrientation());
            }
        }
    }

    @Test
    void oldSnapshotsDefaultToNormalDisplay() {
        SonarMonitorSnapshot source = new SonarMonitorSnapshot(List.of(), 128, 90,
                Vec3.ZERO, new Vec3(0, 0, 1));
        var legacyTag = source.save();
        legacyTag.remove("MirrorDisplay");
        SonarMonitorSnapshot loaded = SonarMonitorSnapshot.load(legacyTag);
        assertFalse(loaded.mirrorDisplay());
        assertEquals(source, loaded);
    }
}
