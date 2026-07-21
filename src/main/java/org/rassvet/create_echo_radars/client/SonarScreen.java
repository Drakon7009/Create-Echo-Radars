package org.rassvet.create_echo_radars.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.rassvet.create_echo_radars.ModNetworking;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarMenu;

public class SonarScreen extends AbstractContainerScreen<SonarMenu> {
    private IntSlider range;
    private IntSlider sector;
    private IntSlider verticalSector;
    private IntSlider tiltAngle;
    private boolean draftInitialized;
    private int draftRange;
    private int draftSector;
    private int draftVerticalSector;
    private int draftTiltAngle;
    private boolean draftAutoHeight;

    public SonarScreen(SonarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 260;
        imageHeight = 248;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 20;
        int width = imageWidth - 40;
        if (!draftInitialized) {
            draftRange = menu.getRange();
            draftSector = menu.getSector();
            draftVerticalSector = menu.getVerticalSector();
            draftTiltAngle = menu.getTiltAngle();
            draftAutoHeight = menu.isAutoHeight();
            draftInitialized = true;
        }
        range = addRenderableWidget(new IntSlider(x, topPos + 35, width,
                "gui.create_echo_radars.range", SonarBlockEntity.MIN_RANGE, SonarBlockEntity.MAX_RANGE,
                draftRange, ""));
        sector = addRenderableWidget(new IntSlider(x, topPos + 65, width,
                "gui.create_echo_radars.horizontal_sector", SonarBlockEntity.MIN_ANGLE, SonarBlockEntity.MAX_ANGLE,
                draftSector, "°"));
        verticalSector = addRenderableWidget(new IntSlider(x, topPos + 95, width,
                "gui.create_echo_radars.vertical_sector", SonarBlockEntity.MIN_ANGLE, SonarBlockEntity.MAX_ANGLE,
                draftVerticalSector, "°"));
        tiltAngle = addRenderableWidget(new IntSlider(x, topPos + 125, width,
                "gui.create_echo_radars.tilt_angle", SonarBlockEntity.MIN_TILT, SonarBlockEntity.MAX_TILT,
                draftTiltAngle, "°"));
        addRenderableWidget(Button.builder(autoHeightMessage(), button -> {
            draftAutoHeight = !draftAutoHeight;
            button.setMessage(autoHeightMessage());
        }).bounds(x, topPos + 155, width, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.create_echo_radars.angle_preview"), button -> {
            captureDraft();
            if (minecraft != null && menu.getSonar() != null) {
                boolean enabled = SonarDebugRenderer.toggleAnglePreview(menu.getSonar(),
                        draftRange, draftSector, draftVerticalSector, draftTiltAngle);
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.translatable(enabled
                            ? "message.create_echo_radars.angle_preview_enabled"
                            : "message.create_echo_radars.angle_preview_disabled"), true);
                }
                onClose();
            }
        }).bounds(x, topPos + 185, width, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.create_echo_radars.apply"), button -> {
            captureDraft();
            if (menu.getSonar() != null) {
                ModNetworking.sendSettings(menu.getSonar().getBlockPos(), draftRange,
                        draftSector, draftVerticalSector, draftTiltAngle, draftAutoHeight);
            }
            onClose();
        }).bounds(x, topPos + 216, width, 20).build());
    }

    private void captureDraft() {
        if (range == null) return;
        draftRange = range.intValue();
        draftSector = sector.intValue();
        draftVerticalSector = verticalSector.intValue();
        draftTiltAngle = tiltAngle.intValue();
    }

    private Component autoHeightMessage() {
        return Component.translatable("gui.create_echo_radars.auto_height",
                Component.translatable(draftAutoHeight
                        ? "config.create_echo_radars.value.on"
                        : "config.create_echo_radars.value.off"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xe0202528);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3,
                0xff394247);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 10, 0xffffff, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final String key;
        private final int min;
        private final int max;
        private final String suffix;

        private IntSlider(int x, int y, int width, String key, int min, int max, int initial, String suffix) {
            super(x, y, width, 20, Component.empty(), (initial - min) / (double) (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.suffix = suffix;
            updateMessage();
        }

        private int intValue() {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key, intValue() + suffix));
        }

        @Override
        protected void applyValue() {}
    }
}
