package org.rassvet.create_echo_radars.client;

import net.neoforged.fml.ModList;
import org.rassvet.create_echo_radars.CreateEchoRadars;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Keeps every direct Veil reference behind a reflective boundary, allowing the
 * mod to start and render with vanilla classes when Veil is not installed.
 */
final class SonarGlassRenderBackend {
    private static final String VEIL_BACKEND =
            "org.rassvet.create_echo_radars.client.SonarGlassVeilRenderer";
    private static boolean veilEnabled = ModList.get().isLoaded("veil");
    private static String fallbackReason = veilEnabled ? null : "missing";
    private static Method veilDepthRender;

    private SonarGlassRenderBackend() {
    }

    static boolean isVeilEnabled() {
        return veilEnabled;
    }

    static String fallbackReason() {
        return fallbackReason == null ? "unknown" : fallbackReason;
    }

    static void renderDepth(List<SonarGlassDepthDraw> draws) {
        if (draws.isEmpty()) return;
        if (veilEnabled) {
            try {
                if (veilDepthRender == null) {
                    veilDepthRender = veilBackend().getDeclaredMethod(
                            "renderDepth", List.class);
                }
                veilDepthRender.invoke(null, draws);
                return;
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause() == null
                        ? exception : exception.getCause();
                disableVeil(cause);
            } catch (ReflectiveOperationException | LinkageError exception) {
                disableVeil(exception);
            }
        }
        SonarGlassVanillaDepthRenderer.renderDepth(draws);
    }

    private static Class<?> veilBackend() throws ClassNotFoundException {
        return Class.forName(VEIL_BACKEND, true,
                SonarGlassRenderBackend.class.getClassLoader());
    }

    static void disableVeil(Throwable failure) {
        if (!veilEnabled) return;
        veilEnabled = false;
        fallbackReason = failure.getClass().getSimpleName();
        CreateEchoRadars.LOGGER.warn(
                "Veil sonar glass renderer failed; switching to the compatibility renderer",
                failure);
    }
}
