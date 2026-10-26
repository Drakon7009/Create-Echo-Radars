package org.rassvet.create_echo_radars.compat;

import com.happysg.radar.api.monitor.MonitorRadarData;
import com.happysg.radar.api.monitor.MonitorRadarSnapshot;
import com.happysg.radar.api.network.RadarNetworkApi;
import com.happysg.radar.api.radar.RadarDisplayProfile;
import com.happysg.radar.api.radar.RadarSweepStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorSnapshot;

import java.util.List;
import java.util.Map;

/** Shared identifiers and network queries for the Radars 5.0 API. */
public final class RadarsApi {
    public static final ResourceLocation SONAR = ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID, "sonar");
    public static final RadarDisplayProfile DISPLAY = new RadarDisplayProfile(SONAR, RadarSweepStyle.NONE, false, false);

    private RadarsApi() {}

    public static MonitorRadarData data(SonarMonitorSnapshot snapshot) {
        return snapshot == null ? MonitorRadarData.empty() : new MonitorRadarData(Map.of(SONAR, snapshot.save()));
    }

    public static SonarMonitorSnapshot snapshot(MonitorRadarSnapshot radar) {
        return radar.data().get(SONAR).map(SonarMonitorSnapshot::load).orElse(null);
    }

    public static List<SonarBlockEntity> sonars(ServerLevel level, BlockPos controller) {
        return RadarNetworkApi.getRadarPositions(level, controller).stream()
                .filter(level::hasChunkAt).map(level::getBlockEntity)
                .filter(SonarBlockEntity.class::isInstance).map(SonarBlockEntity.class::cast)
                .filter(sonar -> !sonar.isRemoved()).sorted(java.util.Comparator.comparingLong(s -> s.getBlockPos().asLong()))
                .toList();
    }
}
