package org.rassvet.create_echo_radars.compat;

import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.world.phys.Vec3;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorSnapshot;
import org.rassvet.create_echo_radars.content.sonar.EchoSounderDepth;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorExtension;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorDimensions;
import org.rassvet.create_echo_radars.content.sonar.SonarDisplayLayout;
import org.rassvet.create_echo_radars.content.sonar.SonarDisplayProjection;
import org.rassvet.create_echo_radars.content.sonar.SonarMath;
import org.rassvet.create_echo_radars.content.sonar.SonarOrientation;

public final class SonarMonitorHitTest {
    public static com.happysg.radar.api.monitor.MonitorContactSnapshot findContact(
            com.happysg.radar.api.monitor.MonitorWorldHitTestContext context) {
        if (!(context.level().getBlockEntity(context.monitorPos()) instanceof MonitorBlockEntity monitor)) return null;
        for (var radar : context.radars()) {
            if (!radar.data().has(RadarsApi.SONAR)) continue;
            SonarMonitorSnapshot snapshot = RadarsApi.snapshot(radar);
            if (snapshot == null) continue;
            RadarTrack track = findTrack(monitor, snapshot, -context.normalizedX(), -context.normalizedZ());
            if (track != null) return new com.happysg.radar.api.monitor.MonitorContactSnapshot(
                    track.id(), track.position(), true, RadarsApi.SONAR.toString());
        }
        return null;
    }

    private static RadarTrack findTrack(MonitorBlockEntity monitor, SonarMonitorSnapshot snapshot,
                                         double clickedX, double clickedZ) {
        // The public context already accounts for the single-monitor render scale.
        if (snapshot.mirrorDisplay()) clickedX = -clickedX;
        SonarMonitorDimensions dimensions = ((SonarMonitorExtension) monitor).createEchoRadars$getMonitorDimensions();

        RadarTrack best = null;
        double bestDistance = 0.025;
        int displayRange = snapshot.effectiveDisplayRange();
        SonarOrientation orientation = snapshot.displayOrientation();
        Vec3 trackOrigin = snapshot.displayOrigin();
        for (RadarTrack track : monitor.getTracks()) {
            Vec3 relative = track.position().subtract(trackOrigin);
            if (relative.length() > snapshot.range()) continue;
            SonarMath.Projection projection = SonarMath.project(relative, orientation);
            double normalizedRange = projection.range() / Math.max(1, displayRange);
            SonarDisplayProjection.Point projected;
            switch (snapshot.sonarType()) {
                case FORWARD_LOOKING_F -> {
                    if (!SonarMath.insideCone(relative, orientation, snapshot.horizontalSector(),
                            snapshot.verticalSector(), snapshot.range())) continue;
                    projected = SonarDisplayProjection.project(normalizedRange,
                            Math.toRadians(projection.bearingDegrees()), snapshot.horizontalSector());
                }
                case MECHANICAL_IMAGING_C -> {
                    SonarMath.Projection horizontalProjection = SonarMath.projectHorizontal(relative, orientation);
                    normalizedRange = horizontalProjection.range() / Math.max(1, displayRange);
                    double angle = Math.toRadians(horizontalProjection.bearingDegrees());
                    projected = new SonarDisplayProjection.Point(
                            Math.sin(angle) * normalizedRange * 0.94,
                            Math.cos(angle) * normalizedRange * 0.94);
                }
                case ECHO_SOUNDER_A -> {
                    if (!SonarMath.insideCone(relative, orientation, snapshot.horizontalSector(),
                            snapshot.verticalSector(), snapshot.range())) continue;
                    double elevation = Math.toDegrees(Math.atan2(projection.up(),
                            Math.hypot(projection.forward(), projection.side())));
                    SonarDisplayProjection.Point angular = EchoSounderDepth.angularPoint(
                            projection.bearingDegrees(), elevation,
                            snapshot.horizontalSector(), snapshot.verticalSector());
                    if (angular == null) continue;
                    SonarDisplayLayout.Area display = SonarDisplayLayout.area(dimensions);
                    SonarDisplayLayout.Area map = EchoSounderDepth.worldMapArea(display);
                    double sourceX = map.centerX() + angular.x() * map.width() * 0.5;
                    double sourceZ = map.centerZ() + angular.z() * map.height() * 0.5;
                    projected = new SonarDisplayProjection.Point(
                            (sourceX - display.centerX()) * 2 / display.width(),
                            -(sourceZ - display.centerZ()) * 2 / display.height());
                }
                case SIDE_SCAN_D -> projected = new SonarDisplayProjection.Point(
                        Math.copySign(normalizedRange * 0.94, projection.bearingDegrees()), 0.9);
                default -> throw new IllegalStateException();
            }
            double distance = Math.pow(projected.x() - clickedX, 2)
                    + Math.pow(projected.z() - clickedZ, 2);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = track;
            }
        }
        return best;
    }

}
