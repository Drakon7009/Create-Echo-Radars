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

    private SonarRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                             int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                             Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload,
                setupState, clearState);
    }

    public static RenderType gridOverlay() {
        return GRID_OVERLAY;
    }
}
