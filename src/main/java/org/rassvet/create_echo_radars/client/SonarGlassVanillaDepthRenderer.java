package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.content.glass.SonarGlassState;

import java.util.List;

/**
 * Veil-free compatibility backend using the same depth reconstruction and
 * procedural grid as the primary renderer.
 */
public final class SonarGlassVanillaDepthRenderer {
    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(
                    CreateEchoRadars.MOD_ID, "sonar_glass_depth_fallback");

    private static ShaderInstance shader;
    private static boolean drawLogged;

    private SonarGlassVanillaDepthRenderer() {
    }

    public static void registerShader(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    SHADER, DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(
                    "Failed to register " + SHADER + " shader", exception);
        }
    }

    static void renderDepth(List<SonarGlassDepthDraw> draws) {
        if (draws.isEmpty() || !SonarGlassDepthCapture.isValid()) return;
        if (shader == null) {
            throw new IllegalStateException(
                    "Vanilla sonar_glass_depth_fallback shader is unavailable");
        }

        int width = SonarGlassDepthCapture.width();
        int height = SonarGlassDepthCapture.height();

        shader.setSampler("DepthSampler",
                SonarGlassDepthCapture.opaqueTextureId());
        shader.setSampler("CutoutDepthSampler",
                SonarGlassDepthCapture.cutoutTextureId());
        shader.setSampler("SceneDepthSampler",
                SonarGlassDepthCapture.sceneTextureId());
        IrisShaderCompat.HandDepthTextures handDepth =
                IrisShaderCompat.handDepthTextures();
        int fallbackDepth = SonarGlassDepthCapture.opaqueTextureId();
        shader.setSampler("IrisPostHandDepthSampler",
                handDepth == null ? fallbackDepth : handDepth.postHandDepth());
        shader.setSampler("IrisPreHandDepthSampler",
                handDepth == null ? fallbackDepth : handDepth.preHandDepth());
        uniform("HandMaskEnabled").set(handDepth == null ? 0 : 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        try {
            for (SonarGlassDepthDraw draw : draws) {
                setDepthUniforms(draw, width, height);
                draw.aperture().bind();
                draw.aperture().drawWithShader(
                        draw.modelView(), draw.projection(), shader);
            }
            if (!drawLogged) {
                drawLogged = true;
                CreateEchoRadars.LOGGER.info(
                        "Sonar glass compatibility depth renderer started: "
                                + "{} aperture group(s), {}x{}",
                        draws.size(), width, height);
            }
        } finally {
            VertexBuffer.unbind();
            shader.clear();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void setDepthUniforms(SonarGlassDepthDraw draw,
                                         int width, int height) {
        uniform("InverseViewProjection").set(draw.inverseViewProjection());
        Vec3 camera = draw.cameraPosition();
        uniform("CameraPosition").set(
                (float) camera.x, (float) camera.y, (float) camera.z);
        uniform("MinRenderDistance")
                .set(ClientConfig.sonarGlassMinimumDistance());
        uniform("GridStyle").set(ClientConfig.sonarGlassGridStyle().shaderId());
        uniform("ScreenSize").set((float) width, (float) height);
        uniform("CycleAge").set(draw.cycleAge());
        uniform("Disconnect").set(draw.disconnect());
        setStateUniforms("Current", draw.current());
        setStateUniforms("Previous", draw.previous());
    }

    private static void setStateUniforms(String prefix, SonarGlassState state) {
        setVector(prefix + "Origin", state.origin());
        setVector(prefix + "Forward", state.forward());
        setVector(prefix + "Right", state.right());
        setVector(prefix + "Up", state.up());
        uniform(prefix + "Range").set((float) state.range());
        uniform(prefix + "HorizontalSector")
                .set((float) state.horizontalSector());
        uniform(prefix + "VerticalSector")
                .set((float) state.verticalSector());
        uniform(prefix + "Type").set(state.sonarType().ordinal());
    }

    private static void setVector(String name, Vec3 vector) {
        uniform(name).set((float) vector.x, (float) vector.y, (float) vector.z);
    }

    private static Uniform uniform(String name) {
        Uniform uniform = shader.getUniform(name);
        if (uniform == null) {
            throw new IllegalStateException(
                    "Missing uniform " + name + " in " + SHADER);
        }
        return uniform;
    }
}
