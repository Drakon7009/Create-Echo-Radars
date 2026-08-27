package org.rassvet.create_echo_radars.client;

import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.monitor.MonitorSelectionPacket;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.rassvet.create_echo_radars.content.sonar.SonarDisplayLayout;
import org.rassvet.create_echo_radars.content.sonar.SonarDisplayProjection;
import org.rassvet.create_echo_radars.content.sonar.SonarMath;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorDimensions;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorExtension;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorSnapshot;
import org.rassvet.create_echo_radars.content.sonar.SonarOrientation;

public final class SonarMonitorScreen extends Screen {
    private static final ResourceLocation MONITOR_GUI = ResourceLocation.fromNamespaceAndPath(
            "create_radar", "textures/gui/monitor_gui.png");
    private static final int TARGET_TEXTURE_SIZE = 512;
    private static final int TARGET_UI_PIXELS = 900;
    private static final int GRID_MARGIN_PIXELS = 21;
    private static final double TRACK_HIT_RADIUS = 12;
    private static final int TRACK_TEXTURE_PIXELS = 256;
    private static final int MINIMUM_TRACK_QUAD_PIXELS = 8;

    private final BlockPos controllerPos;
    private int uiSize;
    private float uiScale;
    private int left;
    private int top;
    private float displayLeft;
    private float displayTop;
    private float displayWidth;
    private float displayHeight;
    private float displayScale;

    public SonarMonitorScreen(BlockPos controllerPos) {
        super(Component.translatable("block.create_echo_radars.sonar"));
        this.controllerPos = controllerPos.immutable();
    }

    @Override
    protected void init() {
        super.init();
        recalculateUiBounds();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        recalculateUiBounds();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawPanelBackground(graphics);
        MonitorBlockEntity monitor = getController();
        SonarMonitorSnapshot snapshot = snapshot(monitor);
        if (monitor == null || snapshot == null || !monitor.isLinked()) {
            graphics.drawCenteredString(font, Component.translatable("create_radar.monitor.offline"),
                    width / 2, height / 2 - 4, 0xffffff);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        updateDisplayBounds(monitor);
        SonarDisplayLayout.Area area = displayArea(monitor);
        String hoveredTrackId = findTrack(monitor, snapshot, mouseX, mouseY);
        var poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(displayLeft - area.left() * displayScale,
                displayTop - area.bottom() * displayScale, 10);
        poseStack.scale(displayScale, -displayScale, 1);
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        float trackQuadPixels = Math.max(MINIMUM_TRACK_QUAD_PIXELS,
                Math.round(TRACK_TEXTURE_PIXELS * uiScale));
        SonarMonitorRenderer.renderScreen(monitor, snapshot, poseStack, graphics.bufferSource(),
                partialTick, hoveredTrackId, trackQuadPixels * 0.5f / displayScale);
        poseStack.popPose();
        graphics.flush();
        graphics.drawCenteredString(font, Component.translatable("create_radar.monitor.click_hint"),
                width / 2, top + uiSize + 6, 0xa0a0a0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !insideDisplay(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        MonitorBlockEntity monitor = getController();
        SonarMonitorSnapshot snapshot = snapshot(monitor);
        if (monitor == null || snapshot == null) return false;
        String selectedId = findTrack(monitor, snapshot, mouseX, mouseY);
        monitor.selectedEntity = selectedId;
        MonitorSelectionPacket.send(controllerPos, selectedId);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private MonitorBlockEntity getController() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        BlockEntity blockEntity = minecraft.level.getBlockEntity(controllerPos);
        if (!(blockEntity instanceof MonitorBlockEntity monitor)) return null;
        return monitor.isController() ? monitor : monitor.getController();
    }

    private static SonarMonitorSnapshot snapshot(MonitorBlockEntity monitor) {
        return monitor == null ? null
                : ((SonarMonitorExtension) monitor).createEchoRadars$getSonarSnapshot();
    }

    private void recalculateUiBounds() {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        if (guiScale <= 0) guiScale = 1;
        uiSize = (int) Math.round(TARGET_UI_PIXELS / guiScale);
        int maximumSize = Math.min(width, height) - 20;
        uiSize = Mth.clamp(uiSize, 120, Math.max(120, maximumSize));
        uiScale = uiSize / (float) TARGET_TEXTURE_SIZE;
        left = (width - uiSize) / 2;
        top = (height - uiSize) / 2;
    }

    private void drawPanelBackground(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        graphics.blit(MONITOR_GUI, left, top, 0, 0, uiSize, uiSize, uiSize, uiSize);
        RenderSystem.disableBlend();
    }

    private void updateDisplayBounds(MonitorBlockEntity monitor) {
        SonarMonitorDimensions dimensions = ((SonarMonitorExtension) monitor)
                .createEchoRadars$getMonitorDimensions();
        float frameMargin = Math.round(GRID_MARGIN_PIXELS * uiScale);
        float availableWidth = Math.max(1, uiSize - frameMargin * 2);
        float availableHeight = Math.max(1, uiSize - frameMargin * 2);
        displayScale = Math.min(availableWidth / dimensions.width(), availableHeight / dimensions.height());
        displayWidth = dimensions.width() * displayScale;
        displayHeight = dimensions.height() * displayScale;
        displayLeft = left + frameMargin + (availableWidth - displayWidth) * 0.5f;
        displayTop = top + frameMargin + (availableHeight - displayHeight) * 0.5f;
    }

    private boolean insideDisplay(double mouseX, double mouseY) {
        return mouseX >= displayLeft && mouseX <= displayLeft + displayWidth
                && mouseY >= displayTop && mouseY <= displayTop + displayHeight;
    }

    private String findTrack(MonitorBlockEntity monitor, SonarMonitorSnapshot snapshot,
                             double mouseX, double mouseY) {
        if (!insideDisplay(mouseX, mouseY)) return null;
        SonarDisplayLayout.Area area = displayArea(monitor);
        SonarDisplayLayout.Area plotArea = snapshot.sonarType()
                == org.rassvet.create_echo_radars.content.sonar.SonarType.SIDE_SCAN_D
                ? sideScanPlotArea(area) : area;
        int displayRange = snapshot.effectiveDisplayRange();
        SonarOrientation orientation = snapshot.displayOrientation();
        Vec3 trackOrigin = snapshot.displayOrigin();
        String bestId = null;
        double bestDistance = TRACK_HIT_RADIUS * TRACK_HIT_RADIUS;
        for (RadarTrack track : monitor.getTracks()) {
            Vec3 relative = track.position().subtract(trackOrigin);
            if (relative.length() > snapshot.range()) continue;
            SonarMath.Projection projection = SonarMath.project(relative, orientation);
            double normalizedRange = projection.range() / Math.max(1, displayRange);
            if (snapshot.sonarType()
                    != org.rassvet.create_echo_radars.content.sonar.SonarType.MECHANICAL_IMAGING_C
                    && !SonarDisplayLayout.trackInsideDisplayRange(
                    projection.range(), displayRange)) continue;
            SonarDisplayProjection.Point point;
            switch (snapshot.sonarType()) {
                case FORWARD_LOOKING_F -> {
                    if (!SonarMath.insideCone(relative, orientation, snapshot.horizontalSector(),
                            snapshot.verticalSector(), snapshot.range())) continue;
                    point = SonarDisplayProjection.project(normalizedRange,
                            Math.toRadians(projection.bearingDegrees()), snapshot.horizontalSector());
                }
                case MECHANICAL_IMAGING_C -> {
                    SonarMath.Projection horizontalProjection = SonarMath.projectHorizontal(relative, orientation);
                    if (!SonarDisplayLayout.trackInsideDisplayRange(
                            horizontalProjection.range(), displayRange)) continue;
                    double horizontalRange = horizontalProjection.range() / Math.max(1, displayRange);
                    double angle = Math.toRadians(horizontalProjection.bearingDegrees());
                    point = new SonarDisplayProjection.Point(Math.sin(angle) * horizontalRange * 0.94,
                            Math.cos(angle) * horizontalRange * 0.94);
                }
                case ECHO_SOUNDER_A -> point = new SonarDisplayProjection.Point(0.9,
                        1 - normalizedRange * 1.88);
                case SIDE_SCAN_D -> {
                    SideScanDataPosition position = ClientConfig.sideScanDataPosition();
                    double signedDistance = Math.copySign(normalizedRange * 0.94,
                            projection.bearingDegrees());
                    point = switch (position) {
                        case BOTTOM -> new SonarDisplayProjection.Point(signedDistance, -0.9);
                        case TOP -> new SonarDisplayProjection.Point(signedDistance, 0.9);
                        case RIGHT -> new SonarDisplayProjection.Point(0.9, signedDistance);
                        case LEFT -> new SonarDisplayProjection.Point(-0.9, signedDistance);
                    };
                }
                default -> throw new IllegalStateException();
            }
            double sourceX = plotArea.centerX() + point.x() * plotArea.width() * 0.5;
            double sourceZ = plotArea.centerZ() + point.z() * plotArea.height() * 0.5;
            double screenX = displayLeft + (sourceX - area.left()) * displayScale;
            double screenY = displayTop + (sourceZ - area.bottom()) * displayScale;
            double distance = Math.pow(screenX - mouseX, 2) + Math.pow(screenY - mouseY, 2);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestId = track.id();
            }
        }
        return bestId;
    }

    private static SonarDisplayLayout.Area displayArea(MonitorBlockEntity monitor) {
        SonarMonitorDimensions dimensions = ((SonarMonitorExtension) monitor)
                .createEchoRadars$getMonitorDimensions();
        return SonarDisplayLayout.area(dimensions);
    }

    private static SonarDisplayLayout.Area sideScanPlotArea(SonarDisplayLayout.Area area) {
        float inset = area.minSize() * 0.115f;
        return new SonarDisplayLayout.Area(area.left() + inset, area.right() - inset,
                area.bottom() + inset, area.top() - inset);
    }
}
