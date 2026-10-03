package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Depth copies shared by both grid backends.
 *
 * <p>The opaque copy supplies normal terrain. The world-cutout copy identifies
 * vegetation in the main level. The scene copy also contains Sable sublevel
 * construction geometry. Comparing all three lets the shader reject main-level
 * kelp while still selecting a moving construction that appeared after the
 * main terrain passes.</p>
 */
final class SonarGlassDepthCapture {
    private static TextureTarget opaqueTarget;
    private static TextureTarget cutoutTarget;
    private static TextureTarget sceneTarget;
    private static boolean opaqueValid;
    private static boolean cutoutValid;
    private static boolean sceneValid;
    private static boolean irisSourceLogged;

    private SonarGlassDepthCapture() {
    }

    static void captureOpaqueDepth() {
        CaptureSource source = CaptureSource.current();
        ensureTargets(source.width(), source.height());
        opaqueValid = capture(opaqueTarget, source);
        cutoutValid = false;
        sceneValid = false;
    }

    static void captureWorldCutoutDepth() {
        CaptureSource source = CaptureSource.current();
        cutoutValid = opaqueValid && dimensionsMatch(source)
                && capture(cutoutTarget, source);
        sceneValid = false;
    }

    static void captureSceneDepth() {
        CaptureSource source = CaptureSource.current();
        sceneValid = opaqueValid && cutoutValid && dimensionsMatch(source)
                && capture(sceneTarget, source);
    }

    /** Copies depth from the active world framebuffer. */
    private static boolean capture(TextureTarget target, CaptureSource source) {
        RenderSystem.assertOnRenderThread();
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL30.glBindFramebuffer(
                    GL30.GL_READ_FRAMEBUFFER, source.sourceFramebuffer());
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                    target.getDepthTextureId());
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                    0, 0, source.width(), source.height());
            return true;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL30.glBindFramebuffer(
                    GL30.GL_READ_FRAMEBUFFER, source.previousReadFramebuffer());
            GL30.glBindFramebuffer(
                    GL30.GL_DRAW_FRAMEBUFFER, source.previousDrawFramebuffer());
            GL11.glViewport(source.viewportX(), source.viewportY(),
                    source.viewportWidth(), source.viewportHeight());
        }
    }

    static boolean isValid() {
        return opaqueValid && cutoutValid && sceneValid
                && opaqueTarget != null && cutoutTarget != null
                && sceneTarget != null;
    }

    static boolean hasOpaqueDepth() {
        return opaqueValid && opaqueTarget != null;
    }

    static void invalidate() {
        opaqueValid = false;
        cutoutValid = false;
        sceneValid = false;
    }

    static int opaqueTextureId() {
        if (!hasOpaqueDepth()) {
            throw new IllegalStateException("Sonar glass depth capture is unavailable");
        }
        return opaqueTarget.getDepthTextureId();
    }

    static int cutoutTextureId() {
        if (!isValid()) {
            throw new IllegalStateException("Sonar glass depth capture is unavailable");
        }
        return cutoutTarget.getDepthTextureId();
    }

    static int sceneTextureId() {
        if (!isValid()) {
            throw new IllegalStateException("Sonar glass depth capture is unavailable");
        }
        return sceneTarget.getDepthTextureId();
    }

    static int width() {
        return opaqueTarget == null ? 0 : opaqueTarget.width;
    }

    static int height() {
        return opaqueTarget == null ? 0 : opaqueTarget.height;
    }

    private static boolean dimensionsMatch(CaptureSource source) {
        return opaqueTarget != null
                && opaqueTarget.width == source.width()
                && opaqueTarget.height == source.height();
    }

    private static void ensureTargets(int width, int height) {
        if (opaqueTarget != null && cutoutTarget != null && sceneTarget != null
                && opaqueTarget.width == width && opaqueTarget.height == height
                && cutoutTarget.width == width && cutoutTarget.height == height
                && sceneTarget.width == width && sceneTarget.height == height) return;
        if (opaqueTarget != null) opaqueTarget.destroyBuffers();
        if (cutoutTarget != null) cutoutTarget.destroyBuffers();
        if (sceneTarget != null) sceneTarget.destroyBuffers();
        opaqueTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        cutoutTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        sceneTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        invalidate();
    }

    private record CaptureSource(
            int sourceFramebuffer,
            int previousReadFramebuffer,
            int previousDrawFramebuffer,
            int width,
            int height,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight
    ) {
        private static CaptureSource current() {
            RenderSystem.assertOnRenderThread();
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int previousRead =
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int previousDraw =
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

            var main = Minecraft.getInstance().getMainRenderTarget();
            // Iris may bind a gbuffer framebuffer for terrain. Read the
            // framebuffer bound at this stage when one is available.
            boolean iris = IrisShaderCompat.isShaderPackInUse();
            int framebuffer = iris && previousDraw != 0
                    ? previousDraw : main.frameBufferId;
            if (iris && !irisSourceLogged) {
                irisSourceLogged = true;
                CreateEchoRadars.LOGGER.info(
                        "Iris sonar depth source framebuffer: {}, main framebuffer: {}",
                        framebuffer, main.frameBufferId);
            }
            return new CaptureSource(
                    framebuffer, previousRead, previousDraw,
                    Math.max(1, main.width), Math.max(1, main.height),
                    viewport[0], viewport[1],
                    viewport[2], viewport[3]);
        }
    }
}
