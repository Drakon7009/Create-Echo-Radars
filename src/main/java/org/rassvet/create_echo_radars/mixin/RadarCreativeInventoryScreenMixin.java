package org.rassvet.create_echo_radars.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.compat.RadarInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class RadarCreativeInventoryScreenMixin
        extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    @Shadow private static CreativeModeTab selectedTab;
    @Shadow private float scrollOffs;
    @Unique private int createEchoRadars$bannerRow = -1;
    @Unique private long createEchoRadars$lastRender;
    @Unique private long createEchoRadars$animationTime;
    @Unique private static final ResourceLocation createEchoRadars$BANNER = ResourceLocation.fromNamespaceAndPath(
            CreateEchoRadars.MOD_ID, "textures/gui/sprites/banner.png");

    protected RadarCreativeInventoryScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu,
                                                Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void createEchoRadars$selectTab(CreativeModeTab tab, CallbackInfo ci) {
        createEchoRadars$animationTime = 0;
        createEchoRadars$lastRender = 0;
        createEchoRadars$insertBannerRow();
    }

    @Inject(method = "refreshCurrentTabContents", at = @At("TAIL"))
    private void createEchoRadars$refreshContents(Collection<ItemStack> items, CallbackInfo ci) {
        createEchoRadars$insertBannerRow();
    }

    @Unique
    private void createEchoRadars$insertBannerRow() {
        createEchoRadars$bannerRow = -1;
        if (!RadarInventory.isRadarTab(selectedTab)) return;
        List<ItemStack> radarItems = new ArrayList<>();
        List<ItemStack> echoItems = new ArrayList<>();
        for (ItemStack stack : menu.items) {
            if (stack.isEmpty()) continue;
            (RadarInventory.isEchoItem(stack) ? echoItems : radarItems).add(stack);
        }
        if (echoItems.isEmpty()) return;
        createEchoRadars$bannerRow = (radarItems.size() + 8) / 9;
        menu.items.clear();
        menu.items.addAll(radarItems);
        while (menu.items.size() < (createEchoRadars$bannerRow + 1) * 9) {
            menu.items.add(ItemStack.EMPTY);
        }
        menu.items.addAll(echoItems);
        menu.scrollTo(scrollOffs);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void createEchoRadars$renderBanner(GuiGraphics graphics, int mouseX, int mouseY,
                                              float partialTick, CallbackInfo ci) {
        if (!RadarInventory.isRadarTab(selectedTab) || createEchoRadars$bannerRow < 0) return;
        int scrollRows = (menu.items.size() + 8) / 9 - 5;
        int firstRow = Math.max((int) (scrollOffs * scrollRows + 0.5), 0);
        int visibleRow = createEchoRadars$bannerRow - firstRow;
        if (visibleRow < 0 || visibleRow >= 5) {
            createEchoRadars$lastRender = 0;
            return;
        }
        int x = leftPos + 8;
        int y = topPos + 17 + visibleRow * 18;
        boolean hovered = mouseX >= x && mouseX < x + 162 && mouseY >= y && mouseY < y + 18;
        long now = System.nanoTime();
        if (hovered && createEchoRadars$lastRender != 0) {
            createEchoRadars$animationTime += now - createEchoRadars$lastRender;
        }
        createEchoRadars$lastRender = now;
        int frame = (int) ((createEchoRadars$animationTime / 300_000_000L) % 24);
        graphics.blit(createEchoRadars$BANNER, x, y, 0, frame * 18, 162, 18, 162, 432);
        Component label = Component.translatable("itemGroup.create_echo_radars");
        graphics.fill(x + 2, y + 2, x + font.width(label) + 8, y + 16, 0xaa00203c);
        graphics.drawString(font, label, x + 5, y + 5, 0x88ccff, true);
    }
}
