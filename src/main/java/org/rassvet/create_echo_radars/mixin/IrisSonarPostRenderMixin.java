package org.rassvet.create_echo_radars.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.rassvet.create_echo_radars.client.SonarGlassOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Iris finishes the world composite before GameRenderer draws the hand. */
@Mixin(GameRenderer.class)
public abstract class IrisSonarPostRenderMixin {
    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER))
    private void createEchoRadars$renderAfterIris(DeltaTracker delta,
                                                   CallbackInfo ci) {
        SonarGlassOverlay.onIrisLevelRendered();
    }
}
