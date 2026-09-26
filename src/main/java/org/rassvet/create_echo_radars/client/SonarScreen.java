package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.ModNetworking;
import org.rassvet.create_echo_radars.config.SyncedServerConfig;
import org.rassvet.create_echo_radars.content.sonar.SonarBlock;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarMenu;
import org.rassvet.create_echo_radars.content.sonar.SonarOrientation;
import org.rassvet.create_echo_radars.content.sonar.SonarRangeLimit;
import org.rassvet.create_echo_radars.content.sonar.SonarType;

import java.util.function.BooleanSupplier;

public class SonarScreen extends AbstractContainerScreen<SonarMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateEchoRadars.MOD_ID, "textures/gui/sonar_settings.png");
    private static final ResourceLocation BLOCKER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateEchoRadars.MOD_ID, "textures/gui/sonar_settings_blocker.png");
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 512;
    private static final int THREE_SLIDER_PANEL_V = 256;
    private static final int CONTROLS_WIDTH = 225;
    private static final int PREVIEW_WIDTH = 162;
    private static final int SCREEN_HEIGHT = 187;
    private static final int THREE_SLIDER_SCREEN_HEIGHT = 154;
    private static final int SLIDER_WIDTH = 199;
    private static final int SLIDER_HEIGHT = 26;
    private static final int BLOCKER_WIDTH = 189;
    private static final int BLOCKER_HEIGHT = 16;
    /** The preview is angular only. Its length must never be derived from sonar range. */
    private static final double PREVIEW_CONE_LENGTH = 1.55;
    /** Center of the red forward-looking emitter in sonar.obj, relative to the block center. */
    private static final double FORWARD_PREVIEW_EMITTER_FORWARD_OFFSET = 0.4755;
    private static final double FORWARD_PREVIEW_EMITTER_UP_OFFSET = -0.176;

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
    private boolean draftAnglePreviewEnabled;
    private IconToggleButton autoHeightButton;
    private IconToggleButton anglePreviewButton;
    private IntSlider draggedSlider;
    private boolean settingsSent;

    public SonarScreen(SonarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = CONTROLS_WIDTH + PREVIEW_WIDTH;
        imageHeight = usesThreeSliderLayout(sonarType())
                ? THREE_SLIDER_SCREEN_HEIGHT : SCREEN_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        SonarType type = sonarType();
        if (!draftInitialized) {
            draftRange = menu.getRange();
            draftSector = usesThreeSliderLayout(type)
                    ? type.defaultHorizontalAngle() : menu.getSector();
            draftVerticalSector = menu.getVerticalSector();
            draftTiltAngle = SonarBlockEntity.clampTilt(type, menu.getTiltAngle());
            draftAutoHeight = menu.isAutoHeight();
            draftAnglePreviewEnabled = menu.getSonar() != null
                    && SonarDebugRenderer.isAnglePreviewEnabled(menu.getSonar());
            draftInitialized = true;
        }

        int sliderX = leftPos + 8;
        range = addRenderableWidget(new IntSlider(sliderX, topPos + 23, SLIDER_WIDTH,
                "gui.create_echo_radars.range", SonarBlockEntity.MIN_RANGE,
                SyncedServerConfig.maximumSonarRange(), draftRange, "", this::onRangeChanged));
        sector = null;
        int verticalSectorY;
        int tiltAngleY;
        if (usesThreeSliderLayout(type)) {
            draftSector = type.defaultHorizontalAngle();
            verticalSectorY = topPos + 58;
            tiltAngleY = topPos + 91;
        } else {
            sector = addRenderableWidget(new IntSlider(sliderX, topPos + 58, SLIDER_WIDTH,
                    "gui.create_echo_radars.horizontal_sector", SonarBlockEntity.MIN_ANGLE,
                    type.maximumHorizontalAngle(), draftSector, "°", this::onAngleChanged));
            verticalSectorY = topPos + 91;
            tiltAngleY = topPos + 124;
        }
        verticalSector = addRenderableWidget(new IntSlider(sliderX, verticalSectorY, SLIDER_WIDTH,
                "gui.create_echo_radars.vertical_sector", SonarBlockEntity.MIN_ANGLE,
                type.maximumVerticalAngle(), draftVerticalSector, "°", this::onAngleChanged));
        tiltAngle = addRenderableWidget(new IntSlider(sliderX, tiltAngleY, SLIDER_WIDTH,
                "gui.create_echo_radars.tilt_angle", SonarBlockEntity.minimumTilt(type),
                SonarBlockEntity.maximumTilt(type), draftTiltAngle, "°", this::refreshAnglePreview));
        clampRangeToAngles();

        int footerY = topPos + (usesThreeSliderLayout(type) ? 131 : 164);
        autoHeightButton = addRenderableWidget(new IconToggleButton(leftPos + 6, footerY,
                6, () -> draftAutoHeight, autoHeightMessage(), () -> {
                    draftAutoHeight = !draftAutoHeight;
                    autoHeightButton.setMessage(autoHeightMessage());
                }));
        anglePreviewButton = addRenderableWidget(new IconToggleButton(leftPos + 25, footerY,
                25, () -> draftAnglePreviewEnabled,
                Component.translatable("gui.create_echo_radars.angle_preview"), () -> {
                    captureDraft();
                    if (minecraft != null && menu.getSonar() != null) {
                        draftAnglePreviewEnabled = SonarDebugRenderer.toggleAnglePreview(menu.getSonar(),
                                draftRange, draftSector, draftVerticalSector, draftTiltAngle);
                        if (minecraft.player != null) {
                            minecraft.player.displayClientMessage(Component.translatable(draftAnglePreviewEnabled
                                    ? "message.create_echo_radars.angle_preview_enabled"
                                    : "message.create_echo_radars.angle_preview_disabled"), true);
                        }
                    }
                }));

        addRenderableWidget(new ApplyButton(leftPos + 192, footerY, 18, 19,
                Component.translatable("gui.create_echo_radars.apply"), this::onClose));
    }

    @Override
    public void onClose() {
        if (!settingsSent && draftInitialized && menu.getSonar() != null) {
            settingsSent = true;
            captureDraft();
            ModNetworking.sendSettings(menu.getSonar().getBlockPos(), draftRange,
                    draftSector, draftVerticalSector, draftTiltAngle, draftAutoHeight);
        }
        super.onClose();
    }

    private void captureDraft() {
        if (range == null) return;
        draftRange = range.intValue();
        draftSector = sector == null
                ? sonarType().defaultHorizontalAngle() : sector.intValue();
        draftVerticalSector = verticalSector.intValue();
        draftTiltAngle = tiltAngle.intValue();
    }

    private void refreshAnglePreview() {
        captureDraft();
        if (!draftAnglePreviewEnabled || menu.getSonar() == null) return;
        draftAnglePreviewEnabled = SonarDebugRenderer.updateAnglePreview(menu.getSonar(),
                draftRange, draftSector, draftVerticalSector, draftTiltAngle);
    }

    private void onRangeChanged() {
        clampRangeToAngles();
        refreshAnglePreview();
    }

    private void onAngleChanged() {
        clampRangeToAngles();
        refreshAnglePreview();
    }

    private void clampRangeToAngles() {
        if (range == null || verticalSector == null) return;
        int maximumRange = SonarRangeLimit.effectiveMaximumRange(sonarType(),
                currentHorizontalAngle(), currentVerticalAngle(),
                SyncedServerConfig.maximumSonarRange(), SyncedServerConfig.angleRangeReduction());
        range.clampTo(maximumRange);
    }

    private Component autoHeightMessage() {
        return Component.translatable("gui.create_echo_radars.auto_height",
                Component.translatable(draftAutoHeight
                        ? "config.create_echo_radars.value.on"
                        : "config.create_echo_radars.value.off"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderControlPanel(graphics);
        renderPreviewPanel(graphics);
        int previewLeft = leftPos + CONTROLS_WIDTH + 3;
        graphics.enableScissor(previewLeft, topPos + 16,
                leftPos + imageWidth - 1, topPos + imageHeight - 1);
        if (sonarType() == SonarType.FORWARD_LOOKING_F) {
            renderSonarModel(graphics, partialTick);
            renderAngleVolume(graphics, currentHorizontalAngle(), currentVerticalAngle(), currentTiltAngle());
        } else {
            renderAngleVolume(graphics, currentHorizontalAngle(), currentVerticalAngle(), currentTiltAngle());
            renderSonarModel(graphics, partialTick);
        }
        graphics.drawString(font, "H " + currentHorizontalAngle() + "°",
                leftPos + CONTROLS_WIDTH + 8, topPos + 23, 0xff8ed8ff, false);
        graphics.drawString(font, "V " + currentVerticalAngle() + "°",
                leftPos + CONTROLS_WIDTH + 8, topPos + 34, 0xff83f2b1, false);
        graphics.disableScissor();
    }

    private void renderControlPanel(GuiGraphics graphics) {
        int sourceV = usesThreeSliderLayout(sonarType()) ? THREE_SLIDER_PANEL_V : 0;
        graphics.blit(TEXTURE, leftPos, topPos, 0, sourceV,
                CONTROLS_WIDTH, imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private SonarType sonarType() {
        return menu.getSonar() == null
                ? SonarType.FORWARD_LOOKING_F : menu.getSonar().getSonarType();
    }

    private static boolean usesThreeSliderLayout(SonarType type) {
        return type == SonarType.SIDE_SCAN_D || type == SonarType.MECHANICAL_IMAGING_C;
    }

    private void renderPreviewPanel(GuiGraphics graphics) {
        int x = leftPos + CONTROLS_WIDTH + 2;
        int right = leftPos + imageWidth;
        graphics.fill(x, topPos, right, topPos + imageHeight, 0xee151c22);
        graphics.fill(x, topPos, right, topPos + 16, 0xff6f899f);
        graphics.fill(x, topPos, x + 1, topPos + imageHeight, 0xffa8c4df);
        graphics.fill(right - 1, topPos, right, topPos + imageHeight, 0xff05080a);
        graphics.fill(x, topPos + imageHeight - 1, right, topPos + imageHeight, 0xff05080a);
        Component previewTitle = Component.translatable("gui.create_echo_radars.sector_preview");
        graphics.drawCenteredString(font, previewTitle, x + (right - x) / 2, topPos + 4, 0xfff2f5f7);
    }

    private void renderAngleVolume(GuiGraphics graphics, int horizontalAngle,
                                   int verticalAngle, int tiltAngle) {
        if (menu.getSonar() == null) return;
        SonarBlockEntity sonar = menu.getSonar();
        PreviewPose previewPose = previewPose(sonar, tiltAngle);
        PoseStack poseStack = graphics.pose();
        graphics.flush();
        poseStack.pushPose();
        applyPreviewTransform(poseStack, previewYaw(sonar), sonar.getSonarType());
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        SonarDebugRenderer.drawPreviewShell(matrix, previewPose.origin, previewPose.orientation,
                PREVIEW_CONE_LENGTH,
                horizontalAngle, verticalAngle, tiltAngle, sonar.getSonarType());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private void renderSonarModel(GuiGraphics graphics, float partialTick) {
        if (minecraft == null || menu.getSonar() == null) return;
        SonarBlockEntity sonar = menu.getSonar();
        BlockState state = sonar.getBlockState();
        PoseStack poseStack = graphics.pose();
        graphics.flush();
        poseStack.pushPose();
        applyPreviewTransform(poseStack, previewYaw(sonar), sonar.getSonarType());
        if (sideScanWorldTransformIsUpsideDown(sonar)) {
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));
            poseStack.translate(-0.5, -0.5, -0.5);
        }

        RenderSystem.enableDepthTest();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        minecraft.getBlockRenderer().renderSingleBlock(state, poseStack, buffers,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        if (sonar.getSonarType() == SonarType.MECHANICAL_IMAGING_C) {
            renderMechanicalRotatingPart(sonar, partialTick, poseStack, buffers);
        }
        buffers.endBatch();
        poseStack.popPose();
    }

    private void applyPreviewTransform(PoseStack poseStack, float yaw, SonarType sonarType) {
        boolean centered = sonarType == SonarType.SIDE_SCAN_D
                || sonarType == SonarType.ECHO_SOUNDER_A
                || sonarType == SonarType.MECHANICAL_IMAGING_C;
        double previewX = centered ? PREVIEW_WIDTH / 2.0 : 46.0;
        double previewY = usesThreeSliderLayout(sonarType) ? 79.0 : 96.0;
        poseStack.translate(leftPos + CONTROLS_WIDTH + previewX, topPos + previewY, 100.0);
        poseStack.scale(48.0f, -48.0f, 48.0f);
        poseStack.mulPose(Axis.XP.rotationDegrees(26.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-0.5, -0.5, -0.5);
    }

    private static float previewYaw(SonarBlockEntity sonar) {
        Direction facing = sonar.getBlockState().getValue(SonarBlock.FACING);
        double heading = Math.toDegrees(Math.atan2(facing.getStepX(), facing.getStepZ()));
        double forwardFacingOffset = sonar.getSonarType() == SonarType.FORWARD_LOOKING_F ? 180.0 : 0.0;
        return (float) (45.0 - heading + forwardFacingOffset);
    }

    private static PreviewPose previewPose(SonarBlockEntity sonar, int tiltAngle) {
        Direction facing = sonar.getBlockState().getValue(SonarBlock.FACING);
        SonarOrientation base = new SonarOrientation(
                Vec3.atLowerCornerOf(facing.getNormal()),
                Vec3.atLowerCornerOf(facing.getClockWise().getNormal()),
                new Vec3(0, 1, 0));
        if (sonar.isUpsideDown() ^ sideScanWorldTransformIsUpsideDown(sonar)) {
            base = new SonarOrientation(base.forward().scale(-1),
                    base.right(), base.up().scale(-1));
        }

        float mechanicalAngle = sonar.mechanicalAngle();
        SonarOrientation unTilted = switch (sonar.getSonarType()) {
            case ECHO_SOUNDER_A -> new SonarOrientation(
                    base.up().scale(-1), base.right(), base.forward());
            case MECHANICAL_IMAGING_C -> new SonarOrientation(
                    base.direction(mechanicalAngle, 0),
                    base.direction(mechanicalAngle + 90, 0), base.up());
            case SIDE_SCAN_D, FORWARD_LOOKING_F -> base;
        };
        Vec3 origin = sonar.getSonarType() == SonarType.FORWARD_LOOKING_F
                ? new Vec3(0.5, 0.5, 0.5)
                .add(unTilted.forward().scale(FORWARD_PREVIEW_EMITTER_FORWARD_OFFSET))
                .add(unTilted.up().scale(FORWARD_PREVIEW_EMITTER_UP_OFFSET))
                : SonarBlockEntity.emitterPosition(new Vec3(0.5, 0.5, 0.5),
                sonar.getSonarType(), unTilted, mechanicalAngle);
        SonarOrientation orientation = sonar.getSonarType() == SonarType.SIDE_SCAN_D
                ? unTilted : SonarBlockEntity.applyTilt(unTilted, tiltAngle);
        return new PreviewPose(origin, orientation);
    }

    private static boolean sideScanWorldTransformIsUpsideDown(SonarBlockEntity sonar) {
        return sonar.getSonarType() == SonarType.SIDE_SCAN_D
                && SonarOrientation.of(sonar).up().y < -1.0e-3;
    }

    private void renderMechanicalRotatingPart(SonarBlockEntity sonar, float partialTick,
                                              PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        BakedModel rotatingModel = minecraft.getModelManager()
                .getModel(MechanicalSonarRenderer.ROTATING_MODEL);
        Direction facing = sonar.getBlockState().getValue(SonarBlock.FACING);
        float placementAngle = switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-placementAngle));
        if (sonar.isUpsideDown()) poseStack.mulPose(Axis.XP.rotationDegrees(180));
        poseStack.mulPose(Axis.YP.rotationDegrees(-sonar.getMechanicalAngle(partialTick)));
        poseStack.translate(-0.5, -0.5, -0.5);
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), buffers.getBuffer(RenderType.cutout()),
                sonar.getBlockState(), rotatingModel, 1.0f, 1.0f, 1.0f,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderType.cutout());
        poseStack.popPose();
    }

    private int currentHorizontalAngle() {
        return sector == null ? draftSector : sector.intValue();
    }

    private int currentVerticalAngle() {
        return verticalSector == null ? draftVerticalSector : verticalSector.intValue();
    }

    private int currentTiltAngle() {
        return tiltAngle == null ? draftTiltAngle : tiltAngle.intValue();
    }

    private record PreviewPose(Vec3 origin, SonarOrientation orientation) {}

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (217 - font.width(title)) / 2, 4, 0xfff2f5f7, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clampRangeToAngles();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (autoHeightButton != null && autoHeightButton.isHovered()) {
            graphics.renderTooltip(font, autoHeightMessage(), mouseX, mouseY);
        } else if (anglePreviewButton != null && anglePreviewButton.isHovered()) {
            graphics.renderTooltip(font,
                    Component.translatable("gui.create_echo_radars.angle_preview"), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        IntSlider clickedSlider = button == 0 ? sliderAt(mouseX, mouseY) : null;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        draggedSlider = handled ? clickedSlider : null;
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && draggedSlider != null) {
            draggedSlider.dragTo(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        if (button == 0) draggedSlider = null;
        return handled;
    }

    private IntSlider sliderAt(double mouseX, double mouseY) {
        if (range != null && range.isMouseOver(mouseX, mouseY)) return range;
        if (sector != null && sector.isMouseOver(mouseX, mouseY)) return sector;
        if (verticalSector != null && verticalSector.isMouseOver(mouseX, mouseY)) return verticalSector;
        if (tiltAngle != null && tiltAngle.isMouseOver(mouseX, mouseY)) return tiltAngle;
        return null;
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final String key;
        private final int min;
        private final int max;
        private final String suffix;
        private final Runnable onValueChanged;
        private int availableMaximum;
        private boolean dragging;

        private IntSlider(int x, int y, int width, String key, int min, int max,
                          int initial, String suffix, Runnable onValueChanged) {
            super(x, y, width, SLIDER_HEIGHT, Component.empty(),
                    (Mth.clamp(initial, min, max) - min) / (double) (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.availableMaximum = max;
            this.suffix = suffix;
            this.onValueChanged = onValueChanged;
            updateMessage();
        }

        private int intValue() {
            return min + (int) Math.round(value * (max - min));
        }

        private void clampTo(int maximumValue) {
            int clampedMaximum = Mth.clamp(maximumValue, min, max);
            availableMaximum = clampedMaximum;
            if (intValue() <= clampedMaximum) return;
            value = (clampedMaximum - min) / (double) (max - min);
            updateMessage();
        }

        private void setValueFromMouse(double mouseX) {
            double newValue = Mth.clamp((mouseX - (getX() + 5.0)) / (width - 15.0), 0.0, 1.0);
            if (newValue == value) return;
            value = newValue;
            updateMessage();
            applyValue();
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            dragging = true;
            setValueFromMouse(mouseX);
        }

        private void dragTo(double mouseX) {
            dragging = true;
            setValueFromMouse(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (dragging) setValueFromMouse(mouseX);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button,
                                    double dragX, double dragY) {
            if (button != 0 || !dragging) return false;
            setValueFromMouse(mouseX);
            return true;
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            dragging = false;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blit(TEXTURE, getX(), getY(), 8, 23,
                    SLIDER_WIDTH, SLIDER_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            if (availableMaximum < max) {
                int limitHandleX = getX() + 5 + (int) Math.round(
                        (availableMaximum - min) * (width - 15) / (double) (max - min));
                int blockedStart = limitHandleX + 5;
                int blockedWidth = getX() + 5 + BLOCKER_WIDTH - blockedStart;
                graphics.blit(BLOCKER_TEXTURE, blockedStart, getY() + 5,
                        blockedStart - (getX() + 5), 0,
                        blockedWidth, BLOCKER_HEIGHT, BLOCKER_WIDTH, BLOCKER_HEIGHT);
            }
            int handleX = getX() + 5 + (int) Math.round(value * (width - 15));
            int sourceY = isHoveredOrFocused() ? 208 : 187;
            graphics.blit(TEXTURE, handleX, getY() + 4, 0, sourceY,
                    5, 19, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, getMessage(),
                    getX() + width / 2, getY() + 9, 0xffffffff);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key, intValue() + suffix));
        }

        @Override
        protected void applyValue() {
            onValueChanged.run();
        }
    }

    private static final class IconToggleButton extends AbstractButton {
        private final int iconU;
        private final BooleanSupplier selected;
        private final Runnable onPress;
        private boolean pressed;

        private IconToggleButton(int x, int y, int iconU, BooleanSupplier selected,
                                 Component message, Runnable onPress) {
            super(x, y, 18, 19, message);
            this.iconU = iconU;
            this.selected = selected;
            this.onPress = onPress;
        }

        @Override
        public void onPress() {
            onPress.run();
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            pressed = true;
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            if (pressed && isMouseOver(mouseX, mouseY)) onPress();
            pressed = false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int sourceU = pressed || selected.getAsBoolean() ? iconU + 57 : iconU;
            int sourceV = pressed ? 188
                    : selected.getAsBoolean() || isHoveredOrFocused() ? 207 : 188;
            graphics.blit(TEXTURE, getX(), getY(), sourceU, sourceV,
                    18, 19, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }

    private static final class ApplyButton extends AbstractButton {
        private final Runnable onPress;

        private ApplyButton(int x, int y, int width, int height, Component message, Runnable onPress) {
            super(x, y, width, height, message);
            this.onPress = onPress;
        }

        @Override
        public void onPress() {
            onPress.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int sourceY = isHoveredOrFocused() ? 188 : 207;
            graphics.blit(TEXTURE, getX(), getY(), 44, sourceY,
                    18, 19, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }
}
