package org.rassvet.create_echo_radars.mixin;

import org.rassvet.create_echo_radars.client.SonarGlassOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures solid-only terrain depth before Sodium draws its cutout pass.
 *
 * <p>Sodium 0.6 renders both SOLID and CUTOUT from its Minecraft
 * {@code solid} layer callback. Consequently NeoForge's
 * {@code AFTER_SOLID_BLOCKS} stage is too late to exclude kelp and seagrass.
 * This optional hook sits between those two internal passes.</p>
 */
@Pseudo
@Mixin(
        targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer",
        remap = false
)
public abstract class SodiumSonarOpaqueDepthMixin {
    @Inject(
            method = "drawChunkLayer"
                    + "(Lnet/minecraft/client/renderer/RenderType;"
                    + "Lnet/caffeinemc/mods/sodium/client/render/chunk/"
                    + "ChunkRenderMatrices;DDD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/"
                            + "RenderSectionManager;renderLayer"
                            + "(Lnet/caffeinemc/mods/sodium/client/render/chunk/"
                            + "ChunkRenderMatrices;"
                            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/"
                            + "terrain/TerrainRenderPass;DDD)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            ),
            require = 0,
            remap = false
    )
    private void createEchoRadars$captureSolidOnlyDepth(CallbackInfo ci) {
        SonarGlassOverlay.captureSodiumOpaqueDepth();
    }

    @Inject(
            method = "drawChunkLayer"
                    + "(Lnet/minecraft/client/renderer/RenderType;"
                    + "Lnet/caffeinemc/mods/sodium/client/render/chunk/"
                    + "ChunkRenderMatrices;DDD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/"
                            + "RenderSectionManager;renderLayer"
                            + "(Lnet/caffeinemc/mods/sodium/client/render/chunk/"
                            + "ChunkRenderMatrices;"
                            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/"
                            + "terrain/TerrainRenderPass;DDD)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            ),
            require = 0,
            remap = false
    )
    private void createEchoRadars$captureMainWorldCutoutDepth(CallbackInfo ci) {
        SonarGlassOverlay.captureSodiumWorldCutoutDepth();
    }
}
