package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.rassvet.create_echo_radars.CreateEchoRadars;

import java.io.IOException;

/** DeepSeas fog fallback for shader packs without an active native water adapter. */
public final class DeepSeasShaderFogRenderer {
    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID,
                    "deep_seas_shader_fog");
    private static ShaderInstance shader;
    private static boolean fogLogged;
    private static boolean ownerBoundsLogged;

    private DeepSeasShaderFogRenderer() {}

    public static void registerShader(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    SHADER, DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to register " + SHADER, exception);
        }
    }

    static void render(Matrix4f inverseViewProjection, Vec3 cameraPosition,
                       AABB ownerBounds, float opacity) {
        if (IrisNativeWaterFog.appliedThisFrame() || shader == null || !SonarGlassDepthCapture.isValid()
                || !DeepSeasFogState.shouldRender()) return;
        IrisShaderCompat.HandDepthTextures handDepth =
                IrisShaderCompat.handDepthTextures();
        if (handDepth == null) return;
        shader.setSampler("DepthSampler", SonarGlassDepthCapture.opaqueTextureId());
        shader.setSampler("WorldDepthSampler",
                SonarGlassDepthCapture.cutoutTextureId());
        shader.setSampler("SceneDepthSampler",
                SonarGlassDepthCapture.sceneTextureId());
        shader.setSampler("IrisPostHandDepthSampler", handDepth.postHandDepth());
        shader.setSampler("IrisPreHandDepthSampler", handDepth.preHandDepth());
        shader.getUniform("InverseViewProjection").set(inverseViewProjection);
        shader.getUniform("ScreenSize").set(
                (float) SonarGlassDepthCapture.width(),
                (float) SonarGlassDepthCapture.height());
        shader.getUniform("FogOpacity").set(opacity);
        shader.getUniform("OwnerBoundsEnabled").set(ownerBounds == null ? 0 : 1);
        if (ownerBounds != null) {
            if (!ownerBoundsLogged) {
                ownerBoundsLogged = true;
                CreateEchoRadars.LOGGER.info(
                        "DeepSeas shader fog is protecting the player's Sable bounds: {}",
                        ownerBounds);
            }
            shader.getUniform("OwnerBoundsMin").set(
                    (float) (ownerBounds.minX - cameraPosition.x),
                    (float) (ownerBounds.minY - cameraPosition.y),
                    (float) (ownerBounds.minZ - cameraPosition.z));
            shader.getUniform("OwnerBoundsMax").set(
                    (float) (ownerBounds.maxX - cameraPosition.x),
                    (float) (ownerBounds.maxY - cameraPosition.y),
                    (float) (ownerBounds.maxZ - cameraPosition.z));
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        try {
            BufferBuilder quad = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            quad.addVertex(-1, -1, 0);
            quad.addVertex(1, -1, 0);
            quad.addVertex(1, 1, 0);
            quad.addVertex(-1, 1, 0);
            BufferUploader.drawWithShader(quad.buildOrThrow());
            if (!fogLogged) {
                fogLogged = true;
                CreateEchoRadars.LOGGER.info(
                        "DeepSeas submarine shader fog is drawing after Iris");
            }
        } finally {
            shader.clear();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }
}
