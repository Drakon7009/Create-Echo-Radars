package org.rassvet.create_echo_radars;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.network.chat.Component;
import com.happysg.radar.block.behavior.networks.NetworkData;
import org.rassvet.create_echo_radars.content.glass.SonarGlass;
import org.rassvet.create_echo_radars.content.glass.SonarGlassNetwork;
import org.rassvet.create_echo_radars.content.glass.SonarGlassNetworkManager;
import org.rassvet.create_echo_radars.content.sonar.SonarScanManager;

public final class CommonEvents {
    private CommonEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CommonEvents::levelTick);
        NeoForge.EVENT_BUS.addListener(CommonEvents::playerLoggedIn);
        NeoForge.EVENT_BUS.addListener(CommonEvents::blockPlaced);
    }

    private static void levelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SonarScanManager.get(level).tick();
            SonarGlassNetworkManager.get(level).tick(level);
        }
    }

    private static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ModNetworking.syncServerConfig(player);
        }
    }

    private static void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !SonarGlass.isGlass(level, event.getPos())) return;
        SonarGlassNetwork.Component component = SonarGlassNetwork.find(level, event.getPos());
        NetworkData data = NetworkData.get(level);
        int linked = 0;
        for (net.minecraft.core.BlockPos pos : component.blocks()) {
            if (data.getFiltererForEndpoint(level.dimension(), pos) != null && ++linked > 1) {
                event.setCanceled(true);
                if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
                    player.displayClientMessage(Component.translatable(
                            "message.create_echo_radars.sonar_glass.merge_linked"), true);
                }
                return;
            }
        }
    }
}
