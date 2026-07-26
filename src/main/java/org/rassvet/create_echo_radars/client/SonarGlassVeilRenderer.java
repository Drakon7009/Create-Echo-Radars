package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.content.glass.SonarGlassState;

import java.util.List;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;

/** Primary depth-based sonar-glass renderer backed by Veil. */
final class SonarGlassVeilRenderer {
    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(
                    CreateEchoRadars.MOD_ID, "sonar_glass_depth");

    private static boolean drawLogged;

    private SonarGlassVeilRenderer() {
    }

    static void renderDepth(List<SonarGlassDepthDraw> draws) {
        if (draws.isEmpty() || !SonarGlassDepthCapture.isValid()) return;
        int width = SonarGlassDepthCapture.width();
        int height = SonarGlassDepthCapture.height();

        ShaderProgram shader =
                VeilRenderSystem.renderer().getShaderManager().getShader(SHADER);
        if (shader == null || !shader.isValid()) {
            throw new IllegalStateException(
                    "Veil sonar_glass_depth shader is unavailable");
        }

        ShaderInstance bridge = VeilRenderBridge.toShaderInstance(shader);
        shader.setTexture("DepthSampler", GL_TEXTURE_2D,
                SonarGlassDepthCapture.opaqueTextureId());
        shader.setTexture("CutoutDepthSampler", GL_TEXTURE_2D,
                SonarGlassDepthCapture.cutoutTextureId());
        shader.setTexture("SceneDepthSampler", GL_TEXTURE_2D,
                SonarGlassDepthCapture.sceneTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        try {
            for (SonarGlassDepthDraw draw : draws) {
                setDepthUniforms(shader, draw, width, height);
                draw.aperture().bind();
                draw.aperture().drawWithShader(
                        draw.modelView(), draw.projection(), bridge);
            }
            if (!drawLogged) {
                drawLogged = true;
                CreateEchoRadars.LOGGER.info(
                        "Sonar glass depth renderer started: "
                                + "{} aperture group(s), {}x{}",
                        draws.size(), width, height);
            }
        } finally {
            VertexBuffer.unbind();
            bridge.clear();
            shader.clearSamplers();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void setDepthUniforms(ShaderProgram shader,
                                         SonarGlassDepthDraw draw,
                                         int width, int height) {
        shader.getUniformSafe("InverseViewProjection")
                .setMatrix(draw.inverseViewProjection());
        Vec3 camera = draw.cameraPosition();
        shader.getUniformSafe("CameraPosition")
                .setVector((float) camera.x, (float) camera.y, (float) camera.z);
        shader.getUniformSafe("MinRenderDistance")
                .setFloat(ClientConfig.sonarGlassMinimumDistance());
        shader.getUniformSafe("ScreenSize").setVector(width, height);
        shader.getUniformSafe("CycleAge").setFloat(draw.cycleAge());
        shader.getUniformSafe("Disconnect").setFloat(draw.disconnect());
        setStateUniforms(shader, "Current", draw.current());
        setStateUniforms(shader, "Previous", draw.previous());
    }

    private static void setStateUniforms(ShaderProgram shader, String prefix,
                                         SonarGlassState state) {
        setVector(shader, prefix + "Origin", state.origin());
        setVector(shader, prefix + "Forward", state.forward());
        setVector(shader, prefix + "Right", state.right());
        setVector(shader, prefix + "Up", state.up());
        shader.getUniformSafe(prefix + "Range").setFloat(state.range());
        shader.getUniformSafe(prefix + "HorizontalSector")
                .setFloat(state.horizontalSector());
        shader.getUniformSafe(prefix + "VerticalSector")
                .setFloat(state.verticalSector());
        shader.getUniformSafe(prefix + "Type")
                .setInt(state.sonarType().ordinal());
    }

    private static void setVector(ShaderProgram shader, String name, Vec3 vector) {
        shader.getUniformSafe(name).setVector(
                (float) vector.x, (float) vector.y, (float) vector.z);
    }
}
