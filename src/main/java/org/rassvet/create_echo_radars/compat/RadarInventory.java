package org.rassvet.create_echo_radars.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.rassvet.create_echo_radars.CreateEchoRadars;

public final class RadarInventory {
    public static final ResourceLocation TAB = ResourceLocation.fromNamespaceAndPath("create_radar", "radar");

    private RadarInventory() {}

    public static boolean isRadarTab(CreativeModeTab tab) {
        return TAB.equals(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab));
    }

    public static boolean isEchoItem(ItemStack stack) {
        return !stack.isEmpty() && CreateEchoRadars.MOD_ID.equals(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    public static void addItems(BuildCreativeModeTabContentsEvent event) {
        if (!TAB.equals(event.getTabKey().location())) return;
        event.accept(CreateEchoRadars.SONAR_ITEM.get());
        event.accept(CreateEchoRadars.ECHO_SOUNDER_ITEM.get());
        event.accept(CreateEchoRadars.MECHANICAL_SCANNING_SONAR_ITEM.get());
        event.accept(CreateEchoRadars.SIDE_SCAN_SONAR_ITEM.get());
        event.accept(CreateEchoRadars.SIGNAL_SUMMATOR_ITEM.get());
        event.accept(CreateEchoRadars.COPPER_SONAR_GLASS_ITEM.get());
        event.accept(CreateEchoRadars.IRON_SONAR_GLASS_ITEM.get());
        event.accept(CreateEchoRadars.SONAR_DEBUG_TOOL.get());
    }
}
