package org.rassvet.create_echo_radars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.rassvet.create_echo_radars.CreateEchoRadars;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Supplies pack-native fog with world data; never samples framebuffer colour. */
public final class IrisNativeWaterFog {
    private static final Map<Integer, Uniforms> PROGRAMS = new HashMap<>();
    private static final Set<String> LOGGED = new HashSet<>();
    private static Matrix4f inverse = new Matrix4f(), view = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static AABB bounds;
    private static boolean applied;
    private static float sky;
    private static int biomeColor;

    private IrisNativeWaterFog() {}

    public static String patch(String name, String source) {
        IrisWaterFogPatcher.Result result = IrisWaterFogPatcher.patch(name, source);
        if (result.patched() && LOGGED.add(name + ":" + result.family()))
            CreateEchoRadars.LOGGER.info("Native submarine water fog adapter: {} ({})", name, result.family());
        return result.source();
    }

    static void prepare(Matrix4f viewProjection, Matrix4f modelView, Vec3 position) {
        applied = false;
        bounds = DeepSeasFogState.ownerBounds();
        camera = position;
        if (bounds == null) return;
        inverse.set(viewProjection).invert();
        view.set(modelView);
        var level = Minecraft.getInstance().level;
        if (level == null) { bounds = null; return; }
        BlockPos biomePosition = BlockPos.containing(position);
        biomeColor = level.getBiome(biomePosition).value().getWaterFogColor();
        sky = 0;
        double cx = (bounds.minX + bounds.maxX) * .5;
        double cy = (bounds.minY + bounds.maxY) * .5;
        double cz = (bounds.minZ + bounds.maxZ) * .5;
        Vec3[] probes = {new Vec3(bounds.minX - .5, cy, cz), new Vec3(bounds.maxX + .5, cy, cz),
                new Vec3(cx, bounds.minY - .5, cz), new Vec3(cx, bounds.maxY + .5, cz),
                new Vec3(cx, cy, bounds.minZ - .5), new Vec3(cx, cy, bounds.maxZ + .5)};
        int samples = 0;
        for (Vec3 probe : probes) {
            BlockPos p = BlockPos.containing(probe);
            if (level.hasChunkAt(p) && level.getFluidState(p).is(FluidTags.WATER)) {
                sky += level.getBrightness(LightLayer.SKY, p) / 15f;
                samples++;
            }
        }
        if (samples > 0) sky /= samples;
    }

    public static boolean appliedThisFrame() { return applied; }

    /** Called after Iris binds and updates a composite program. */
    public static void bind(int program) {
        Uniforms u = PROGRAMS.computeIfAbsent(program, Uniforms::new);
        if (u.enabled < 0) return;
        boolean active = bounds != null && DeepSeasFogState.shouldRender();
        GL20.glUniform1i(u.enabled, active ? 1 : 0);
        if (!active) return;
        applied = true;
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL20.glUniform2f(u.viewport, viewport[2], viewport[3]);
        GL20.glUniformMatrix4fv(u.inverse, false, inverse.get(new float[16]));
        GL20.glUniformMatrix4fv(u.view, false, view.get(new float[16]));
        GL20.glUniform3f(u.camera, (float) camera.x, (float) camera.y, (float) camera.z);
        GL20.glUniform3f(u.min, (float) (bounds.minX - camera.x), (float) (bounds.minY - camera.y), (float) (bounds.minZ - camera.z));
        GL20.glUniform3f(u.max, (float) (bounds.maxX - camera.x), (float) (bounds.maxY - camera.y), (float) (bounds.maxZ - camera.z));
        GL20.glUniform3f(u.biome, ((biomeColor >> 16) & 255) / 255f, ((biomeColor >> 8) & 255) / 255f, (biomeColor & 255) / 255f);
        GL20.glUniform1f(u.sky, sky);
    }

    public static void forget(int program) { PROGRAMS.remove(program); }

    private static final class Uniforms {
        final int enabled, inverse, view, camera, min, max, biome, sky, viewport;
        Uniforms(int program) {
            enabled = GL20.glGetUniformLocation(program, "cerSubmarine");
            inverse = location(program, "cerInverseVP"); view = location(program, "cerView");
            camera = location(program, "cerCamera"); min = location(program, "cerBoundsMin");
            max = location(program, "cerBoundsMax"); biome = location(program, "cerWaterBiomeColor");
            sky = location(program, "cerExteriorSky"); viewport = location(program, "cerViewport");
        }
        private int location(int program, String name) {
            return enabled < 0 ? -1 : GL20.glGetUniformLocation(program, name);
        }
    }
}
