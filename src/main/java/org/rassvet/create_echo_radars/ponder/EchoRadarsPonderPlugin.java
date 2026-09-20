package org.rassvet.create_echo_radars.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import org.rassvet.create_echo_radars.CreateEchoRadars;

public final class EchoRadarsPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CreateEchoRadars.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        EchoRadarsPonderScenes.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        EchoRadarsPonderTags.register(helper);
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        helper.registerSharedText("sonar_types.water",
                "Install the sonar at the bow. It can be placed directly in water and must be submerged to operate.");
        helper.registerSharedText("sonar_types.limits",
                "Every sonar has a limited detection range and field of view.");
        helper.registerSharedText("sonar_types.configure",
                "Right-click the sonar to adjust its horizontal and vertical viewing angles.");
        helper.registerSharedText("sonar_types.angle_range",
                "Wider viewing angles cover more water, but angles set too high reduce the maximum detection range.");

        helper.registerSharedText("mechanical.intro",
                "The Mechanical Imaging Sonar scans the full area around itself.");
        helper.registerSharedText("mechanical.power",
                "It only scans while rotational force is supplied through the shaft on its base.");
        helper.registerSharedText("mechanical.configure_network",
                "Configure it with right-click and connect it to a Create: Radars controller like any other sonar.");

        helper.registerSharedText("glass.single",
                "Sonar glass accepts only one direct Data Transmitter, so it can display only one signal this way.");
        helper.registerSharedText("glass.remove_direct",
                "Remove the direct Data Transmitter before combining several signals with a Signal Summator.");
        helper.registerSharedText("glass.select",
                "Right-click the glass with a Signal Summator. The whole selected window will light up.");
        helper.registerSharedText("glass.place",
                "Place the selected Signal Summator no farther than 16 blocks from the glass.");
        helper.registerSharedText("glass.inputs",
                "Insert Data Transmitters into free sockets: up to four signals are combined for this glass window.");
    }
}
