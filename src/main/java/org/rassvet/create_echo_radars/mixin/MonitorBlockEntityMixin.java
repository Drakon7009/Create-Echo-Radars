package org.rassvet.create_echo_radars.mixin;

import com.happysg.radar.block.monitor.MonitorBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarDisplayLayout;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorDimensions;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorSnapshot;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorExtension;
import org.rassvet.create_echo_radars.content.sonar.SonarRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.rassvet.create_echo_radars.content.sonar.SonarScanManager;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = MonitorBlockEntity.class, remap = false)
public abstract class MonitorBlockEntityMixin implements SonarMonitorExtension {
    @Shadow
    protected @Nullable BlockPos radarPos;

    @Unique
    private SonarMonitorSnapshot createEchoRadars$sonarSnapshot;
    @Unique
    private BlockPos createEchoRadars$sonarSnapshotPos;
    @Unique
    private SonarMonitorSnapshot createEchoRadars$encodedSnapshotSource;
    @Unique
    private CompoundTag createEchoRadars$encodedSnapshot;
    @Unique
    private int createEchoRadars$monitorWidth = 1;
    @Unique
    private int createEchoRadars$monitorHeight = 1;

    @Inject(method = "write", at = @At("TAIL"))
    private void createEchoRadars$writeSonar(CompoundTag tag, HolderLookup.Provider registries,
                                             boolean clientPacket, CallbackInfo ci) {
        tag.putInt("CreateEchoRadarsMonitorWidth", createEchoRadars$monitorWidth);
        tag.putInt("CreateEchoRadarsMonitorHeight", createEchoRadars$monitorHeight);
        MonitorBlockEntity self = (MonitorBlockEntity) (Object) this;
        if (!clientPacket || !self.isController()) return;
        if (createEchoRadars$sonarSnapshot != null
                && java.util.Objects.equals(createEchoRadars$sonarSnapshotPos, radarPos)) {
            if (createEchoRadars$encodedSnapshotSource != createEchoRadars$sonarSnapshot) {
                createEchoRadars$encodedSnapshotSource = createEchoRadars$sonarSnapshot;
                createEchoRadars$encodedSnapshot = createEchoRadars$sonarSnapshot.save();
            }
            tag.put("CreateEchoRadarsSonar", createEchoRadars$encodedSnapshot);
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void createEchoRadars$readSonar(CompoundTag tag, HolderLookup.Provider registries,
                                            boolean clientPacket, CallbackInfo ci) {
        int fallbackSize = Math.max(1, ((MonitorBlockEntity) (Object) this).getSize());
        createEchoRadars$monitorWidth = tag.contains("CreateEchoRadarsMonitorWidth", Tag.TAG_INT)
                ? Math.max(1, tag.getInt("CreateEchoRadarsMonitorWidth")) : fallbackSize;
        createEchoRadars$monitorHeight = tag.contains("CreateEchoRadarsMonitorHeight", Tag.TAG_INT)
                ? Math.max(1, tag.getInt("CreateEchoRadarsMonitorHeight")) : fallbackSize;
        if (clientPacket && tag.contains("CreateEchoRadarsSonar", Tag.TAG_COMPOUND)) {
            createEchoRadars$sonarSnapshot =
                    SonarMonitorSnapshot.load(tag.getCompound("CreateEchoRadarsSonar"));
        } else if (clientPacket) {
            createEchoRadars$sonarSnapshot = null;
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/happysg/radar/block/monitor/MonitorBlockEntity;sendData()V"))
    private void createEchoRadars$updateServerSnapshot(CallbackInfo ci) {
        createEchoRadars$refreshServerSnapshot();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void createEchoRadars$sendFastMechanicalSnapshot(CallbackInfo ci) {
        MonitorBlockEntity self = (MonitorBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level) || !self.isController()) return;
        if (level.getGameTime() % 5 == 0 || radarPos == null) return;
        if (!(level.getBlockEntity(radarPos) instanceof SonarBlockEntity sonar)
                || sonar.getSonarType() != org.rassvet.create_echo_radars.content.sonar.SonarType.MECHANICAL_IMAGING_C)
            return;
        int interval = SonarRotation.mechanicalUpdateIntervalTicks(sonar.mechanicalAngularSpeed());
        if (interval >= 5 || level.getGameTime() % interval != 0) return;

        createEchoRadars$refreshServerSnapshot();
        self.sendData();
    }

    @Unique
    private void createEchoRadars$refreshServerSnapshot() {
        MonitorBlockEntity self = (MonitorBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level) || !self.isController()) return;
        SonarScanManager manager = SonarScanManager.get(level);
        self.getRadar().ifPresent(radar -> {
            if (radar instanceof SonarBlockEntity sonar) manager.touch(sonar);
        });
        if (radarPos == null) {
            createEchoRadars$clearSnapshot();
            return;
        }
        manager.touch(radarPos);
        boolean staleSnapshot = !java.util.Objects.equals(createEchoRadars$sonarSnapshotPos, radarPos);
        SonarMonitorSnapshot snapshot = manager.snapshot(radarPos);
        if (snapshot != null) {
            createEchoRadars$sonarSnapshot = snapshot;
            createEchoRadars$sonarSnapshotPos = radarPos;
        } else if (staleSnapshot) {
            createEchoRadars$clearSnapshot();
        }
    }

    @Unique
    private void createEchoRadars$clearSnapshot() {
        createEchoRadars$sonarSnapshot = null;
        createEchoRadars$sonarSnapshotPos = null;
        createEchoRadars$encodedSnapshotSource = null;
        createEchoRadars$encodedSnapshot = null;
    }

    @Override
    public SonarMonitorSnapshot createEchoRadars$getSonarSnapshot() {
        return createEchoRadars$sonarSnapshot;
    }

    @Override
    public void createEchoRadars$setSonarSnapshot(SonarMonitorSnapshot snapshot) {
        createEchoRadars$sonarSnapshot = snapshot;
    }

    @Override
    public SonarMonitorDimensions createEchoRadars$getMonitorDimensions() {
        int fallbackSize = Math.max(1, ((MonitorBlockEntity) (Object) this).getSize());
        return SonarDisplayLayout.resolveDimensions(createEchoRadars$monitorWidth,
                createEchoRadars$monitorHeight, fallbackSize);
    }

    @Override
    public void createEchoRadars$setMonitorDimensions(int width, int height) {
        createEchoRadars$monitorWidth = Math.max(1, width);
        createEchoRadars$monitorHeight = Math.max(1, height);
    }
}
