package org.rassvet.create_echo_radars.client;

import com.happysg.radar.block.monitor.MonitorBlock;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.monitor.MonitorSprite;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.rassvet.create_echo_radars.content.sonar.MechanicalSweepBuffer;
import org.rassvet.create_echo_radars.content.sonar.SonarDisplayProjection;
import org.rassvet.create_echo_radars.content.sonar.SonarDisplayLayout;
import org.rassvet.create_echo_radars.content.sonar.SonarFrame;
import org.rassvet.create_echo_radars.content.sonar.SonarMath;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorDimensions;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorExtension;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorSnapshot;
import org.rassvet.create_echo_radars.content.sonar.SonarOrientation;
import org.rassvet.create_echo_radars.content.sonar.SonarReturn;
import org.rassvet.create_echo_radars.content.sonar.SonarRotation;
import org.rassvet.create_echo_radars.config.SyncedServerConfig;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SonarMonitorRenderer {
    private static final float BACKGROUND_DEPTH = 0.940f;
    private static final float SECTOR_DEPTH = 0.943f;
    private static final float GRID_DEPTH = 0.954f;
    private static final float SWEEP_DEPTH = 0.949f;
    private static final float ECHO_DEPTH = 0.952f;
    private static final float TRACK_DEPTH = 0.957f;
    private static final float LABEL_DEPTH = 0.975f;
    private static final float LABEL_SCALE = 1.45f;
    private static final float SIDE_SCAN_LABEL_SCALE = 1.70f;
    private static final float SIDE_SCAN_LABEL_INSET = 0.70f;
    private static final int ARC_STEPS = 48;
    private static final int BACKGROUND_RASTER_RESOLUTION = 256;
    private static final float REVEAL_COMPLETE_EPSILON = 1.0e-4f;
    private static final float REVEAL_EDGE_MIN_WIDTH = 0.035f;
    private static final float REVEAL_EDGE_RANGE_CELLS = 8;
    private static final int MAX_REVEAL_STATES = 256;
    private static final double REVEAL_STATE_TTL_TICKS = 80;
    private static final int MAX_FRAME_ECHO_LAYOUTS = 512;
    private static final int MAX_CLIENT_HISTORIES = 64;
    private static final int MAX_LOCAL_FRAMES = 512;
    private static final float MECHANICAL_SAMPLE_DEGREES =
            SonarRotation.MECHANICAL_SCAN_STEP_DEGREES;
    private static final float MECHANICAL_PREFETCH_ARC_DEGREES = 120;
    private static final Map<RevealKey, RevealState> REVEAL_STATES = new HashMap<>();
    private static final Map<FrameLayoutKey, CachedFrameEchoLayout> FRAME_ECHO_LAYOUTS = new HashMap<>();
    private static final Map<ClientHistoryKey, ClientFrameHistory> CLIENT_HISTORIES = new HashMap<>();
    private static long clientHistoryAccessSequence;
    private static long frameLayoutAccessSequence;

    private SonarMonitorRenderer() {}

    public static void render(MonitorBlockEntity monitor, SonarMonitorSnapshot snapshot, PoseStack poseStack,
                              MultiBufferSource buffers, float partialTick) {
        setupTransform(poseStack, monitor.getBlockState().getValue(MonitorBlock.FACING));
        renderContents(monitor, snapshot, poseStack, buffers, partialTick, null, false,
                Float.NaN);
    }

    public static void renderScreen(MonitorBlockEntity monitor, SonarMonitorSnapshot snapshot,
                                    PoseStack poseStack, MultiBufferSource buffers, float partialTick,
                                    String hoveredTrackId, float trackRadius) {
        renderContents(monitor, snapshot, poseStack, buffers, partialTick, hoveredTrackId, true,
                trackRadius);
    }

    private static void renderContents(MonitorBlockEntity monitor, SonarMonitorSnapshot snapshot,
                                       PoseStack poseStack, MultiBufferSource buffers, float partialTick,
                                       String hoveredTrackId, boolean overrideHover,
                                       float screenTrackRadius) {
        SonarDisplayLayout.Area area = displayArea(monitor);
        int displayRange = displayRange(snapshot);
        SonarPalette palette = ClientConfig.palette();

        if (snapshot.sonarType() != org.rassvet.create_echo_radars.content.sonar.SonarType.FORWARD_LOOKING_F) {
            renderSpecialized(monitor, snapshot, poseStack, buffers, palette, area, displayRange,
                    partialTick, overrideHover);
            return;
        }

        renderBackground(poseStack, buffers, palette, area, snapshot.horizontalSector());
        renderEchoes(monitor, snapshot, poseStack, buffers, palette,
                area, displayRange, partialTick);
        renderGrid(snapshot, poseStack, buffers, area);
        renderTracks(monitor, snapshot, poseStack, buffers, area, displayRange,
                hoveredTrackId, overrideHover, screenTrackRadius);
        if (area.minSize() >= 2) {
            renderLabels(snapshot, poseStack, buffers, area, displayRange);
        }
    }

    private static void setupTransform(PoseStack poseStack, Direction direction) {
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YN.rotationDegrees(direction.toYRot()));
        poseStack.translate(-0.5, -0.5, -0.5);
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        poseStack.translate(-0.5, -0.5, -0.5);
    }

    private static void renderBackground(PoseStack poseStack, MultiBufferSource buffers, SonarPalette palette,
                                         SonarDisplayLayout.Area area, float sectorDegrees) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.debugQuads());
        colorQuad(buffer, poseStack.last(), area.left(), area.right(), area.bottom(), area.top(), BACKGROUND_DEPTH,
                0.002f, 0.003f, 0.008f, 0.96f);
        SonarPalette.Rgb color = palette.background();
        double halfSector = Math.toRadians(sectorDegrees / 2.0);
        Vec3 apex = displayPoint(SonarDisplayProjection.project(0, 0, sectorDegrees), area);
        Vec3 previous = displayPoint(SonarDisplayProjection.project(1, -halfSector, sectorDegrees),
                area);
        for (int step = 1; step <= ARC_STEPS; step++) {
            double angle = -halfSector + 2 * halfSector * step / ARC_STEPS;
            Vec3 next = displayPoint(SonarDisplayProjection.project(1, angle, sectorDegrees),
                    area);
            colorWedge(buffer, poseStack.last(), (float) apex.x, (float) apex.z, previous, next, SECTOR_DEPTH,
                    color.red(), color.green(), color.blue(), 0.96f);
            previous = next;
        }
    }

    private static void renderGrid(SonarMonitorSnapshot snapshot, PoseStack poseStack, MultiBufferSource buffers,
                                   SonarDisplayLayout.Area area) {
        VertexConsumer buffer = buffers.getBuffer(SonarRenderTypes.gridOverlay());
        PixelGrid pixelGrid = PixelGrid.forArea(area);
        BitSet occupiedPixels = new BitSet(pixelGrid.pixelCount());
        double halfSector = Math.toRadians(snapshot.horizontalSector() / 2.0);
        for (int ring = 1; ring <= 4; ring++) {
            float distance = ring / 4f;
            Vec3 previous = displayPoint(SonarDisplayProjection.project(distance, -halfSector,
                    snapshot.horizontalSector()), area);
            for (int step = 1; step <= ARC_STEPS; step++) {
                double angle = -halfSector + 2 * halfSector * step / ARC_STEPS;
                Vec3 next = displayPoint(SonarDisplayProjection.project(distance, angle,
                        snapshot.horizontalSector()), area);
                pixelLine(buffer, poseStack.last(), pixelGrid, occupiedPixels, previous, next,
                        GRID_DEPTH, 0.72f, 0.76f, 0.78f, 0.55f);
                previous = next;
            }
        }
        for (int ray = 0; ray <= 4; ray++) {
            double angle = -halfSector + 2 * halfSector * ray / 4.0;
            Vec3 apex = displayPoint(SonarDisplayProjection.project(0, angle,
                    snapshot.horizontalSector()), area);
            Vec3 edge = displayPoint(SonarDisplayProjection.project(1, angle,
                    snapshot.horizontalSector()), area);
            pixelLine(buffer, poseStack.last(), pixelGrid, occupiedPixels, apex, edge,
                    GRID_DEPTH, 0.72f, 0.76f, 0.78f, ray == 2 ? 0.72f : 0.55f);
        }
    }

    private static void renderEchoes(MonitorBlockEntity monitor, SonarMonitorSnapshot snapshot,
                                     PoseStack poseStack, MultiBufferSource buffers, SonarPalette palette,
                                     SonarDisplayLayout.Area area, int displayRange, float partialTick) {
        // The Create: Radars background texture is intentionally very dark.
        // Tinting it multiplies sonar colours by that darkness, making even
        // strong echoes look black. This untextured POSITION_COLOR layer
        // preserves the actual sonar palette.
        VertexConsumer points = buffers.getBuffer(RenderType.debugQuads());
        long gameTime = monitor.getLevel().getGameTime();
        float rangeWidth = SonarDisplayProjection.echoRangeHalfWidth(displayRange, ClientConfig.pointGaps());
        float revealBand = Math.max(REVEAL_EDGE_MIN_WIDTH, rangeWidth * REVEAL_EDGE_RANGE_CELLS);
        ClientFrameHistory clientHistory = clientHistory(monitor, snapshot);
        List<SonarFrame> frames = clientHistory.frames();
        SonarFrame activeFrame = null;
        long latestCompletedEpoch = Long.MIN_VALUE;
        for (SonarFrame frame : frames) {
            if (frame.completedTick() == 0) activeFrame = frame;
            else latestCompletedEpoch = Math.max(latestCompletedEpoch, frame.epoch());
        }
        float revealSpeed = SonarDisplayLayout.revealProgressPerTick(
                SyncedServerConfig.blocksPerTick(), snapshot.range());
        SonarFrame latestCompletedFrame = null;
        for (SonarFrame frame : frames) {
            if (frame.completedTick() != 0 && frame.epoch() == latestCompletedEpoch) {
                latestCompletedFrame = frame;
                break;
            }
        }
        RevealState latestCompletedState = latestCompletedFrame == null ? null
                : smoothRevealState(monitor, latestCompletedFrame, gameTime, partialTick, revealSpeed, true);
        boolean nextAnimationAllowed = latestCompletedState == null || latestCompletedState.fullyRevealed();
        RevealState activeState = activeFrame == null ? null
                : smoothRevealState(monitor, activeFrame, gameTime, partialTick,
                        revealSpeed, nextAnimationAllowed);
        float activeRevealProgress = activeState == null ? 0 : activeState.progress;
        if (activeFrame != null && activeRevealProgress >= 1 - REVEAL_COMPLETE_EPSILON) {
            clientHistory.markFullyRefreshed(activeFrame.epoch());
        } else if (latestCompletedFrame != null && latestCompletedState != null
                && latestCompletedState.fullyRevealed()) {
            clientHistory.markFullyRefreshed(latestCompletedFrame.epoch());
        }
        int oldPixelLifetime = ClientConfig.oldPixelLifetimeTicks();
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            SonarFrame frame = frames.get(frameIndex);
            boolean completed = frame.completedTick() != 0;
            boolean latestCompleted = frame.epoch() == latestCompletedEpoch;
            if (completed && SonarDisplayLayout.hideOldFrameAfterFullRefresh(
                    ClientConfig.clearOldPixelsWhenRefreshed(), frame.epoch(),
                    clientHistory.fullyRefreshedEpoch())) continue;
            if (completed && oldPixelLifetime <= 0 && !latestCompleted) continue;
            RevealState revealState;
            if (frame == activeFrame) {
                revealState = activeState;
            } else if (frame == latestCompletedFrame) {
                revealState = latestCompletedState;
            } else {
                revealState = smoothRevealState(monitor, frame, gameTime, partialTick, revealSpeed, true);
            }
            float revealProgress = revealState == null ? 0 : revealState.progress;
            long lifetimeAge = revealState == null || !revealState.fullyRevealed()
                    ? 0 : (long) Math.floor(Math.max(0,
                    gameTime + partialTick - revealState.fullyRevealedAt));
            List<SonarReturn> returns = frame.returns();
            FrameEchoLayout frameLayout = echoLayout(monitor, snapshot, frame);
            for (int returnIndex = 0; returnIndex < returns.size(); returnIndex++) {
                if (!frameLayout.visible()[returnIndex]) continue;
                SonarReturn sonarReturn = returns.get(returnIndex);
                if (sonarReturn.rangeBin() >= displayRange) continue;
                float revealAlpha = revealAlpha(sonarReturn.normalizedDistance(), revealProgress, revealBand);
                if (revealAlpha <= 0) continue;
                float frameAlpha = 1;
                if (completed) {
                    boolean newAnimationActive = activeFrame != null && nextAnimationAllowed;
                    float newSweepAlpha = !newAnimationActive ? 0 : revealAlpha(
                            sonarReturn.normalizedDistance(), activeRevealProgress, revealBand);
                    frameAlpha = SonarDisplayLayout.oldFrameAlpha(oldPixelLifetime,
                            lifetimeAge, newAnimationActive,
                            newSweepAlpha, latestCompleted);
                    if (frameAlpha <= 0) continue;
                }
                float grainNoise = noise(sonarReturn.beam() / 3 * 7349
                        ^ sonarReturn.rangeBin() / 2 * 9151 ^ (int) frame.epoch());
                float speckle = ClientConfig.speckle() * 0.2f;
                float grain = 1 - speckle + grainNoise * speckle * 2;
                float linearStrength = Math.max(0, Math.min(1,
                        sonarReturn.intensity() * grain * ClientConfig.gain()));
                // Sonar palettes are display-referred. Gamma expansion keeps
                // weak but confirmed echoes readable on the dark monitor.
                float strength = (float) Math.pow(linearStrength, 0.55);
                renderPixel(points, poseStack.last(), area, sonarReturn,
                        frameLayout.bearings()[returnIndex],
                        frameLayout.angularResolutions()[returnIndex], snapshot, displayRange,
                        ECHO_DEPTH, palette.color(strength), frameAlpha * revealAlpha);
            }
        }
    }

    private static void renderSpecialized(MonitorBlockEntity monitor, SonarMonitorSnapshot snapshot,
                                          PoseStack poseStack, MultiBufferSource buffers,
                                          SonarPalette palette, SonarDisplayLayout.Area area,
                                          int displayRange, float partialTick,
                                          boolean forceLabels) {
        boolean sideScan = snapshot.sonarType()
                == org.rassvet.create_echo_radars.content.sonar.SonarType.SIDE_SCAN_D;
        SideScanDataLayout sideScanLayout = sideScan
                ? sideScanDataLayout(area, ClientConfig.sideScanDataPosition()) : null;
        SonarDisplayLayout.Area plotArea = sideScan ? sideScanLayout.plotArea() : area;
        VertexConsumer backgroundQuads = buffers.getBuffer(RenderType.debugQuads());
        colorQuad(backgroundQuads, poseStack.last(), area.left(), area.right(), area.bottom(), area.top(),
                BACKGROUND_DEPTH, sideScan ? 0.016f : 0.002f,
                sideScan ? 0.022f : 0.003f, sideScan ? 0.028f : 0.008f, 0.98f);
        if (sideScan) {
            colorQuad(backgroundQuads, poseStack.last(), plotArea.left(), plotArea.right(),
                    plotArea.bottom(), plotArea.top(), SECTOR_DEPTH,
                    0.002f, 0.003f, 0.008f, 0.98f);
        }
        VertexConsumer echoQuads = backgroundQuads;
        float sweepAngle = mechanicalSweepAngle(monitor, snapshot, partialTick);
        double currentTick = monitor.getLevel().getGameTime() + partialTick;
        ClientFrameHistory clientHistory = clientHistory(monitor, snapshot);
        List<SonarFrame> frames = clientHistory.frames();
        List<VisibleOrdinaryFrame> sideScanVisibleFrames = sideScan
                ? visibleSideScanFrames(frames, SonarDisplayLayout.sideScanRowCapacity(
                plotArea, sideScanHistoryAlongX(sideScanLayout.position()))) : List.of();
        if (sideScan && snapshot.autoHeight()) {
            List<SonarFrame> visibleFramesForRange = new java.util.ArrayList<>(
                    sideScanVisibleFrames.size());
            for (VisibleOrdinaryFrame visibleFrame : sideScanVisibleFrames) {
                visibleFramesForRange.add(visibleFrame.frame());
            }
            displayRange = SonarDisplayLayout.compositeDisplayRangeFromFrames(
                    visibleFramesForRange, snapshot.range(), displayRange);
        }
        if (snapshot.sonarType()
                == org.rassvet.create_echo_radars.content.sonar.SonarType.MECHANICAL_IMAGING_C) {
            clientHistory.advanceMechanicalDisplay(sweepAngle, snapshot.scanAngularSpeed(), currentTick);
            SonarDisplayLayout.CircularGeometry circle = SonarDisplayLayout.circularGeometry(area, displayRange);
            List<MechanicalSweepBuffer.VisibleValue<SonarReturn>> pixels =
                    new java.util.ArrayList<>(clientHistory.mechanicalVisiblePixels());
            SonarDisplayLayout.AngularLayout blockLayout = null;
            if (ClientConfig.blockSizedPixels()) {
                List<SonarDisplayLayout.AngularPoint> points =
                        new java.util.ArrayList<>(pixels.size());
                for (int pixelIndex = 0; pixelIndex < pixels.size(); pixelIndex++) {
                    MechanicalSweepBuffer.VisibleValue<SonarReturn> pixel = pixels.get(pixelIndex);
                    SonarReturn sonarReturn = pixel.value();
                    points.add(new SonarDisplayLayout.AngularPoint(sonarReturn.rangeBin(),
                            sonarReturn.bearingDegrees(), MECHANICAL_SAMPLE_DEGREES,
                            sonarReturn.intensity()));
                }
                blockLayout = SonarDisplayLayout.polarBlockLayout(points, 360);
            }
            for (int pixelIndex = 0; pixelIndex < pixels.size(); pixelIndex++) {
                if (blockLayout != null && !blockLayout.visible()[pixelIndex]) continue;
                MechanicalSweepBuffer.VisibleValue<SonarReturn> pixel = pixels.get(pixelIndex);
                SonarReturn sonarReturn = pixel.value();
                float pixelAlpha = SonarRotation.mechanicalPixelAlpha(
                        ClientConfig.mechanicalPixelLifetimeTicks(), pixel.activatedTick(),
                        currentTick, snapshot.scanAngularSpeed());
                if (pixelAlpha <= 0) continue;
                float strength = (float) Math.pow(Math.max(0, Math.min(1,
                        sonarReturn.intensity() * ClientConfig.gain())), 0.55);
                SonarPalette.Rgb color = palette.color(strength);
                float bearing = blockLayout == null ? sonarReturn.bearingDegrees()
                        : blockLayout.bearings()[pixelIndex];
                float resolution = blockLayout == null ? MECHANICAL_SAMPLE_DEGREES
                        : blockLayout.resolutions()[pixelIndex];
                SonarDisplayProjection.Cell cell = SonarDisplayProjection.circularCell(
                        sonarReturn.rangeBin(), displayRange, bearing, resolution,
                        blockLayout != null && ClientConfig.pointGaps());
                Vec3 innerLeft = circularPoint(cell.innerLeft(), area, circle);
                Vec3 innerRight = circularPoint(cell.innerRight(), area, circle);
                Vec3 outerRight = circularPoint(cell.outerRight(), area, circle);
                Vec3 outerLeft = circularPoint(cell.outerLeft(), area, circle);
                colorQuad(echoQuads, poseStack.last(), outerLeft, outerRight,
                        innerRight, innerLeft, ECHO_DEPTH,
                        color.red(), color.green(), color.blue(), pixelAlpha);
            }
            renderSpecializedGrid(snapshot, poseStack, buffers, plotArea);
            double sweep = Math.toRadians(sweepAngle);
            float radius = circle.radius();
            Vec3 center = new Vec3(area.centerX(), 0, area.centerZ());
            Vec3 edge = new Vec3(area.centerX() + Math.sin(sweep) * radius, 0,
                    area.centerZ() + Math.cos(sweep) * radius);
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            line(lines, poseStack.last().pose(), poseStack.last().normal(), center, edge,
                    SWEEP_DEPTH, 0.25f, 1, 0.7f, 0.9f);
            renderSpecializedTracks(monitor, snapshot, poseStack, buffers, area, displayRange);
            renderCircularLabels(snapshot, poseStack, buffers, area, displayRange, circle);
            return;
        } else {
            List<VisibleOrdinaryFrame> visibleFrames = sideScan
                    ? sideScanVisibleFrames
                    : visibleOrdinaryFrames(frames, currentTick);
            int frameCount = Math.max(1, visibleFrames.size());
            for (int frameIndex = 0; frameIndex < visibleFrames.size(); frameIndex++) {
                VisibleOrdinaryFrame visibleFrame = visibleFrames.get(frameIndex);
                SonarFrame frame = visibleFrame.frame();
                float ageAlpha = visibleFrame.alpha();
                Iterable<SonarReturn> frameReturns = sideScan
                        ? strongestSideScanReturns(frame.returns()) : frame.returns();
                for (SonarReturn sonarReturn : frameReturns) {
                    float strength = (float) Math.pow(Math.max(0, Math.min(1,
                            sonarReturn.intensity() * ClientConfig.gain())), 0.55);
                    SonarPalette.Rgb color = palette.color(strength);
                    float x;
                    float z;
                    switch (snapshot.sonarType()) {
                        case ECHO_SOUNDER_A -> {
                            x = area.left() + (frameIndex + 0.5f) * area.width() / frameCount;
                            z = area.top() - sonarReturn.normalizedDistance() * area.height() * 0.94f;
                        }
                        case SIDE_SCAN_D -> {
                            if (sonarReturn.rangeBin() >= displayRange) continue;
                            int ageRows = visibleFrames.size() - 1 - frameIndex;
                            SonarDisplayLayout.SideScanCell cell = SonarDisplayLayout.sideScanCell(
                                    plotArea, sonarReturn.rangeBin(), displayRange,
                                    sonarReturn.bearingDegrees() >= 0, ageRows,
                                    ClientConfig.pointGaps(),
                                    sideScanHistoryAlongX(sideScanLayout.position()),
                                    sideScanNewestAtMinimum(sideScanLayout.position()));
                            colorQuad(echoQuads, poseStack.last(), cell.left(), cell.right(),
                                    cell.bottom(), cell.top(), ECHO_DEPTH,
                                    color.red(), color.green(), color.blue(), ageAlpha);
                            continue;
                        }
                        default -> {
                            continue;
                        }
                    }
                    float point = Math.max(0.008f, area.minSize() / 96f);
                    colorQuad(echoQuads, poseStack.last(), x - point, x + point, z - point, z + point,
                            ECHO_DEPTH, color.red(), color.green(), color.blue(), ageAlpha);
                }
            }
        }
        renderSpecializedGrid(snapshot, poseStack, buffers, plotArea);
        renderSpecializedTracks(monitor, snapshot, poseStack, buffers, plotArea, displayRange);
        if (area.minSize() >= 2 || forceLabels) {
            if (sideScan) {
                renderSideScanData(poseStack, buffers, sideScanLayout, displayRange,
                        sideScanVisibleFrames, currentTick);
            } else {
                drawLabel(displayRange + "m", area.right() - area.minSize() * 0.06f,
                        area.bottom() + area.minSize() * 0.03f,
                        poseStack, buffers, area.minSize());
            }
        }
    }

    private static void renderSideScanData(PoseStack poseStack, MultiBufferSource buffers,
                                           SideScanDataLayout layout, int displayRange,
                                           List<VisibleOrdinaryFrame> frames,
                                           double currentTick) {
        SonarDisplayLayout.Area plot = layout.plotArea();
        float size = Math.max(1, plot.minSize());
        SideScanDataPosition position = layout.position();

        if (position == SideScanDataPosition.BOTTOM || position == SideScanDataPosition.TOP) {
            float bottomZ = sideScanLabelPosition(layout.outerArea().bottom(), plot.bottom());
            float topZ = sideScanLabelPosition(layout.outerArea().top(), plot.top());
            for (int division = -3; division <= 3; division++) {
                float x = plot.centerX() + plot.width() * 0.47f * division / 3f;
                int distance = Math.round(displayRange * Math.abs(division) / 3f);
                drawSideScanLabel(distance + "m", x, bottomZ, poseStack, buffers, size);
                drawSideScanLabel(distance + "m", x, topZ, poseStack, buffers, size);
            }
        } else {
            float leftX = sideScanLabelPosition(layout.outerArea().left(), plot.left());
            float rightX = sideScanLabelPosition(layout.outerArea().right(), plot.right());
            for (int division = -3; division <= 3; division++) {
                float z = plot.centerZ() + plot.height() * 0.47f * division / 3f;
                int distance = Math.round(displayRange * Math.abs(division) / 3f);
                drawSideScanLabel(distance + "m", leftX, z, poseStack, buffers, size);
                drawSideScanLabel(distance + "m", rightX, z, poseStack, buffers, size);
            }
        }

        for (int division = 0; division < 6; division++) {
            int age = sideScanAgeSeconds(frames, currentTick, division, 5);
            String label = age == 0 ? "0s" : "-" + age + "s";
            if (position == SideScanDataPosition.BOTTOM || position == SideScanDataPosition.TOP) {
                float leftX = sideScanLabelPosition(layout.outerArea().left(), plot.left());
                float rightX = sideScanLabelPosition(layout.outerArea().right(), plot.right());
                float progress = division / 5f;
                float z = position == SideScanDataPosition.BOTTOM
                        ? plot.bottom() + plot.height() * progress
                        : plot.top() - plot.height() * progress;
                drawSideScanLabel(label, leftX, z, poseStack, buffers, size);
                drawSideScanLabel(label, rightX, z, poseStack, buffers, size);
            } else {
                float bottomZ = sideScanLabelPosition(layout.outerArea().bottom(), plot.bottom());
                float topZ = sideScanLabelPosition(layout.outerArea().top(), plot.top());
                float progress = division / 5f;
                float x = position == SideScanDataPosition.RIGHT
                        ? plot.right() - plot.width() * progress
                        : plot.left() + plot.width() * progress;
                drawSideScanLabel(label, x, bottomZ, poseStack, buffers, size);
                drawSideScanLabel(label, x, topZ, poseStack, buffers, size);
            }
        }
    }

    private static float sideScanLabelPosition(float outerEdge, float plotEdge) {
        return outerEdge + (plotEdge - outerEdge) * SIDE_SCAN_LABEL_INSET;
    }

    private static int sideScanAgeSeconds(List<VisibleOrdinaryFrame> frames,
                                          double currentTick, int division, int divisions) {
        if (division <= 0 || frames.isEmpty()) return 0;
        int index = Math.round((frames.size() - 1) * (1 - division / (float) divisions));
        SonarFrame frame = frames.get(Math.max(0, Math.min(frames.size() - 1, index))).frame();
        long referenceTick = frame.completedTick() > 0
                ? frame.completedTick() : frame.startedTick();
        return Math.max(0, (int) Math.ceil((currentTick - referenceTick) / 20.0));
    }

    private static SideScanDataLayout sideScanDataLayout(
            SonarDisplayLayout.Area area, SideScanDataPosition position) {
        float inset = area.minSize() * 0.115f;
        SonarDisplayLayout.Area plot = new SonarDisplayLayout.Area(
                area.left() + inset, area.right() - inset,
                area.bottom() + inset, area.top() - inset);
        return new SideScanDataLayout(area, plot, position);
    }

    private static boolean sideScanHistoryAlongX(SideScanDataPosition position) {
        return position == SideScanDataPosition.RIGHT || position == SideScanDataPosition.LEFT;
    }

    private static boolean sideScanNewestAtMinimum(SideScanDataPosition position) {
        return position == SideScanDataPosition.BOTTOM || position == SideScanDataPosition.LEFT;
    }

    private static List<VisibleOrdinaryFrame> visibleSideScanFrames(List<SonarFrame> frames,
                                                                    int rowCapacity) {
        List<SonarFrame> ready = new java.util.ArrayList<>(frames.size());
        for (SonarFrame frame : frames) {
            if (SonarDisplayLayout.sideScanFrameOccupiesRow(frame)) ready.add(frame);
        }
        int first = Math.max(0, ready.size() - Math.max(1, rowCapacity));
        List<VisibleOrdinaryFrame> visible = new java.util.ArrayList<>(ready.size() - first);
        for (int index = first; index < ready.size(); index++) {
            visible.add(new VisibleOrdinaryFrame(ready.get(index), 1));
        }
        return List.copyOf(visible);
    }

    private static List<SonarReturn> strongestSideScanReturns(List<SonarReturn> returns) {
        Map<Integer, SonarReturn> strongest = new LinkedHashMap<>();
        for (SonarReturn sonarReturn : returns) {
            int side = sonarReturn.bearingDegrees() < 0 ? 0 : 1;
            int key = sonarReturn.rangeBin() * 2 + side;
            SonarReturn previous = strongest.get(key);
            if (previous == null || sonarReturn.intensity() > previous.intensity()) {
                strongest.put(key, sonarReturn);
            }
        }
        return List.copyOf(strongest.values());
    }

    private static List<VisibleOrdinaryFrame> visibleOrdinaryFrames(List<SonarFrame> frames,
                                                                     double currentTick) {
        long latestCompletedEpoch = Long.MIN_VALUE;
        boolean newScanActive = false;
        for (SonarFrame frame : frames) {
            if (frame.completedTick() == 0) newScanActive = true;
            else latestCompletedEpoch = Math.max(latestCompletedEpoch, frame.epoch());
        }

        int lifetimeTicks = ClientConfig.oldPixelLifetimeTicks();
        List<VisibleOrdinaryFrame> visible = new java.util.ArrayList<>(frames.size());
        for (SonarFrame frame : frames) {
            if (frame.completedTick() == 0) {
                visible.add(new VisibleOrdinaryFrame(frame, 1));
                continue;
            }
            long ageTicks = (long) Math.floor(Math.max(0, currentTick - frame.completedTick()));
            boolean latestCompleted = frame.epoch() == latestCompletedEpoch;
            float alpha = SonarDisplayLayout.oldFrameAlpha(lifetimeTicks, ageTicks,
                    newScanActive, newScanActive ? 1 : 0, latestCompleted);
            if (alpha > 0) visible.add(new VisibleOrdinaryFrame(frame, alpha));
        }
        return List.copyOf(visible);
    }

    private record VisibleOrdinaryFrame(SonarFrame frame, float alpha) {}

    private record SideScanDataLayout(SonarDisplayLayout.Area outerArea,
                                      SonarDisplayLayout.Area plotArea,
                                      SideScanDataPosition position) {}

    private static void renderSpecializedTracks(MonitorBlockEntity monitor, SonarMonitorSnapshot snapshot,
                                                PoseStack poseStack, MultiBufferSource buffers,
                                                SonarDisplayLayout.Area area, int displayRange) {
        SonarOrientation orientation = snapshot.displayOrientation();
        float radius = area.minSize() * 0.5f;
        Vec3 trackOrigin = snapshot.displayOrigin();
        for (RadarTrack track : monitor.getTracks()) {
            Vec3 relative = track.position().subtract(trackOrigin);
            if (relative.length() > snapshot.range()) continue;
            SonarMath.Projection projection = SonarMath.project(relative, orientation);
            double normalized = projection.range() / Math.max(1, displayRange);
            if (snapshot.sonarType()
                    != org.rassvet.create_echo_radars.content.sonar.SonarType.MECHANICAL_IMAGING_C
                    && !SonarDisplayLayout.trackInsideDisplayRange(
                    projection.range(), displayRange)) continue;
            float x;
            float z;
            switch (snapshot.sonarType()) {
                case MECHANICAL_IMAGING_C -> {
                    SonarMath.Projection horizontalProjection = SonarMath.projectHorizontal(relative, orientation);
                    if (!SonarDisplayLayout.trackInsideDisplayRange(
                            horizontalProjection.range(), displayRange)) continue;
                    normalized = horizontalProjection.range() / Math.max(1, displayRange);
                    double bearing = Math.toRadians(horizontalProjection.bearingDegrees());
                    SonarDisplayLayout.CircularGeometry circle = SonarDisplayLayout.circularGeometry(area, displayRange);
                    x = area.centerX() + (float) (Math.sin(bearing) * normalized * circle.radius());
                    z = area.centerZ() + (float) (Math.cos(bearing) * normalized * circle.radius());
                }
                case ECHO_SOUNDER_A -> {
                    x = area.right() - area.width() * 0.05f;
                    z = area.top() - (float) normalized * area.height() * 0.94f;
                }
                case SIDE_SCAN_D -> {
                    SideScanDataPosition position = ClientConfig.sideScanDataPosition();
                    if (sideScanHistoryAlongX(position)) {
                        x = position == SideScanDataPosition.RIGHT
                                ? area.right() - area.width() * 0.05f
                                : area.left() + area.width() * 0.05f;
                        z = area.centerZ() + (float) Math.copySign(
                                normalized * area.height() * 0.47f,
                                projection.bearingDegrees());
                    } else {
                        x = area.centerX() + (float) Math.copySign(
                                normalized * area.width() * 0.47f,
                                projection.bearingDegrees());
                        z = position == SideScanDataPosition.BOTTOM
                                ? area.bottom() + area.height() * 0.05f
                                : area.top() - area.height() * 0.05f;
                    }
                }
                default -> {
                    continue;
                }
            }
            Color color = track.getColor();
            renderSprite(track.getSprite(), poseStack, buffers, x, z, radius,
                    color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), 1);
            if (track.id().equals(monitor.getSelectedEntity())) {
                renderSprite(MonitorSprite.TARGET_SELECTED, poseStack, buffers, x, z,
                        radius, 1, 1, 1, 1);
            }
        }
    }

    public static float mechanicalSweepAngle(MonitorBlockEntity monitor,
                                             SonarMonitorSnapshot snapshot, float partialTick) {
        if (snapshot.sonarType()
                != org.rassvet.create_echo_radars.content.sonar.SonarType.MECHANICAL_IMAGING_C
                || snapshot.scanAngleTick() <= 0 || monitor.getLevel() == null) {
            return snapshot.scanAngle();
        }
        double elapsedTicks = monitor.getLevel().getGameTime() + partialTick - snapshot.scanAngleTick();
        return SonarRotation.wrap(snapshot.scanAngle()
                + snapshot.scanAngularSpeed() * (float) Math.max(0, elapsedTicks));
    }

    private static void renderSpecializedGrid(SonarMonitorSnapshot snapshot, PoseStack poseStack,
                                               MultiBufferSource buffers, SonarDisplayLayout.Area area) {
        VertexConsumer pixels = buffers.getBuffer(SonarRenderTypes.gridOverlay());
        PixelGrid pixelGrid = PixelGrid.forArea(area);
        BitSet occupiedPixels = new BitSet(pixelGrid.pixelCount());
        if (snapshot.sonarType()
                == org.rassvet.create_echo_radars.content.sonar.SonarType.MECHANICAL_IMAGING_C) {
            float maximum = SonarDisplayLayout.circularGeometry(area, displayRange(snapshot)).radius();
            for (int ring = 1; ring <= 4; ring++) {
                float radius = maximum * ring / 4f;
                Vec3 previous = null;
                for (int step = 0; step <= ARC_STEPS * 2; step++) {
                    double angle = Math.PI * 2 * step / (ARC_STEPS * 2.0);
                    Vec3 next = new Vec3(area.centerX() + Math.sin(angle) * radius, 0,
                            area.centerZ() + Math.cos(angle) * radius);
                    if (previous != null) pixelLine(pixels, poseStack.last(), pixelGrid,
                            occupiedPixels, previous, next, GRID_DEPTH,
                            0.55f, 0.65f, 0.68f, 0.45f);
                    previous = next;
                }
            }
            pixelLine(pixels, poseStack.last(), pixelGrid, occupiedPixels,
                    new Vec3(area.centerX(), 0, area.centerZ() - maximum),
                    new Vec3(area.centerX(), 0, area.centerZ() + maximum), GRID_DEPTH,
                    0.55f, 0.65f, 0.68f, 0.45f);
            pixelLine(pixels, poseStack.last(), pixelGrid, occupiedPixels,
                    new Vec3(area.centerX() - maximum, 0, area.centerZ()),
                    new Vec3(area.centerX() + maximum, 0, area.centerZ()), GRID_DEPTH,
                    0.55f, 0.65f, 0.68f, 0.45f);
            return;
        }
        if (snapshot.sonarType()
                == org.rassvet.create_echo_radars.content.sonar.SonarType.SIDE_SCAN_D) {
            boolean historyAlongX = sideScanHistoryAlongX(ClientConfig.sideScanDataPosition());
            for (int division = 1; division < 5; division++) {
                if (historyAlongX) {
                    float x = area.left() + area.width() * division / 5f;
                    pixelLine(pixels, poseStack.last(), pixelGrid, occupiedPixels,
                            new Vec3(x, 0, area.bottom()),
                            new Vec3(x, 0, area.top()), GRID_DEPTH,
                            0.55f, 0.65f, 0.68f, 0.32f);
                } else {
                    float z = area.bottom() + area.height() * division / 5f;
                    pixelLine(pixels, poseStack.last(), pixelGrid, occupiedPixels,
                            new Vec3(area.left(), 0, z),
                            new Vec3(area.right(), 0, z), GRID_DEPTH,
                            0.55f, 0.65f, 0.68f, 0.32f);
                }
            }
            if (historyAlongX) {
                pixelLine(pixels, poseStack.last(), pixelGrid, occupiedPixels,
                        new Vec3(area.left(), 0, area.centerZ()),
                        new Vec3(area.right(), 0, area.centerZ()), GRID_DEPTH,
                        0.75f, 0.8f, 0.82f, 0.72f);
            } else {
                pixelLine(pixels, poseStack.last(), pixelGrid, occupiedPixels,
                        new Vec3(area.centerX(), 0, area.bottom()),
                        new Vec3(area.centerX(), 0, area.top()), GRID_DEPTH,
                        0.75f, 0.8f, 0.82f, 0.72f);
            }
            for (int division = 1; division <= 3; division++) {
                float offset = (historyAlongX ? area.height() : area.width())
                        * 0.47f * division / 3f;
                for (int side : new int[]{-1, 1}) {
                    if (historyAlongX) {
                        float z = area.centerZ() + side * offset;
                        pixelLine(pixels, poseStack.last(), pixelGrid, occupiedPixels,
                                new Vec3(area.left(), 0, z),
                                new Vec3(area.right(), 0, z), GRID_DEPTH,
                                0.55f, 0.65f, 0.68f, division == 3 ? 0.5f : 0.3f);
                    } else {
                        float x = area.centerX() + side * offset;
                        pixelLine(pixels, poseStack.last(), pixelGrid, occupiedPixels,
                                new Vec3(x, 0, area.bottom()),
                                new Vec3(x, 0, area.top()), GRID_DEPTH,
                                0.55f, 0.65f, 0.68f, division == 3 ? 0.5f : 0.3f);
                    }
                }
            }
            return;
        }
        for (int division = 1; division < 4; division++) {
            float z = area.bottom() + area.height() * division / 4f;
            pixelLine(pixels, poseStack.last(), pixelGrid, occupiedPixels,
                    new Vec3(area.left(), 0, z), new Vec3(area.right(), 0, z), GRID_DEPTH,
                    0.55f, 0.65f, 0.68f, 0.38f);
        }
    }

    private static void renderCircularLabels(SonarMonitorSnapshot snapshot, PoseStack poseStack,
                                             MultiBufferSource buffers, SonarDisplayLayout.Area area,
                                             int displayRange, SonarDisplayLayout.CircularGeometry circle) {
        float size = area.minSize();
        double distanceLabelAngle = Math.toRadians(42);
        for (int ring = 1; ring <= 4; ring++) {
            float radius = circle.radius() * ring / 4f;
            float x = area.centerX() + (float) Math.sin(distanceLabelAngle) * radius;
            float z = area.centerZ() + (float) Math.cos(distanceLabelAngle) * radius;
            int distance = Math.max(1, Math.round(displayRange * ring / 4f));
            drawLabel(distance + "m", x, z, poseStack, buffers, size);
        }

        float angleRadius = Math.min(size * 0.465f,
                circle.radius() + size * 0.035f);
        for (int angle = 0; angle < 360; angle += 45) {
            double radians = Math.toRadians(angle);
            float x = area.centerX() + (float) Math.sin(radians) * angleRadius;
            float z = area.centerZ() + (float) Math.cos(radians) * angleRadius;
            drawLabel(angle + "\u00b0", x, z, poseStack, buffers, size);
        }
    }

    private static FrameEchoLayout echoLayout(MonitorBlockEntity monitor, SonarMonitorSnapshot snapshot,
                                               SonarFrame frame) {
        FrameLayoutKey key = new FrameLayoutKey(monitor.getLevel(), monitor.getBlockPos().asLong(),
                frame.epoch(), frame.startedTick());
        List<SonarReturn> returns = frame.returns();
        float fallbackResolution = snapshot.horizontalSector()
                / (float) Math.max(1, snapshot.horizontalBeams() - 1);
        boolean pointGaps = ClientConfig.pointGaps();
        boolean blockSizedPixels = ClientConfig.blockSizedPixels();
        CachedFrameEchoLayout cached = FRAME_ECHO_LAYOUTS.get(key);
        if (cached != null && Float.compare(cached.fallbackResolution, fallbackResolution) == 0
                && cached.pointGaps == pointGaps
                && cached.blockSizedPixels == blockSizedPixels
                && (cached.returns == returns || cached.returns.equals(returns))) {
            cached.returns = returns;
            cached.lastAccess = ++frameLayoutAccessSequence;
            return cached.layout;
        }
        if (cached == null && FRAME_ECHO_LAYOUTS.size() >= MAX_FRAME_ECHO_LAYOUTS) {
            evictOldestFrameLayout();
        }
        List<SonarDisplayLayout.AngularPoint> framePoints =
                new java.util.ArrayList<>(returns.size());
        for (SonarReturn sonarReturn : returns) {
            framePoints.add(new SonarDisplayLayout.AngularPoint(
                    sonarReturn.rangeBin(), sonarReturn.bearingDegrees(),
                    sonarReturn.angularResolutionDegrees(), sonarReturn.intensity()));
        }
        SonarDisplayLayout.AngularLayout angularLayout = blockSizedPixels
                ? SonarDisplayLayout.polarBlockLayout(framePoints, snapshot.horizontalSector())
                : SonarDisplayLayout.readableAngularLayout(framePoints, fallbackResolution,
                pointGaps, false);
        FrameEchoLayout layout = new FrameEchoLayout(angularLayout.resolutions(),
                angularLayout.visible(), angularLayout.bearings());
        FRAME_ECHO_LAYOUTS.put(key, new CachedFrameEchoLayout(returns, fallbackResolution,
                pointGaps, blockSizedPixels, layout, ++frameLayoutAccessSequence));
        return layout;
    }

    private static void evictOldestFrameLayout() {
        FrameLayoutKey oldestKey = null;
        long oldestAccess = Long.MAX_VALUE;
        for (Map.Entry<FrameLayoutKey, CachedFrameEchoLayout> entry : FRAME_ECHO_LAYOUTS.entrySet()) {
            if (entry.getValue().lastAccess < oldestAccess) {
                oldestAccess = entry.getValue().lastAccess;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) FRAME_ECHO_LAYOUTS.remove(oldestKey);
    }

    private record FrameEchoLayout(float[] angularResolutions, boolean[] visible, float[] bearings) {}

    private record FrameLayoutKey(Object level, long monitorPos, long epoch, long startedTick) {}

    private static final class CachedFrameEchoLayout {
        private List<SonarReturn> returns;
        private final float fallbackResolution;
        private final boolean pointGaps;
        private final boolean blockSizedPixels;
        private final FrameEchoLayout layout;
        private long lastAccess;

        private CachedFrameEchoLayout(List<SonarReturn> returns, float fallbackResolution,
                                      boolean pointGaps, boolean blockSizedPixels,
                                      FrameEchoLayout layout, long lastAccess) {
            this.returns = returns;
            this.fallbackResolution = fallbackResolution;
            this.pointGaps = pointGaps;
            this.blockSizedPixels = blockSizedPixels;
            this.layout = layout;
            this.lastAccess = lastAccess;
        }
    }

    private static ClientFrameHistory clientHistory(MonitorBlockEntity monitor,
                                                    SonarMonitorSnapshot snapshot) {
        ClientHistoryKey key = new ClientHistoryKey(monitor.getLevel(), monitor.getBlockPos().asLong());
        ClientFrameHistory history = CLIENT_HISTORIES.get(key);
        if (history == null) {
            if (CLIENT_HISTORIES.size() >= MAX_CLIENT_HISTORIES) evictOldestClientHistory();
            history = new ClientFrameHistory();
            CLIENT_HISTORIES.put(key, history);
        }
        history.lastAccess = ++clientHistoryAccessSequence;
        history.update(snapshot);
        return history;
    }

    private static void evictOldestClientHistory() {
        ClientHistoryKey oldestKey = null;
        long oldestAccess = Long.MAX_VALUE;
        for (Map.Entry<ClientHistoryKey, ClientFrameHistory> entry : CLIENT_HISTORIES.entrySet()) {
            if (entry.getValue().lastAccess < oldestAccess) {
                oldestAccess = entry.getValue().lastAccess;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) CLIENT_HISTORIES.remove(oldestKey);
    }

    private record ClientHistoryKey(Object level, long monitorPos) {}

    private static final class ClientFrameHistory {
        private final LinkedHashMap<Long, SonarFrame> completed = new LinkedHashMap<>();
        private final MechanicalSweepBuffer<MechanicalPixelKey, SonarReturn> mechanicalDisplay =
                new MechanicalSweepBuffer<>(MECHANICAL_SAMPLE_DEGREES,
                        MECHANICAL_PREFETCH_ARC_DEGREES);
        private final Map<Long, MechanicalFrameRevision> mechanicalFrameRevisions = new HashMap<>();
        private SonarFrame current;
        private List<SonarFrame> cachedFrames = List.of();
        private ClientSnapshotSignature signature;
        private long fullyRefreshedEpoch = Long.MIN_VALUE;
        private long lastAccess;

        private void update(SonarMonitorSnapshot snapshot) {
            boolean changed = false;
            ClientSnapshotSignature nextSignature = ClientSnapshotSignature.from(snapshot);
            if (!nextSignature.equals(signature)) {
                completed.clear();
                clearMechanicalDisplay();
                current = null;
                signature = nextSignature;
                fullyRefreshedEpoch = Long.MIN_VALUE;
                changed = true;
            }
            List<SonarFrame> serverFrames = snapshot.frames();
            if (serverSessionRestarted(serverFrames)) {
                completed.clear();
                clearMechanicalDisplay();
                current = null;
                fullyRefreshedEpoch = Long.MIN_VALUE;
                changed = true;
            }
            SonarFrame nextCurrent = null;
            for (SonarFrame frame : serverFrames) {
                if (snapshot.sonarType()
                        == org.rassvet.create_echo_radars.content.sonar.SonarType.MECHANICAL_IMAGING_C) {
                    queueMechanicalFrameRevision(frame);
                }
                if (frame.completedTick() == 0) {
                    nextCurrent = frame;
                } else if (!completed.containsKey(frame.epoch())) {
                    completed.put(frame.epoch(), frame);
                    changed = true;
                }
            }
            if (current != nextCurrent) {
                current = nextCurrent;
                changed = true;
            }
            while (completed.size() > MAX_LOCAL_FRAMES) {
                Iterator<Long> iterator = completed.keySet().iterator();
                long removedEpoch = iterator.next();
                iterator.remove();
                mechanicalFrameRevisions.remove(removedEpoch);
                changed = true;
            }
            if (changed) {
                List<SonarFrame> frames = new java.util.ArrayList<>(completed.size() + (current == null ? 0 : 1));
                frames.addAll(completed.values());
                if (current != null) frames.add(current);
                cachedFrames = List.copyOf(frames);
            }
        }

        private List<SonarFrame> frames() {
            return cachedFrames;
        }

        private void markFullyRefreshed(long epoch) {
            fullyRefreshedEpoch = Math.max(fullyRefreshedEpoch, epoch);
        }

        private long fullyRefreshedEpoch() {
            return fullyRefreshedEpoch;
        }

        private void queueMechanicalFrameRevision(SonarFrame frame) {
            boolean completedFrame = frame.completedTick() != 0;
            MechanicalFrameRevision revision = MechanicalFrameRevision.from(frame, completedFrame);
            if (revision.equals(mechanicalFrameRevisions.get(frame.epoch()))) return;
            mechanicalFrameRevisions.put(frame.epoch(), revision);
            queueMechanicalFrame(frame, completedFrame);
        }

        private void queueMechanicalFrame(SonarFrame frame, boolean includeEmptySectors) {
            List<Float> angles = frame.scanAngles();
            Map<Integer, List<SonarReturn>> returnsByAngle = new HashMap<>();
            if (includeEmptySectors) for (float angle : angles) {
                returnsByAngle.computeIfAbsent(sectorKey(mechanicalDisplaySector(angle)),
                        ignored -> new java.util.ArrayList<>());
            }
            for (SonarReturn sonarReturn : frame.returns()) {
                int key = sectorKey(mechanicalDisplaySector(sonarReturn.bearingDegrees()));
                returnsByAngle.computeIfAbsent(key, ignored -> new java.util.ArrayList<>())
                        .add(sonarReturn);
            }
            for (Map.Entry<Integer, List<SonarReturn>> entry : returnsByAngle.entrySet()) {
                queueMechanicalSector(frame.epoch(), entry.getKey() / 10f, entry.getValue());
            }
        }

        private static float mechanicalDisplaySector(float angle) {
            return SonarRotation.nearestScanStep(angle, MECHANICAL_SAMPLE_DEGREES);
        }

        private void queueMechanicalSector(long epoch, float angle, List<SonarReturn> returns) {
            Map<MechanicalPixelKey, SonarReturn> sectorPixels = new HashMap<>();
            for (SonarReturn sonarReturn : returns) {
                MechanicalPixelKey key = MechanicalPixelKey.from(sonarReturn);
                SonarReturn existing = sectorPixels.get(key);
                if (existing == null || sonarReturn.intensity() >= existing.intensity()) {
                    sectorPixels.put(key, sonarReturn);
                }
            }
            mechanicalDisplay.queue(epoch, angle, sectorPixels);
        }

        private void advanceMechanicalDisplay(float sweepAngle, float angularSpeed,
                                               double currentTick) {
            mechanicalDisplay.advanceSweep(sweepAngle, angularSpeed, currentTick);
        }

        private static int sectorKey(float angle) {
            return Math.floorMod(Math.round(SonarRotation.wrap(angle) * 10), 3600);
        }

        private java.util.Collection<MechanicalSweepBuffer.VisibleValue<SonarReturn>>
        mechanicalVisiblePixels() {
            return mechanicalDisplay.visibleValues();
        }

        private void clearMechanicalDisplay() {
            mechanicalDisplay.reset();
            mechanicalFrameRevisions.clear();
        }

        private boolean serverSessionRestarted(List<SonarFrame> serverFrames) {
            long localMaxEpoch = current == null ? Long.MIN_VALUE : current.epoch();
            for (long epoch : completed.keySet()) localMaxEpoch = Math.max(localMaxEpoch, epoch);
            long incomingMaxEpoch = Long.MIN_VALUE;
            for (SonarFrame frame : serverFrames) {
                incomingMaxEpoch = Math.max(incomingMaxEpoch, frame.epoch());
                SonarFrame existing = completed.get(frame.epoch());
                if (existing != null && existing.startedTick() != frame.startedTick()) return true;
                if (current != null && current.epoch() == frame.epoch()
                        && current.startedTick() != frame.startedTick()) return true;
            }
            return localMaxEpoch != Long.MIN_VALUE && incomingMaxEpoch < localMaxEpoch;
        }
    }

    private record MechanicalPixelKey(int rangeBin, int bearingDeciDegrees) {
        private static MechanicalPixelKey from(SonarReturn sonarReturn) {
            return new MechanicalPixelKey(sonarReturn.rangeBin(),
                    Math.floorMod(Math.round(SonarRotation.wrap(
                            sonarReturn.bearingDegrees()) * 10), 3600));
        }
    }

    private record MechanicalFrameRevision(List<SonarReturn> returns, List<Float> angles,
                                           boolean completed) {
        private static MechanicalFrameRevision from(SonarFrame frame, boolean completed) {
            return new MechanicalFrameRevision(frame.returns(), frame.scanAngles(), completed);
        }
    }

    private record ClientSnapshotSignature(int range, int sector, int verticalSector,
                                           org.rassvet.create_echo_radars.content.sonar.SonarType sonarType,
                                           int horizontalBeams,
                                           int rotationDirection,
                                           boolean autoHeight, Vec3 origin, Vec3 forward,
                                           Vec3 right, Vec3 up) {
        private static ClientSnapshotSignature from(SonarMonitorSnapshot snapshot) {
            boolean rotating = snapshot.sonarType()
                    == org.rassvet.create_echo_radars.content.sonar.SonarType.MECHANICAL_IMAGING_C;
            boolean waterfall = snapshot.sonarType()
                    == org.rassvet.create_echo_radars.content.sonar.SonarType.SIDE_SCAN_D;
            return new ClientSnapshotSignature(snapshot.range(), snapshot.horizontalSector(),
                    snapshot.verticalSector(), snapshot.sonarType(), snapshot.horizontalBeams(),
                    rotating ? Float.compare(snapshot.scanAngularSpeed(), 0) : 0,
                    snapshot.autoHeight(), waterfall || rotating ? Vec3.ZERO : snapshot.origin(),
                    waterfall || rotating ? Vec3.ZERO : snapshot.forward(),
                    waterfall || rotating ? Vec3.ZERO : snapshot.right(),
                    waterfall || rotating ? Vec3.ZERO : snapshot.up());
        }
    }

    private static void renderTracks(MonitorBlockEntity monitor, SonarMonitorSnapshot snapshot,
                                     PoseStack poseStack, MultiBufferSource buffers,
                                     SonarDisplayLayout.Area area, int displayRange,
                                     String hoveredTrackId, boolean overrideHover,
                                     float screenTrackRadius) {
        SonarOrientation orientation = new SonarOrientation(snapshot.forward(),
                snapshot.right(), snapshot.up());
        // Create: Radars renders each 256x256 track texture over a quad as wide as
        // the monitor itself. Most of that texture is transparent, so using only
        // the visible-symbol size here makes the actual marker collapse to a pixel.
        float trackRadius = Float.isFinite(screenTrackRadius)
                ? screenTrackRadius : area.minSize() * 0.5f;
        for (RadarTrack track : monitor.getTracks()) {
            Vec3 relative = track.position().subtract(snapshot.origin());
            if (!SonarMath.insideCone(relative, orientation,
                    snapshot.horizontalSector(), snapshot.verticalSector(), snapshot.range())) continue;
            SonarMath.Projection projection = SonarMath.project(relative, orientation);
            if (!SonarDisplayLayout.trackInsideDisplayRange(
                    projection.range(), displayRange)) continue;
            double bearing = projection.bearingDegrees();
            Vec3 point = displayPoint(SonarDisplayProjection.project(
                            projection.range() / Math.max(1, displayRange), Math.toRadians(bearing),
                            snapshot.horizontalSector()),
                    area);
            float x = (float) point.x;
            float z = (float) point.z;
            Color color = track.getColor();
            renderSprite(track.getSprite(), poseStack, buffers, x, z, trackRadius,
                    color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), 1);
            String hovered = overrideHover ? hoveredTrackId : monitor.getHoveredEntity();
            if (track.id().equals(hovered)) {
                renderSprite(MonitorSprite.TARGET_HOVERED, poseStack, buffers, x, z,
                        trackRadius, 1, 1, 1, 1);
            }
            if (track.id().equals(monitor.getSelectedEntity())) {
                renderSprite(MonitorSprite.TARGET_SELECTED, poseStack, buffers, x, z,
                        trackRadius, 1, 1, 1, 1);
            }
        }
    }

    private static void renderLabels(SonarMonitorSnapshot snapshot, PoseStack poseStack,
                                     MultiBufferSource buffers, SonarDisplayLayout.Area area, int displayRange) {
        float size = area.minSize();
        for (int ring = 1; ring <= 4; ring++) {
            Vec3 point = displayPoint(SonarDisplayProjection.project(ring / 4f,
                    Math.toRadians(snapshot.horizontalSector() / 2f), snapshot.horizontalSector()),
                    area);
            drawLabel(Integer.toString(Math.max(1, displayRange * ring / 4)),
                    (float) point.x - size * 0.035f,
                    (float) point.z, poseStack, buffers, size);
        }
        Vec3 apex = displayPoint(SonarDisplayProjection.project(0, 0,
                snapshot.horizontalSector()), area);
        for (int ray = 0; ray <= 4; ray++) {
            int angle = Math.round(-snapshot.horizontalSector() / 2f
                    + snapshot.horizontalSector() * ray / 4f);
            Vec3 point = displayPoint(SonarDisplayProjection.project(0.965f,
                    Math.toRadians(angle), snapshot.horizontalSector()), area);
            String text = (angle > 0 ? "+" : "") + angle + "°";
            Vec3 labelPoint = offsetLabelFromLine(text, point, apex, area, size);
            drawLabel(text, (float) labelPoint.x, (float) labelPoint.z,
                    poseStack, buffers, size);
        }
    }

    private static Vec3 offsetLabelFromLine(String text, Vec3 point, Vec3 lineOrigin,
                                            SonarDisplayLayout.Area area, float size) {
        double dx = point.x - lineOrigin.x;
        double dz = point.z - lineOrigin.z;
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-5) return point;

        Font font = Minecraft.getInstance().font;
        float scale = labelScale(size, LABEL_SCALE);
        float clearance = scale * (Math.max(font.width(text) * 0.5f,
                font.lineHeight * 0.5f) + 3);
        double normalX = -dz / length;
        double normalZ = dx / length;
        Vec3 first = point.add(normalX * clearance, 0, normalZ * clearance);
        Vec3 second = point.add(-normalX * clearance, 0, -normalZ * clearance);

        // Prefer the side nearer the display centre. This pushes the two outside labels
        // inward and keeps their enlarged glyphs clear of the sector boundary.
        Vec3 chosen = distanceToDisplayCenter(first, area) <= distanceToDisplayCenter(second, area)
                ? first : second;
        float halfWidth = font.width(text) * scale * 0.5f;
        float halfHeight = font.lineHeight * scale * 0.5f;
        return new Vec3(
                Math.max(area.left() + halfWidth,
                        Math.min(area.right() - halfWidth, chosen.x)),
                0,
                Math.max(area.bottom() + halfHeight,
                        Math.min(area.top() - halfHeight, chosen.z)));
    }

    private static double distanceToDisplayCenter(Vec3 point, SonarDisplayLayout.Area area) {
        double dx = point.x - area.centerX();
        double dz = point.z - area.centerZ();
        return dx * dx + dz * dz;
    }

    private static void drawLabel(String text, float x, float z, PoseStack poseStack,
                                  MultiBufferSource buffers, float size) {
        drawLabel(text, x, z, poseStack, buffers, size, LABEL_SCALE);
    }

    private static void drawSideScanLabel(String text, float x, float z, PoseStack poseStack,
                                          MultiBufferSource buffers, float size) {
        drawLabel(text, x, z, poseStack, buffers, size, SIDE_SCAN_LABEL_SCALE);
    }

    private static void drawLabel(String text, float x, float z, PoseStack poseStack,
                                  MultiBufferSource buffers, float size, float scaleMultiplier) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(x, LABEL_DEPTH, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        float scale = labelScale(size, scaleMultiplier);
        poseStack.scale(scale, scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, -font.lineHeight / 2f,
                0xdde8e8e8, false,
                poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        poseStack.popPose();
    }

    private static float labelScale(float size, float scaleMultiplier) {
        return 0.0022f * Math.min(1.35f, size / 2f) * scaleMultiplier;
    }

    private static Vec3 displayPoint(SonarDisplayProjection.Point point, SonarDisplayLayout.Area area) {
        return new Vec3(area.centerX() + point.x() * area.width() * 0.5, 0,
                area.centerZ() + point.z() * area.height() * 0.5);
    }

    private static Vec3 circularPoint(SonarDisplayProjection.Point point,
                                      SonarDisplayLayout.Area area,
                                      SonarDisplayLayout.CircularGeometry circle) {
        return new Vec3(area.centerX() + point.x() * circle.radius(), 0,
                area.centerZ() + point.z() * circle.radius());
    }

    private static SonarDisplayLayout.Area displayArea(MonitorBlockEntity monitor) {
        SonarMonitorDimensions dimensions = ((SonarMonitorExtension) monitor)
                .createEchoRadars$getMonitorDimensions();
        return SonarDisplayLayout.area(dimensions);
    }

    private static int displayRange(SonarMonitorSnapshot snapshot) {
        return snapshot.effectiveDisplayRange();
    }

    private static void renderPixel(VertexConsumer buffer, PoseStack.Pose pose,
                                    SonarDisplayLayout.Area area, SonarReturn sonarReturn,
                                    float bearingDegrees, float angularResolution,
                                    SonarMonitorSnapshot snapshot, int displayRange,
                                    float depth, SonarPalette.Rgb color, float alpha) {
        SonarDisplayProjection.Cell cell = SonarDisplayProjection.echoCell(
                sonarReturn.rangeBin(), displayRange, bearingDegrees, angularResolution,
                snapshot.horizontalSector(), ClientConfig.pointGaps());
        Vec3 innerLeft = displayPoint(cell.innerLeft(), area);
        Vec3 innerRight = displayPoint(cell.innerRight(), area);
        Vec3 outerRight = displayPoint(cell.outerRight(), area);
        Vec3 outerLeft = displayPoint(cell.outerLeft(), area);
        colorQuad(buffer, pose, outerLeft, outerRight, innerRight, innerLeft, depth,
                color.red(), color.green(), color.blue(), alpha);
    }

    private static RevealState smoothRevealState(MonitorBlockEntity monitor, SonarFrame frame,
                                                 long gameTime, float partialTick, float speed,
                                                 boolean advance) {
        float cap = frame.completedTick() == 0 ? frame.revealProgress() : 1;
        RevealKey key = new RevealKey(monitor.getBlockPos().asLong(), frame.epoch());
        double now = gameTime + partialTick;
        pruneRevealStates(key, now);
        RevealState state = REVEAL_STATES.computeIfAbsent(key, ignored -> new RevealState(now));
        double elapsed = Math.max(0, now - state.lastTick);
        state.lastTick = now;
        if (!advance) return state;
        state.progress = SonarDisplayLayout.advanceRevealProgress(
                state.progress, cap, elapsed, speed);
        if (frame.completedTick() != 0 && state.progress >= 1 - REVEAL_COMPLETE_EPSILON
                && Double.isNaN(state.fullyRevealedAt)) {
            state.progress = 1;
            state.fullyRevealedAt = now;
        }
        return state;
    }

    private static void pruneRevealStates(RevealKey current, double now) {
        REVEAL_STATES.entrySet().removeIf(entry -> !entry.getKey().equals(current)
                && now - entry.getValue().lastTick > REVEAL_STATE_TTL_TICKS);
        if (REVEAL_STATES.size() < MAX_REVEAL_STATES || REVEAL_STATES.containsKey(current)) return;
        RevealKey oldestKey = null;
        double oldestTick = Double.POSITIVE_INFINITY;
        for (Map.Entry<RevealKey, RevealState> entry : REVEAL_STATES.entrySet()) {
            if (entry.getValue().lastTick < oldestTick) {
                oldestTick = entry.getValue().lastTick;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) REVEAL_STATES.remove(oldestKey);
    }

    private static float revealAlpha(float distance, float revealProgress, float revealBand) {
        if (revealProgress >= 1) return 1;
        if (distance >= revealProgress) return 0;
        float fadeStart = Math.max(0, revealProgress - revealBand);
        if (distance <= fadeStart) return 1;
        float t = (revealProgress - distance) / Math.max(1.0e-4f, revealBand);
        return t * t * (3 - 2 * t);
    }

    private record RevealKey(long monitorPos, long epoch) {}

    private static final class RevealState {
        private float progress;
        private double lastTick;
        private double fullyRevealedAt = Double.NaN;

        private RevealState(double now) {
            this.lastTick = now;
        }

        private boolean fullyRevealed() {
            return !Double.isNaN(fullyRevealedAt);
        }
    }

    private static float noise(int seed) {
        int value = seed;
        value ^= value << 13;
        value ^= value >>> 17;
        value ^= value << 5;
        return (value & 0x7fffffff) / (float) Integer.MAX_VALUE;
    }

    private static void renderSprite(MonitorSprite sprite, PoseStack poseStack, MultiBufferSource buffers,
                                     float x, float z, float radius, float red, float green, float blue, float alpha) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(sprite.getTexture()));
        quad(buffer, poseStack.last(), x - radius, x + radius, z - radius, z + radius,
                TRACK_DEPTH, red, green, blue, alpha);
    }

    private static void wedge(VertexConsumer buffer, PoseStack.Pose pose, float apexX, float apexZ,
                              Vec3 first, Vec3 second, float depth,
                              float red, float green, float blue, float alpha) {
        addVertex(buffer, pose, apexX, depth, apexZ, red, green, blue, alpha, 0.5f, 1);
        addVertex(buffer, pose, (float) second.x, depth, (float) second.z, red, green, blue, alpha, 1, 0);
        addVertex(buffer, pose, (float) first.x, depth, (float) first.z, red, green, blue, alpha, 0, 0);
        addVertex(buffer, pose, apexX, depth, apexZ, red, green, blue, alpha, 0.5f, 1);
    }

    private static void colorWedge(VertexConsumer buffer, PoseStack.Pose pose, float apexX, float apexZ,
                                   Vec3 first, Vec3 second, float depth,
                                   float red, float green, float blue, float alpha) {
        addColorVertex(buffer, pose, apexX, depth, apexZ, red, green, blue, alpha);
        addColorVertex(buffer, pose, (float) second.x, depth, (float) second.z, red, green, blue, alpha);
        addColorVertex(buffer, pose, (float) first.x, depth, (float) first.z, red, green, blue, alpha);
        addColorVertex(buffer, pose, apexX, depth, apexZ, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float left, float right,
                             float bottom, float top, float depth,
                             float red, float green, float blue, float alpha) {
        addVertex(buffer, pose, left, depth, bottom, red, green, blue, alpha, 0, 1);
        addVertex(buffer, pose, right, depth, bottom, red, green, blue, alpha, 1, 1);
        addVertex(buffer, pose, right, depth, top, red, green, blue, alpha, 1, 0);
        addVertex(buffer, pose, left, depth, top, red, green, blue, alpha, 0, 0);
    }

    private static void colorQuad(VertexConsumer buffer, PoseStack.Pose pose, float left, float right,
                                  float bottom, float top, float depth,
                                  float red, float green, float blue, float alpha) {
        addColorVertex(buffer, pose, left, depth, bottom, red, green, blue, alpha);
        addColorVertex(buffer, pose, right, depth, bottom, red, green, blue, alpha);
        addColorVertex(buffer, pose, right, depth, top, red, green, blue, alpha);
        addColorVertex(buffer, pose, left, depth, top, red, green, blue, alpha);
    }

    private static void colorQuad(VertexConsumer buffer, PoseStack.Pose pose,
                                  Vec3 first, Vec3 second, Vec3 third, Vec3 fourth, float depth,
                                  float red, float green, float blue, float alpha) {
        addColorVertex(buffer, pose, (float) first.x, depth, (float) first.z, red, green, blue, alpha);
        addColorVertex(buffer, pose, (float) second.x, depth, (float) second.z, red, green, blue, alpha);
        addColorVertex(buffer, pose, (float) third.x, depth, (float) third.z, red, green, blue, alpha);
        addColorVertex(buffer, pose, (float) fourth.x, depth, (float) fourth.z, red, green, blue, alpha);
    }

    private static void addVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                                  float red, float green, float blue, float alpha, float u, float v) {
        buffer.addVertex(pose.pose(), x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, 0, 1, 0);
    }

    private static void addColorVertex(VertexConsumer buffer, PoseStack.Pose pose,
                                       float x, float y, float z,
                                       float red, float green, float blue, float alpha) {
        buffer.addVertex(pose.pose(), x, y, z).setColor(red, green, blue, alpha);
    }

    /**
     * Draws the decorative sonar grid through a fixed-resolution raster. A 256 px logical
     * surface keeps the pixel-art character of Create: Radars while giving large sonar displays
     * smoother arcs and diagonals than its original 128 px sprites. Echo pixels are rendered
     * elsewhere and never pass here.
     */
    private static void pixelLine(VertexConsumer buffer, PoseStack.Pose pose, PixelGrid grid,
                                  BitSet occupiedPixels, Vec3 first, Vec3 second, float depth,
                                  float red, float green, float blue, float alpha) {
        int x0 = grid.column((float) first.x);
        int z0 = grid.row((float) first.z);
        int x1 = grid.column((float) second.x);
        int z1 = grid.row((float) second.z);
        int deltaX = Math.abs(x1 - x0);
        int stepX = x0 < x1 ? 1 : -1;
        int deltaZ = -Math.abs(z1 - z0);
        int stepZ = z0 < z1 ? 1 : -1;
        int error = deltaX + deltaZ;

        while (true) {
            int pixelIndex = z0 * grid.columns() + x0;
            if (!occupiedPixels.get(pixelIndex)) {
                occupiedPixels.set(pixelIndex);
                colorQuad(buffer, pose, grid.pixelLeft(x0), grid.pixelRight(x0),
                        grid.pixelBottom(z0), grid.pixelTop(z0), depth,
                        red, green, blue, alpha);
            }
            if (x0 == x1 && z0 == z1) break;
            int doubledError = error * 2;
            if (doubledError >= deltaZ) {
                error += deltaZ;
                x0 += stepX;
            }
            if (doubledError <= deltaX) {
                error += deltaX;
                z0 += stepZ;
            }
        }
    }

    private record PixelGrid(SonarDisplayLayout.Area area, int columns, int rows,
                             float pixelWidth, float pixelHeight) {
        private static PixelGrid forArea(SonarDisplayLayout.Area area) {
            float basePixelSize = area.minSize() / BACKGROUND_RASTER_RESOLUTION;
            int columns = Math.max(1, Math.round(area.width() / basePixelSize));
            int rows = Math.max(1, Math.round(area.height() / basePixelSize));
            return new PixelGrid(area, columns, rows,
                    area.width() / columns, area.height() / rows);
        }

        private int column(float x) {
            return Math.max(0, Math.min(columns - 1,
                    (int) Math.floor((x - area.left()) / pixelWidth)));
        }

        private int row(float z) {
            return Math.max(0, Math.min(rows - 1,
                    (int) Math.floor((z - area.bottom()) / pixelHeight)));
        }

        private float pixelLeft(int column) {
            return area.left() + column * pixelWidth;
        }

        private float pixelRight(int column) {
            return pixelLeft(column) + pixelWidth;
        }

        private float pixelBottom(int row) {
            return area.bottom() + row * pixelHeight;
        }

        private float pixelTop(int row) {
            return pixelBottom(row) + pixelHeight;
        }

        private int pixelCount() {
            return columns * rows;
        }
    }

    private static void line(VertexConsumer buffer, Matrix4f matrix, Matrix3f normal,
                             Vec3 first, Vec3 second, float depth,
                             float red, float green, float blue, float alpha) {
        buffer.addVertex(matrix, (float) first.x, depth, (float) first.z)
                .setColor(red, green, blue, alpha).setNormal(0, 1, 0);
        buffer.addVertex(matrix, (float) second.x, depth, (float) second.z)
                .setColor(red, green, blue, alpha).setNormal(0, 1, 0);
    }
}
