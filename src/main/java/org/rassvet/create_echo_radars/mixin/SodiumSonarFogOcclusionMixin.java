package org.rassvet.create_echo_radars.mixin;

import org.rassvet.create_echo_radars.client.SonarGlassOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps distant terrain available to the sonar depth pass without changing the
 * fog rendered by Deep Seas.
 *
 * <p>Sodium normally limits its visible-section search to the end of fully
 * opaque fog. That is a useful optimization for the normal world render, but it
 * also removes the terrain from the depth buffer used by sonar glass. While a
 * sonar display is active, use Sodium's normal chunk render distance instead.
 * The fog uniforms themselves remain untouched, so Deep Seas still hides the
 * regular world at its configured distance.</p>
 */
@Pseudo
@Mixin(
        targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager",
        remap = false
)
public abstract class SodiumSonarFogOcclusionMixin {
    @Shadow
    @Final
    private int renderDistance;

    @Inject(
            method = "getSearchDistance()F",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void createEchoRadars$keepDistantTerrainForSonar(
            CallbackInfoReturnable<Float> cir
    ) {
        if (SonarGlassOverlay.hasActiveDisplay()) {
            cir.setReturnValue(this.renderDistance * 16.0F);
        }
    }
}
