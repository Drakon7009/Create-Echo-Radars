package org.rassvet.create_echo_radars;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.rassvet.create_echo_radars.client.SonarDebugRenderer;
import org.rassvet.create_echo_radars.client.SonarScreen;
import org.rassvet.create_echo_radars.client.MechanicalSonarRenderer;

public final class ClientEvents {
    private ClientEvents() {}

    public static void register(IEventBus modBus, ModContainer container) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerExtensionPoint(IConfigScreenFactory.class,
                    (IConfigScreenFactory) (ignored, parent) ->
                            org.rassvet.create_echo_radars.client.SonarConfigScreen.create(parent));
            modBus.addListener(ClientEvents::registerScreens);
            modBus.addListener(ClientEvents::registerRenderers);
            NeoForge.EVENT_BUS.addListener(SonarDebugRenderer::onRightClickBlock);
            NeoForge.EVENT_BUS.addListener(SonarDebugRenderer::onRenderLevel);
        }
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(CreateEchoRadars.SONAR_MENU.get(), SonarScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateEchoRadars.SONAR_BLOCK_ENTITY.get(),
                MechanicalSonarRenderer::new);
    }
}
