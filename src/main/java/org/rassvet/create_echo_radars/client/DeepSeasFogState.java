package org.rassvet.create_echo_radars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.rassvet.create_echo_radars.CreateEchoRadars;

import java.lang.reflect.Method;
import java.util.UUID;

/** Uses DeepSeas' own client-side submarine fog decision. */
final class DeepSeasFogState {
    private static final boolean INSTALLED = ModList.get().isLoaded("create_submarine");
    private static Method shouldFog;
    private static Method findSealedSublevel;
    private static boolean unavailable;
    private static boolean boundsUnavailable;

    private DeepSeasFogState() {}

    static boolean shouldRender() {
        if (!INSTALLED || unavailable || !IrisShaderCompat.isShaderPackInUse()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return false;
        try {
            if (shouldFog == null) {
                Class<?> handler = Class.forName(
                        "com.maxenonyme.createsubmarine.submarine.client.SubmarineFogHandler");
                shouldFog = handler.getMethod("shouldFog");
            }
            return Boolean.TRUE.equals(shouldFog.invoke(null));
        } catch (ReflectiveOperationException | LinkageError exception) {
            unavailable = true;
            CreateEchoRadars.LOGGER.warn(
                    "DeepSeas submarine fog API is unavailable; shader fog is disabled",
                    exception);
            return false;
        }
    }

    static AABB ownerBounds() {
        if (!shouldRender() || boundsUnavailable
                || !ModList.get().isLoaded("sable")) return null;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (findSealedSublevel == null) {
                Class<?> tracker = Class.forName(
                        "com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker");
                findSealedSublevel = tracker.getMethod("findSealedSublevel",
                        Level.class, BlockPos.class);
            }
            Vec3 eye = minecraft.player.getEyePosition();
            UUID owner = null;
            // Match DeepSeas' 3x3x3 eye probe at compartment boundaries.
            for (double x = -0.35; x <= 0.35 && owner == null; x += 0.35) {
                for (double y = -0.35; y <= 0.35 && owner == null; y += 0.35) {
                    for (double z = -0.35; z <= 0.35 && owner == null; z += 0.35) {
                        owner = (UUID) findSealedSublevel.invoke(null, minecraft.level,
                                BlockPos.containing(eye.x + x, eye.y + y, eye.z + z));
                    }
                }
            }
            return owner == null ? null : LoadedSable.bounds(minecraft.level, owner);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            boundsUnavailable = true;
            CreateEchoRadars.LOGGER.warn(
                    "DeepSeas submarine bounds are unavailable; shader fog keeps the interior clear",
                    exception);
            return null;
        }
    }

    private static final class LoadedSable {
        private static AABB bounds(Level level, UUID owner) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                    .getContainer(level);
            if (container == null) return null;
            var subLevel = container.getSubLevel(owner);
            return subLevel == null || subLevel.isRemoved()
                    ? null : subLevel.boundingBox().toMojang();
        }
    }
}
