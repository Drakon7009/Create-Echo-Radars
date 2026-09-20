package org.rassvet.create_echo_radars.mixin;

import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.monitor.MonitorRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.rassvet.create_echo_radars.client.SonarMonitorRenderer;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorSnapshot;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MonitorRenderer.class, remap = false)
public abstract class MonitorRendererMixin {
    @Inject(
            method = "renderSafe(Lcom/happysg/radar/block/monitor/MonitorBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void createEchoRadars$renderSonar(MonitorBlockEntity monitor, float partialTick,
                                               PoseStack poseStack, MultiBufferSource buffers,
                                               int light, int overlay, CallbackInfo ci) {
        SonarMonitorSnapshot snapshot =
                ((SonarMonitorExtension) monitor).createEchoRadars$getSonarSnapshot();
        SonarMonitorExtension extension = (SonarMonitorExtension) monitor;
        if (snapshot == null || !monitor.isController()
                || (!monitor.isLinked() && !extension.createEchoRadars$isSyntheticSnapshot())) return;
        SonarMonitorRenderer.render(monitor, snapshot, poseStack, buffers, partialTick);
        ci.cancel();
    }
}
