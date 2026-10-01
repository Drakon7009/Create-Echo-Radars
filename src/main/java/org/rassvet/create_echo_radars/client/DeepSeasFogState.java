package org.rassvet.create_echo_radars.client;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.rassvet.create_echo_radars.CreateEchoRadars;

import java.lang.reflect.Method;

/** Uses DeepSeas' own client-side submarine fog decision. */
final class DeepSeasFogState {
    private static final boolean INSTALLED = ModList.get().isLoaded("create_submarine");
    private static Method shouldFog;
    private static boolean unavailable;

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
}
