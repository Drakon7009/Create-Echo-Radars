package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.opengl.GL14;

import java.io.IOException;

/** Alpha-composited overlays drawn after the shader pack's final pass. */
final class PostCompositeShader extends ShaderInstance {
    PostCompositeShader(ResourceProvider resources, ResourceLocation id, VertexFormat format) throws IOException {
        super(resources, id, format);
    }

    @Override
    public void apply() {
        // Iris clears the previously applied ExtendedShader inside super.apply().
        // That restores its blend override, replacing any state set by the caller
        // before drawWithShader(). Establish our state after that hand-off.
        super.apply();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
    }
}
