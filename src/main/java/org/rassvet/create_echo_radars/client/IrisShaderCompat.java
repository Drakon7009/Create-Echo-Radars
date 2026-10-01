package org.rassvet.create_echo_radars.client;

import net.neoforged.fml.ModList;
import org.rassvet.create_echo_radars.CreateEchoRadars;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Optional;

/** Keeps Iris optional while using its public shader-pack state API. */
public final class IrisShaderCompat {
    private static final boolean INSTALLED = ModList.get().isLoaded("iris");
    private static Method activeMethod;
    private static Object api;
    private static boolean unavailable;
    private static Method pipelineManagerMethod;
    private static Method currentPipelineMethod;
    private static Field renderTargetsField;
    private static Method postHandDepthMethod;
    private static Method preHandDepthMethod;
    private static Method textureIdMethod;
    private static boolean handDepthUnavailable;

    public record HandDepthTextures(int postHandDepth, int preHandDepth) {}

    private IrisShaderCompat() {}

    public static boolean isShaderPackInUse() {
        if (!INSTALLED || unavailable) return false;
        try {
            if (activeMethod == null) {
                Class<?> type = Class.forName(
                        "net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                activeMethod = type.getMethod("isShaderPackInUse");
            }
            return Boolean.TRUE.equals(activeMethod.invoke(api));
        } catch (ReflectiveOperationException | LinkageError exception) {
            unavailable = true;
            CreateEchoRadars.LOGGER.warn(
                    "Iris shader state API is unavailable; sonar glass will use the normal render path",
                    exception);
            return false;
        }
    }

    /** Iris depthtex1 includes the hand but excludes translucent glass; depthtex2 excludes both. */
    public static HandDepthTextures handDepthTextures() {
        if (!isShaderPackInUse() || handDepthUnavailable) return null;
        try {
            if (pipelineManagerMethod == null) {
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                pipelineManagerMethod = iris.getMethod("getPipelineManager");
                Class<?> manager = Class.forName(
                        "net.irisshaders.iris.pipeline.PipelineManager");
                currentPipelineMethod = manager.getMethod("getPipeline");
                Class<?> pipeline = Class.forName(
                        "net.irisshaders.iris.pipeline.IrisRenderingPipeline");
                renderTargetsField = pipeline.getDeclaredField("renderTargets");
                renderTargetsField.setAccessible(true);
                Class<?> targets = Class.forName(
                        "net.irisshaders.iris.targets.RenderTargets");
                postHandDepthMethod = targets.getMethod("getDepthTextureNoTranslucents");
                preHandDepthMethod = targets.getMethod("getDepthTextureNoHand");
                Class<?> depthTexture = Class.forName(
                        "net.irisshaders.iris.targets.DepthTexture");
                textureIdMethod = depthTexture.getMethod("getTextureId");
            }
            Object manager = pipelineManagerMethod.invoke(null);
            Optional<?> pipeline = (Optional<?>) currentPipelineMethod.invoke(manager);
            if (pipeline.isEmpty()) return null;
            Object targets = renderTargetsField.get(pipeline.get());
            Object postHandDepth = postHandDepthMethod.invoke(targets);
            Object preHandDepth = preHandDepthMethod.invoke(targets);
            int postHandId = (int) textureIdMethod.invoke(postHandDepth);
            int preHandId = (int) textureIdMethod.invoke(preHandDepth);
            if (postHandId <= 0 || preHandId <= 0) return null;
            return new HandDepthTextures(postHandId, preHandId);
        } catch (ReflectiveOperationException | LinkageError exception) {
            handDepthUnavailable = true;
            CreateEchoRadars.LOGGER.warn(
                    "Iris hand depth is unavailable; submarine shader fog is disabled",
                    exception);
            return null;
        }
    }
}
