package org.rassvet.create_echo_radars.compat.cbcmoreshells;

import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.rassvet.create_echo_radars.config.ServerConfig;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

/** Server-side replacement guidance for CBCMS torpedoes carrying Guided Fuze. */
public final class CbcmsTorpedoGuidance {
    private static final String MONITOR_POS_TAG = "monitorPos";
    private static final String INITIAL_YAW_TAG = "createEchoRadarsTorpedoInitialYaw";
    private static final String TARGET_WAS_AHEAD_TAG = "createEchoRadarsTorpedoTargetWasAhead";
    private static final String PREVIOUS_DISTANCE_TAG = "createEchoRadarsTorpedoPreviousDistanceSquared";
    private static final String TARGET_PASSED_TAG = "createEchoRadarsTorpedoTargetPassed";
    private static final String PITCH_COMMAND_TAG = "createEchoRadarsTorpedoPitchCommand";
    private static final String APPLIED_PITCH_TAG = "createEchoRadarsTorpedoAppliedPitch";
    private static final String PITCH_LIMIT_TAG = "createEchoRadarsTorpedoPitchLimit";

    private CbcmsTorpedoGuidance() {}

    public static boolean isSupportedTorpedo(AbstractCannonProjectile projectile) {
        return CbcmsTorpedoTypes.isSupported(projectile.getClass());
    }

    public static void guide(ItemStack fuze, AbstractCannonProjectile projectile) {
        if (projectile.level().getFluidState(projectile.blockPosition()).isEmpty()) return;

        CompoundTag tag = fuze.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBoolean(TARGET_PASSED_TAG)) return;

        BlockPos monitorPos = NbtUtils.readBlockPos(tag, MONITOR_POS_TAG).orElse(null);
        if (monitorPos == null
                || !(projectile.level().getBlockEntity(monitorPos)
                instanceof NetworkFiltererBlockEntity monitor)
                || monitor.activeTrackCache == null) {
            forgetTrackingState(fuze, tag);
            return;
        }

        Vec3 target = monitor.activeTrackCache.getPosition();
        if (target == null) {
            forgetTrackingState(fuze, tag);
            return;
        }

        Vec3 velocity = projectile.getDeltaMovement();
        boolean tagChanged = false;
        double initialYaw;
        if (tag.contains(INITIAL_YAW_TAG)) {
            initialYaw = tag.getDouble(INITIAL_YAW_TAG);
        } else {
            initialYaw = TorpedoGuidanceMath.yawDegrees(velocity.x, velocity.z);
            tag.putDouble(INITIAL_YAW_TAG, initialYaw);
            tagChanged = true;
        }

        Vec3 toTarget = target.subtract(projectile.position());
        TorpedoGuidanceMath.Velocity guidanceVelocity = velocity(velocity);
        TorpedoGuidanceMath.Velocity guidanceTarget = velocity(toTarget);
        double previousDistanceSquared = tag.contains(PREVIOUS_DISTANCE_TAG)
                ? tag.getDouble(PREVIOUS_DISTANCE_TAG) : Double.NaN;
        boolean wasAhead = tag.getBoolean(TARGET_WAS_AHEAD_TAG);
        if (TorpedoGuidanceMath.hasPassedTarget(
                wasAhead, previousDistanceSquared, guidanceVelocity, guidanceTarget)) {
            tag.putBoolean(TARGET_PASSED_TAG, true);
            tag.remove(PREVIOUS_DISTANCE_TAG);
            fuze.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            return;
        }
        if (!wasAhead && TorpedoGuidanceMath.isHorizontallyAhead(guidanceVelocity, guidanceTarget)) {
            tag.putBoolean(TARGET_WAS_AHEAD_TAG, true);
            tagChanged = true;
        }
        double distanceSquared = TorpedoGuidanceMath.horizontalDistanceSquared(guidanceTarget);
        if (!tag.contains(PREVIOUS_DISTANCE_TAG)
                || Double.compare(previousDistanceSquared, distanceSquared) != 0) {
            tag.putDouble(PREVIOUS_DISTANCE_TAG, distanceSquared);
            tagChanged = true;
        }

        double targetYaw = Math.hypot(toTarget.x, toTarget.z) < 1.0e-9
                ? TorpedoGuidanceMath.yawDegrees(velocity.x, velocity.z)
                : TorpedoGuidanceMath.yawDegrees(toTarget.x, toTarget.z);
        double seekDelta = TorpedoGuidanceMath.wrapDegrees(targetYaw - initialYaw);
        if (Math.abs(seekDelta) > ServerConfig.torpedoGuidanceMaxSeekDegrees()) {
            saveTagIfChanged(fuze, tag, tagChanged);
            return;
        }

        double previousPitchCommand = tag.contains(PITCH_COMMAND_TAG)
                ? tag.getDouble(PITCH_COMMAND_TAG)
                : TorpedoGuidanceMath.pitchDegrees(guidanceVelocity);
        double previousAppliedPitch = tag.contains(APPLIED_PITCH_TAG)
                ? tag.getDouble(APPLIED_PITCH_TAG) : Double.NaN;
        double pitchLimit;
        if (tag.contains(PITCH_LIMIT_TAG)) {
            pitchLimit = tag.getDouble(PITCH_LIMIT_TAG);
        } else {
            pitchLimit = TorpedoGuidanceMath.speedScaledMaxPitch(
                    guidanceVelocity.length(), ServerConfig.torpedoGuidanceMaxPitchDegrees());
            tag.putDouble(PITCH_LIMIT_TAG, pitchLimit);
            tagChanged = true;
        }
        TorpedoGuidanceMath.Steering steering = TorpedoGuidanceMath.steerWithPersistentPitch(
                guidanceVelocity, guidanceTarget,
                ServerConfig.torpedoGuidanceYawDegreesPerTick(),
                ServerConfig.torpedoGuidancePitchDegreesPerTick(),
                pitchLimit,
                previousPitchCommand,
                previousAppliedPitch);
        TorpedoGuidanceMath.Velocity guided = steering.velocity();
        if (!tag.contains(PITCH_COMMAND_TAG)
                || Double.compare(previousPitchCommand, steering.commandPitchDegrees()) != 0) {
            tag.putDouble(PITCH_COMMAND_TAG, steering.commandPitchDegrees());
            tagChanged = true;
        }
        if (!tag.contains(APPLIED_PITCH_TAG)
                || Double.compare(previousAppliedPitch, steering.appliedPitchDegrees()) != 0) {
            tag.putDouble(APPLIED_PITCH_TAG, steering.appliedPitchDegrees());
            tagChanged = true;
        }
        if (wouldExitThroughSurface(projectile, guided)) {
            guided = TorpedoGuidanceMath.flattenUpward(guided);
            tag.putDouble(PITCH_COMMAND_TAG, 0.0);
            tag.putDouble(APPLIED_PITCH_TAG, 0.0);
            tagChanged = true;
        }
        Vec3 guidedMovement = new Vec3(guided.x(), guided.y(), guided.z());
        if (!guidedMovement.equals(velocity)) {
            projectile.setDeltaMovement(guidedMovement);
            // setDeltaMovement alone does not schedule a velocity packet. Without
            // this flag the client keeps simulating the old straight course and
            // is periodically pulled back to the server position, which looks
            // like stuttering instead of a turn on slow CBCMS torpedoes.
            projectile.hasImpulse = true;
        }
        saveTagIfChanged(fuze, tag, tagChanged);
    }

    private static void forgetTrackingState(ItemStack fuze, CompoundTag tag) {
        if (!tag.contains(PREVIOUS_DISTANCE_TAG) && !tag.contains(PITCH_COMMAND_TAG)
                && !tag.contains(APPLIED_PITCH_TAG) && !tag.contains(PITCH_LIMIT_TAG)) return;
        tag.remove(PREVIOUS_DISTANCE_TAG);
        tag.remove(PITCH_COMMAND_TAG);
        tag.remove(APPLIED_PITCH_TAG);
        tag.remove(PITCH_LIMIT_TAG);
        fuze.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void saveTagIfChanged(ItemStack fuze, CompoundTag tag, boolean changed) {
        if (changed) fuze.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static boolean wouldExitThroughSurface(AbstractCannonProjectile projectile,
                                                   TorpedoGuidanceMath.Velocity velocity) {
        Vec3 position = projectile.position();
        BlockPos projected = BlockPos.containing(position.x + velocity.x(),
                position.y + velocity.y(), position.z + velocity.z());
        BlockPos projectedLevel = BlockPos.containing(position.x + velocity.x(),
                position.y, position.z + velocity.z());
        return TorpedoGuidanceMath.shouldFlattenForSurface(velocity,
                !projectile.level().getFluidState(projected).isEmpty(),
                !projectile.level().getFluidState(projectedLevel).isEmpty());
    }

    private static TorpedoGuidanceMath.Velocity velocity(Vec3 vector) {
        return new TorpedoGuidanceMath.Velocity(vector.x, vector.y, vector.z);
    }
}
