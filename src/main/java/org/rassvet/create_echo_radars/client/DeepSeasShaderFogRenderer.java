package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.rassvet.create_echo_radars.CreateEchoRadars;

import java.io.IOException;

/** Reapplies DeepSeas' 2–40 block blue fog after Iris' final composite. */
public final class DeepSeasShaderFogRenderer {
    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID,
                    "deep_seas_shader_fog");
    private static ShaderInstance shader;
    private static boolean fogLogged;

    private DeepSeasShaderFogRenderer() {}

    public static void registerShader(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    SHADER, DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to register " + SHADER, exception);
        }
    }

    static void render(Matrix4f inverseViewProjection, float opacity) {
        if (shader == null || !SonarGlassDepthCapture.isValid()
                || !DeepSeasFogState.shouldRender()) return;
        IrisShaderCompat.HandDepthTextures handDepth =
                IrisShaderCompat.handDepthTextures();
        if (handDepth == null) return;
        shader.setSampler("DepthSampler", SonarGlassDepthCapture.opaqueTextureId());
        shader.setSampler("WorldDepthSampler",
                SonarGlassDepthCapture.cutoutTextureId());
        shader.setSampler("SceneDepthSampler",
                SonarGlassDepthCapture.sceneTextureId());
        shader.setSampler("IrisDepthSampler", handDepth.finalDepth());
        shader.setSampler("IrisPreHandDepthSampler", handDepth.preHandDepth());
        shader.getUniform("InverseViewProjection").set(inverseViewProjection);
        shader.getUniform("ScreenSize").set(
                (float) SonarGlassDepthCapture.width(),
                (float) SonarGlassDepthCapture.height());
        shader.getUniform("FogOpacity").set(opacity);

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
