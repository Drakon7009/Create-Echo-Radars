package org.rassvet.create_echo_radars.client;

import com.happysg.radar.api.monitor.client.MonitorRadarRenderRegistry;
import com.happysg.radar.api.monitor.client.MonitorRadarRenderer;
import com.happysg.radar.api.monitor.client.MonitorRadarWorldRenderContext;
import com.happysg.radar.api.monitor.client.MonitorScreenRegistry;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import org.rassvet.create_echo_radars.compat.RadarsApi;

import java.util.Map;
import java.util.WeakHashMap;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorSnapshot;

public final class RadarsClientIntegration {
    private RadarsClientIntegration() {}

    public static void register() {
        MonitorScreenRegistry.register(RadarsApi.SONAR, (level, pos, radars) ->
                radars.stream().anyMatch(radar -> radar.data().has(RadarsApi.SONAR))
                        ? new SonarMonitorScreen(pos) : null);
        MonitorRadarRenderRegistry.register(RadarsApi.SONAR, new MonitorRadarRenderer() {
            private final Map<com.happysg.radar.api.monitor.MonitorRadarData, SonarMonitorSnapshot> snapshots = new WeakHashMap<>();

            @Override
            public boolean renderWorld(MonitorRadarWorldRenderContext context) {
                if (!(context.level().getBlockEntity(context.monitorPos()) instanceof MonitorBlockEntity monitor)) return false;
                SonarMonitorSnapshot snapshot = snapshots.computeIfAbsent(context.radar().data(),
                        data -> data.get(RadarsApi.SONAR).map(SonarMonitorSnapshot::load).orElse(null));
                if (snapshot == null) return false;
                SonarMonitorRenderer.renderOnMonitorFace(monitor, snapshot,
                        context.poseStack(), context.bufferSource(), context.partialTicks());
                return true;
            }
        });
    }
}
