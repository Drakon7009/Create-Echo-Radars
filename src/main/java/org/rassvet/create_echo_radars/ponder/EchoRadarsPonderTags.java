package org.rassvet.create_echo_radars.ponder;

import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import org.rassvet.create_echo_radars.CreateEchoRadars;

public final class EchoRadarsPonderTags {
    public static final ResourceLocation ECHO_RADARS = ResourceLocation.fromNamespaceAndPath(
            CreateEchoRadars.MOD_ID, "echo_radars");

    private EchoRadarsPonderTags() {
    }

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(ECHO_RADARS)
                .addToIndex()
                .item(CreateEchoRadars.SONAR_ITEM.get(), true, true)
                .title("Create: Echo Radars")
                .description("Sonars and displays for underwater detection networks")
                .register();

        helper.addTagToComponent(CreateEchoRadars.SONAR.getId(), ECHO_RADARS);
        helper.addTagToComponent(CreateEchoRadars.ECHO_SOUNDER.getId(), ECHO_RADARS);
        helper.addTagToComponent(CreateEchoRadars.SIDE_SCAN_SONAR.getId(), ECHO_RADARS);
        helper.addTagToComponent(CreateEchoRadars.MECHANICAL_SCANNING_SONAR.getId(), ECHO_RADARS);
        helper.addTagToComponent(CreateEchoRadars.COPPER_SONAR_GLASS.getId(), ECHO_RADARS);
        helper.addTagToComponent(CreateEchoRadars.IRON_SONAR_GLASS.getId(), ECHO_RADARS);
        helper.addTagToComponent(CreateEchoRadars.SIGNAL_SUMMATOR.getId(), ECHO_RADARS);
    }
}
