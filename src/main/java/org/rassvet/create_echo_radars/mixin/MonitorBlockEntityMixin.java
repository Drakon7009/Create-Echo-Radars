package org.rassvet.create_echo_radars.mixin;

import com.happysg.radar.api.monitor.MonitorRadarSnapshot;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.rassvet.create_echo_radars.compat.RadarsApi;
import org.rassvet.create_echo_radars.content.sonar.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Local renderer bridge; real sonar snapshots are synchronized by Radars' public API. */
@Mixin(value = MonitorBlockEntity.class, remap = false)
public abstract class MonitorBlockEntityMixin implements SonarMonitorExtension {
    @Shadow protected DetectionConfig filter;
    @Unique private SonarMonitorSnapshot createEchoRadars$synthetic;
    @Unique private SonarMonitorSnapshot createEchoRadars$decoded;
    @Unique private MonitorRadarSnapshot createEchoRadars$source;
    @Unique private int createEchoRadars$width = 1;
    @Unique private int createEchoRadars$height = 1;

    @Override
    public SonarMonitorSnapshot createEchoRadars$getSonarSnapshot() {
        if (createEchoRadars$synthetic != null) return createEchoRadars$synthetic;
        MonitorBlockEntity self = (MonitorBlockEntity) (Object) this;
        for (MonitorRadarSnapshot radar : self.getRadarSnapshots()) {
            if (!radar.data().has(RadarsApi.SONAR)) continue;
            if (createEchoRadars$source == null || createEchoRadars$source.data() != radar.data()) {
                createEchoRadars$source = radar;
                createEchoRadars$decoded = RadarsApi.snapshot(radar);
            }
            return createEchoRadars$decoded;
        }
        createEchoRadars$source = null;
        createEchoRadars$decoded = null;
        return null;
    }

    @Override public void createEchoRadars$setSonarSnapshot(SonarMonitorSnapshot snapshot) {
        createEchoRadars$synthetic = snapshot;
    }
    @Override public boolean createEchoRadars$isSyntheticSnapshot() { return createEchoRadars$synthetic != null; }
    @Override public void createEchoRadars$setSyntheticSnapshot(SonarMonitorSnapshot snapshot) {
        createEchoRadars$synthetic = snapshot;
    }
    @Override public SonarMonitorDimensions createEchoRadars$getMonitorDimensions() {
        int size = Math.max(1, ((MonitorBlockEntity) (Object) this).getSize());
        return createEchoRadars$synthetic == null ? new SonarMonitorDimensions(size, size)
                : new SonarMonitorDimensions(createEchoRadars$width, createEchoRadars$height);
    }
    @Override public void createEchoRadars$setMonitorDimensions(int width, int height) {
        createEchoRadars$width = Math.max(1, width);
        createEchoRadars$height = Math.max(1, height);
    }
    @Override public DetectionConfig createEchoRadars$getDetectionConfig() { return filter; }

    // Only the opt-in performance test uses synthetic monitor state without a real source.
    @Inject(method = "write", at = @At("TAIL"))
    private void createEchoRadars$writeSynthetic(CompoundTag tag, HolderLookup.Provider registries,
                                                boolean clientPacket, CallbackInfo ci) {
        if (createEchoRadars$synthetic == null) return;
        tag.put("CreateEchoRadarsSyntheticSonar", createEchoRadars$synthetic.save());
        tag.putInt("CreateEchoRadarsMonitorWidth", createEchoRadars$width);
        tag.putInt("CreateEchoRadarsMonitorHeight", createEchoRadars$height);
    }
    @Inject(method = "read", at = @At("TAIL"))
    private void createEchoRadars$readSynthetic(CompoundTag tag, HolderLookup.Provider registries,
                                               boolean clientPacket, CallbackInfo ci) {
        createEchoRadars$synthetic = tag.contains("CreateEchoRadarsSyntheticSonar", 10)
                ? SonarMonitorSnapshot.load(tag.getCompound("CreateEchoRadarsSyntheticSonar")) : null;
        createEchoRadars$width = Math.max(1, tag.getInt("CreateEchoRadarsMonitorWidth"));
        createEchoRadars$height = Math.max(1, tag.getInt("CreateEchoRadarsMonitorHeight"));
    }
}
