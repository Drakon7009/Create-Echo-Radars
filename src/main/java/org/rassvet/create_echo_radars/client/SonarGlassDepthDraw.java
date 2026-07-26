package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.rassvet.create_echo_radars.content.glass.SonarGlassState;

/**
 * One connected sonar-glass aperture rendered by the optional depth-based
 * Veil backend. The terrain itself is reconstructed from the copied world
 * depth texture, so this contains no terrain vertices.
 */
record SonarGlassDepthDraw(VertexBuffer aperture,
                           Matrix4f modelView,
                           Matrix4f projection,
                           Matrix4f inverseViewProjection,
                           Vec3 cameraPosition,
                           SonarGlassState current,
                           SonarGlassState previous,
                           float cycleAge,
                           float disconnect) {
    SonarGlassDepthDraw {
        modelView = new Matrix4f(modelView);
        projection = new Matrix4f(projection);
        inverseViewProjection = new Matrix4f(inverseViewProjection);
        previous = previous == null ? current : previous;
    }
}
