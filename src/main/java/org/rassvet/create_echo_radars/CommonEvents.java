package org.rassvet.create_echo_radars;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.rassvet.create_echo_radars.content.sonar.SonarScanManager;

public final class CommonEvents {
    private CommonEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CommonEvents::levelTick);
        NeoForge.EVENT_BUS.addListener(CommonEvents::playerLoggedIn);
    }

    private static void levelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SonarScanManager.get(level).tick();
        }
    }

    private static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ModNetworking.syncServerConfig(player);
        }
    }
}
