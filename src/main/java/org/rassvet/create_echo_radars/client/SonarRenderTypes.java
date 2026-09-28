package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Render layers used only by the sonar monitor. */
public final class SonarRenderTypes extends RenderType {
    // Grid pixels share one depth and are deduplicated before emission, so upload sorting
    // cannot change the result and only adds a large per-frame cost on bigger monitors.
    private static final RenderType GRID_OVERLAY = create(
            "create_echo_radars_sonar_grid_overlay",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            128 * 1024,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
    // Keep the echo order identical to the GUI, while still testing the monitor
    // against world geometry. Depth writes would hide later translucent returns.
    private static final RenderType WORLD_QUADS = create(
            "create_echo_radars_sonar_world_quads",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            128 * 1024,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
    // The monitor GUI reflects its Y axis to map the block's X/Z plane onto
    // screen coordinates. Its depth ordering is therefore different from the
    // block renderer's; draw the GUI background and echoes in emission order.
    private static final RenderType SCREEN_QUADS = create(
            "create_echo_radars_sonar_screen_quads",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            128 * 1024,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private SonarRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                             int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                             Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload,
                setupState, clearState);
    }

    public static RenderType gridOverlay() {
        return GRID_OVERLAY;
    }

    public static RenderType worldQuads() {
        return WORLD_QUADS;
    }

    public static RenderType screenQuads() {
        return SCREEN_QUADS;
    }
}
