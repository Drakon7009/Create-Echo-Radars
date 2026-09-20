package org.rassvet.create_echo_radars;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.rassvet.create_echo_radars.client.SonarDebugRenderer;
import org.rassvet.create_echo_radars.client.SonarScreen;
import org.rassvet.create_echo_radars.client.MechanicalSonarRenderer;
import org.rassvet.create_echo_radars.client.SonarGlassOverlay;
import org.rassvet.create_echo_radars.client.SonarGlassVanillaDepthRenderer;
import org.rassvet.create_echo_radars.client.SonarSignalSummatorRenderer;
import org.rassvet.create_echo_radars.client.SonarSignalSummatorOutline;
import org.rassvet.create_echo_radars.compat.fusion.SonarSlopeFrameConnectionPredicate;
import org.rassvet.create_echo_radars.performance.PerformanceClientTelemetry;
import org.rassvet.create_echo_radars.performance.PerformanceTestBuild;

public final class ClientEvents {
    private ClientEvents() {}

    public static void register(IEventBus modBus, ModContainer container) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            if (ModList.get().isLoaded("fusion")) {
                SonarSlopeFrameConnectionPredicate.register();
            }
            container.registerExtensionPoint(IConfigScreenFactory.class,
                    (IConfigScreenFactory) (ignored, parent) ->
                            org.rassvet.create_echo_radars.client.SonarConfigScreen.create(parent));
            modBus.addListener(ClientEvents::registerScreens);
            modBus.addListener(ClientEvents::registerRenderers);
            modBus.addListener(ClientEvents::registerAdditionalModels);
            modBus.addListener(SonarGlassVanillaDepthRenderer::registerShader);
            NeoForge.EVENT_BUS.addListener(SonarDebugRenderer::onRightClickBlock);
            NeoForge.EVENT_BUS.addListener(SonarDebugRenderer::onRenderLevel);
            NeoForge.EVENT_BUS.addListener(SonarGlassOverlay::onRenderLevel);
            NeoForge.EVENT_BUS.addListener(SonarSignalSummatorOutline::onClientTick);
            if (PerformanceTestBuild.enabled()) {
                NeoForge.EVENT_BUS.addListener(PerformanceClientTelemetry::onFrame);
            }
        }
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(CreateEchoRadars.SONAR_MENU.get(), SonarScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateEchoRadars.SONAR_BLOCK_ENTITY.get(),
                MechanicalSonarRenderer::new);
        event.registerBlockEntityRenderer(CreateEchoRadars.SIGNAL_SUMMATOR_BLOCK_ENTITY.get(),
                SonarSignalSummatorRenderer::new);
    }

    private static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(MechanicalSonarRenderer.ROTATING_MODEL);
    }
}
