package org.rassvet.create_echo_radars.mixin;

import com.happysg.radar.block.monitor.MonitorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.rassvet.create_echo_radars.client.SonarMonitorScreen;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.happysg.radar.block.monitor.MonitorBlock$Client", remap = false)
public abstract class MonitorBlockClientMixin {
    @Inject(method = "openMonitorScreen", at = @At("HEAD"), cancellable = true)
    private static void createEchoRadars$openSonarMonitorScreen(BlockPos pos, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
        if (!(blockEntity instanceof MonitorBlockEntity monitor)) return;
        MonitorBlockEntity controller = monitor.isController() ? monitor : monitor.getController();
        if (controller == null || !controller.isLinked()
                || ((SonarMonitorExtension) controller).createEchoRadars$getSonarSnapshot() == null) return;
        BlockPos controllerPos = controller.getControllerPos();
        minecraft.setScreen(new SonarMonitorScreen(
                controllerPos == null ? controller.getBlockPos() : controllerPos));
        ci.cancel();
    }
}
