package org.rassvet.create_echo_radars.compat;

import com.happysg.radar.api.monitor.MonitorExtensionState;
import com.happysg.radar.api.monitor.MonitorRadarSnapshot;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorSnapshot;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RadarsApiTest {
    @BeforeAll static void bootstrap() {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void publicMonitorPayloadKeepsEachSourcesOwnGeometry() {
        var first = snapshot(new Vec3(2, 3, 4));
        var second = snapshot(new Vec3(20, 30, 40));
        var firstRadar = radar(new BlockPos(1, 2, 3), first);
        var secondRadar = radar(new BlockPos(4, 5, 6), second);
        assertEquals(first, RadarsApi.snapshot(firstRadar));
        assertEquals(second, RadarsApi.snapshot(secondRadar));
        assertNotEquals(RadarsApi.snapshot(firstRadar).origin(), RadarsApi.snapshot(secondRadar).origin());
        assertEquals(RadarsApi.SONAR, firstRadar.displayProfile().typeId());
    }

    @Test void publicPayloadCannotBeMutatedThroughRetrievedNbt() {
        var source = snapshot(Vec3.ZERO);
        var radar = radar(BlockPos.ZERO, source);
        radar.data().get(RadarsApi.SONAR).orElseThrow().putInt("Range", 1);
        assertEquals(source, RadarsApi.snapshot(radar));
        assertFalse(RadarsApi.data(null).has(RadarsApi.SONAR));
    }

    private static SonarMonitorSnapshot snapshot(Vec3 origin) {
        return new SonarMonitorSnapshot(List.of(), 96, 60, origin,
                new Vec3(0, 0, -1), new Vec3(1, 0, 0), new Vec3(0, 1, 0));
    }
    private static MonitorRadarSnapshot radar(BlockPos pos, SonarMonitorSnapshot sonar) {
        return new MonitorRadarSnapshot(pos, sonar.origin(), 96, true, RadarsApi.DISPLAY,
                0, 0, 0, 60, null, null, null, RadarsApi.data(sonar), MonitorExtensionState.empty());
    }
}
