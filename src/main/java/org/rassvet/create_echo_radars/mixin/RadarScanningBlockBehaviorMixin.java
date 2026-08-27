package org.rassvet.create_echo_radars.mixin;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Create Radar beta applies its own Sable occlusion before exposing tracks to
 * the sonar. Echo Radars performs the same decision later using its own config
 * and ClipContext, so retaining both checks makes debug visibility disagree
 * with the tracks shown on the monitor.
 */
@Pseudo
@Mixin(targets = "com.happysg.radar.block.radar.behavior.RadarScanningBlockBehavior",
        remap = false)
public abstract class RadarScanningBlockBehaviorMixin {
    @Unique
    private static volatile Method createEchoRadars$sableOcclusionMethod;

    @Redirect(
            method = "updateRadarTracks",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/happysg/radar/block/radar/behavior/RadarOcclusion;isOccluded(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lcom/happysg/radar/block/arad/rwr/RadarType;Ldev/ryanhcode/sable/companion/SubLevelAccess;)Z"
            )
    )
    private boolean createEchoRadars$deferSonarSableOcclusion(
            BlockEntity radar, Vec3 scanPosition, Vec3 targetPosition,
            @Coerce Object radarType, SubLevelAccess targetSubLevel) {
        if (radar instanceof SonarBlockEntity) {
            return false;
        }
        return createEchoRadars$invokeOriginalOcclusion(
                radar, scanPosition, targetPosition, radarType, targetSubLevel);
    }

    @Unique
    private static boolean createEchoRadars$invokeOriginalOcclusion(
            BlockEntity radar, Vec3 scanPosition, Vec3 targetPosition,
            Object radarType, SubLevelAccess targetSubLevel) {
        try {
            Method method = createEchoRadars$sableOcclusionMethod;
            if (method == null) {
                Class<?> owner = Class.forName(
                        "com.happysg.radar.block.radar.behavior.RadarOcclusion");
                Class<?> radarTypeClass = Class.forName(
                        "com.happysg.radar.block.arad.rwr.RadarType");
                method = owner.getMethod("isOccluded", BlockEntity.class, Vec3.class,
                        Vec3.class, radarTypeClass, SubLevelAccess.class);
                createEchoRadars$sableOcclusionMethod = method;
            }
            return (boolean) method.invoke(
                    null, radar, scanPosition, targetPosition, radarType, targetSubLevel);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error fatal) throw fatal;
            throw new IllegalStateException("Create Radar Sable occlusion failed", cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Create Radar Sable occlusion method is unavailable", error);
        }
    }
}
