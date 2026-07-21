package org.rassvet.create_echo_radars.mixin;

import com.happysg.radar.block.monitor.MonitorBlock;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.monitor.MonitorInputHandler;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorSnapshot;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorExtension;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorDimensions;
import org.rassvet.create_echo_radars.content.sonar.SonarDisplayProjection;
import org.rassvet.create_echo_radars.content.sonar.SonarMath;
import org.rassvet.create_echo_radars.content.sonar.SonarOrientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MonitorInputHandler.class, remap = false)
public abstract class MonitorInputHandlerMixin {
    @Inject(method = "findTrack", at = @At("HEAD"), cancellable = true)
    private static void createEchoRadars$findSonarTrack(Level level, Vec3 hit, MonitorBlockEntity monitor,
                                                        CallbackInfoReturnable<RadarTrack> cir) {
        SonarMonitorSnapshot snapshot =
                ((SonarMonitorExtension) monitor).createEchoRadars$getSonarSnapshot();
        if (snapshot == null) return;

        hit = PhysicsHandler.getShipVec(hit, monitor);

        Direction widthDirection = level.getBlockState(monitor.getControllerPos())
                .getValue(MonitorBlock.FACING).getClockWise();
        Direction monitorFacing = level.getBlockState(monitor.getControllerPos()).getValue(MonitorBlock.FACING);
        SonarMonitorDimensions dimensions =
                ((SonarMonitorExtension) monitor).createEchoRadars$getMonitorDimensions();
        Vec3 center = Vec3.atCenterOf(monitor.getControllerPos())
                .add(widthDirection.getStepX() * (dimensions.width() - 1) / 2.0,
                        (dimensions.height() - 1) / 2.0,
                        widthDirection.getStepZ() * (dimensions.width() - 1) / 2.0);
        Vec3 local = adjustForFacing(hit.subtract(center), monitorFacing);
        double clickedX = local.x / (dimensions.width() * 0.5);
        double clickedZ = local.z / (dimensions.height() * 0.5);

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
                case ECHO_SOUNDER_A -> projected = new SonarDisplayProjection.Point(0.9,
                        1 - normalizedRange * 1.88);
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
        cir.setReturnValue(best);
    }

    private static Vec3 adjustForFacing(Vec3 relative, Direction facing) {
        return switch (facing) {
            case NORTH -> new Vec3(relative.x, 0, relative.y);
            case SOUTH -> new Vec3(-relative.x, 0, relative.y);
            case WEST -> new Vec3(relative.z, 0, relative.y);
            case EAST -> new Vec3(-relative.z, 0, relative.y);
            default -> relative;
        };
    }
}
