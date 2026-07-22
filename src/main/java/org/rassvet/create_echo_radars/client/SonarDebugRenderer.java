package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.joml.Matrix4f;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.config.SyncedServerConfig;
import org.rassvet.create_echo_radars.content.sonar.SableSonarCompat;
import org.rassvet.create_echo_radars.content.sonar.SideScanGeometry;
import org.rassvet.create_echo_radars.content.sonar.SideScanHitFilter;
import org.rassvet.create_echo_radars.content.sonar.NearbyBlockTracker;
import org.rassvet.create_echo_radars.content.sonar.SonarAdaptiveTracePlan;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarOrientation;
import org.rassvet.create_echo_radars.content.sonar.SonarType;
import org.rassvet.create_echo_radars.content.sonar.SonarVoxelDda;

import java.util.ArrayList;
import java.util.List;

public final class SonarDebugRenderer {
    private static final int SHELL_SEGMENTS = 32;
    private static final double RAY_WIDTH = 0.035;
    private static final double EDGE_WIDTH = 0.075;
    private static final long ANGLE_PREVIEW_DURATION_MS = 5L * 60L * 1000L;

    private static BlockPos selectedPos;
    private static net.minecraft.resources.ResourceKey<Level> selectedDimension;
    private static List<Ray> tracedRays = List.of();
    private static long lastTraceTick = Long.MIN_VALUE;
    private static SonarDebugRayMode lastRayMode;
    private static AnglePreview anglePreview;

    private SonarDebugRenderer() {}

    public static boolean toggleAnglePreview(SonarBlockEntity sonar, int range,
                                             int horizontalSector, int verticalSector, int tiltAngle) {
        Level level = sonar.getLevel();
        if (level == null) return false;
        if (anglePreview != null && anglePreview.pos.equals(sonar.getBlockPos())
                && anglePreview.dimension.equals(level.dimension())) {
            anglePreview = null;
            return false;
        }
        selectedPos = null;
        selectedDimension = null;
        tracedRays = List.of();
        anglePreview = new AnglePreview(sonar.getBlockPos().immutable(), level.dimension(),
                Math.max(SonarBlockEntity.MIN_RANGE, Math.min(SonarBlockEntity.MAX_RANGE, range)),
                Math.max(SonarBlockEntity.MIN_ANGLE, Math.min(SonarBlockEntity.MAX_ANGLE, horizontalSector)),
                Math.max(SonarBlockEntity.MIN_ANGLE, Math.min(SonarBlockEntity.MAX_ANGLE, verticalSector)),
                Math.max(SonarBlockEntity.MIN_TILT, Math.min(SonarBlockEntity.MAX_TILT, tiltAngle)),
                Util.getMillis() + ANGLE_PREVIEW_DURATION_MS);
        return true;
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()
                || !event.getItemStack().is(CreateEchoRadars.SONAR_DEBUG_TOOL.get())
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof SonarBlockEntity)) {
            return;
        }

        boolean disabling = event.getPos().equals(selectedPos)
                && event.getLevel().dimension().equals(selectedDimension);
        if (!disabling) anglePreview = null;
        selectedPos = disabling ? null : event.getPos().immutable();
        selectedDimension = disabling ? null : event.getLevel().dimension();
        tracedRays = List.of();
        lastTraceTick = Long.MIN_VALUE;
        lastRayMode = null;

        if (event.getEntity() == Minecraft.getInstance().player) {
            event.getEntity().displayClientMessage(Component.translatable(disabling
                    ? "message.create_echo_radars.debug_disabled"
                    : "message.create_echo_radars.debug_enabled"), true);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) return;
        renderDebugSelection(event, level);
        renderAnglePreview(event, minecraft, level);
    }

    private static void renderDebugSelection(RenderLevelStageEvent event, Level level) {
        if (selectedPos == null || !level.dimension().equals(selectedDimension)) return;
        if (!(level.getBlockEntity(selectedPos) instanceof SonarBlockEntity sonar)) {
            selectedPos = null;
            selectedDimension = null;
            tracedRays = List.of();
            return;
        }

        Vec3 origin = sonar.emitterPosition();
        SonarOrientation orientation = sonar.orientation();
        int range = sonar.getSonarRange();
        int sector = sonar.getHorizontalSector();
        int verticalSector = sonar.getVerticalSector();
        long gameTime = level.getGameTime();
        SonarDebugRayMode rayMode = ClientConfig.debugRayMode();
        if (rayMode != lastRayMode || gameTime - lastTraceTick >= 5 || lastTraceTick == Long.MIN_VALUE) {
            List<Ray> nextRays = new ArrayList<>();
            if (rayMode.tracesAnything()) {
                nextRays.addAll(traceRays(level, origin, orientation, range, sector, verticalSector,
                        sonar.getSonarType(), rayMode));
            }
            if (rayMode == SonarDebugRayMode.ALL && SyncedServerConfig.entityOcclusionCheck()) {
                nextRays.addAll(traceEntityVisibilityRays(level, sonar, origin, orientation,
                        range, sector, verticalSector));
            }
            tracedRays = List.copyOf(nextRays);
            lastTraceTick = gameTime;
            lastRayMode = rayMode;
        }
        renderGeometry(event.getPoseStack(), event.getCamera().getPosition(),
                origin, orientation, range, sector, verticalSector,
                sonar.getSonarType(), tracedRays, true);
    }

    private static void renderAnglePreview(RenderLevelStageEvent event, Minecraft minecraft, Level level) {
        if (anglePreview == null) return;
        if (Util.getMillis() >= anglePreview.expiresAtMillis) {
            anglePreview = null;
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "message.create_echo_radars.angle_preview_expired"), true);
            }
            return;
        }
        if (!level.dimension().equals(anglePreview.dimension)) return;
        if (!(level.getBlockEntity(anglePreview.pos) instanceof SonarBlockEntity sonar)) {
            anglePreview = null;
            return;
        }
        SonarOrientation orientation = sonar.orientation(anglePreview.tiltAngle);
        Vec3 origin = sonar.emitterPosition();
        renderGeometry(event.getPoseStack(), event.getCamera().getPosition(),
                origin, orientation, anglePreview.range, anglePreview.horizontalSector,
                anglePreview.verticalSector, sonar.getSonarType(), List.of(), false);
    }

    private static List<Ray> traceRays(Level level, Vec3 origin, SonarOrientation orientation,
                                       int range, int sector, int verticalSector,
                                       SonarType sonarType, SonarDebugRayMode rayMode) {
        int horizontalBeams = SyncedServerConfig.horizontalBeams(sonarType);
        int verticalBeams = SyncedServerConfig.verticalBeams(sonarType);
        SonarAdaptiveTracePlan.Settings settings = SonarAdaptiveTracePlan.settings(
                range, sector, verticalSector,
                horizontalBeams, verticalBeams, SyncedServerConfig.additionalRays(),
                SyncedServerConfig.hitRefinementBacktrackBlocks());

        List<RayPlan> pending = new ArrayList<>(horizontalBeams * verticalBeams);
        if (sonarType == SonarType.SIDE_SCAN_D) {
            for (SonarAdaptiveTracePlan.Leaf leaf :
                    SideScanGeometry.createLeaves(horizontalBeams, verticalBeams)) {
                pending.add(new RayPlan(leaf, 0, range));
            }
        } else {
            for (int beam = 0; beam < horizontalBeams; beam++) {
                for (int vertical = 0; vertical < verticalBeams; vertical++) {
                    pending.add(new RayPlan(new SonarAdaptiveTracePlan.Leaf(beam, vertical, 0, 0),
                            0, range));
                }
            }
        }

        int primaryPlanCount = pending.size();
        List<Ray> rays = new ArrayList<>(pending.size());
        NearbyBlockTracker detectedHitBlocks = new NearbyBlockTracker();
        List<SideScanRefinementCandidate> sideScanCandidates = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            RayPlan plan = pending.get(i);
            double endDistance = plan.endDistance;
            Vec3 direction = direction(orientation, settings, plan.leaf);
            Vec3 rayOrigin = sonarType == SonarType.SIDE_SCAN_D
                    ? origin.add(orientation.right().scale(
                    SideScanGeometry.emitterSideOffset(plan.leaf)))
                    : origin;
            SableSonarCompat.Snapshot sableSnapshot = SableSonarCompat.capture(level, rayOrigin,
                    orientation, range,
                    sonarType == SonarType.SIDE_SCAN_D ? 180 : sector,
                    sonarType == SonarType.SIDE_SCAN_D ? 180 : verticalSector,
                    List.of(new SableSonarCompat.RaySegment(
                            rayOrigin, direction, plan.startDistance, endDistance)));
            TraceEnd end = findTraceEnd(level, rayOrigin, direction,
                    plan.startDistance, endDistance, sableSnapshot);
            Vec3 start = rayOrigin.add(direction.scale(plan.startDistance));
            if (rayMode.shows(plan.leaf.refinement())) {
                rays.add(new Ray(start, end.position, end.hit, plan.leaf.beam(),
                        plan.leaf.vertical(), horizontalBeams,
                        plan.leaf.refinement() ? RayKind.REFINEMENT : RayKind.MAIN));
            }
            boolean nearbyDetected = false;
            boolean delaySideScanRefinement = false;
            if (end.hit && !plan.leaf.refinement()
                    && SyncedServerConfig.refineOnlyUndetectedNeighbors()) {
                SideScanHitFilter.Cell hit = hitCell(end.position, direction);
                if (sonarType == SonarType.SIDE_SCAN_D) {
                    delaySideScanRefinement = true;
                    sideScanCandidates.add(new SideScanRefinementCandidate(plan, end, hit));
                } else {
                    nearbyDetected = detectedHitBlocks.hasNearbyAndRecord(
                            hit.x(), hit.y(), hit.z());
                }
            }
            if (end.hit && !delaySideScanRefinement && !nearbyDetected
                    && rayMode.tracesRefinements()) {
                List<SonarAdaptiveTracePlan.Leaf> refinements = sonarType == SonarType.SIDE_SCAN_D
                        ? SonarAdaptiveTracePlan.sideScanRefinementsForHit(
                        plan.leaf, end.distance, settings)
                        : SonarAdaptiveTracePlan.refinementsForHit(
                        plan.leaf, end.distance, settings);
                for (SonarAdaptiveTracePlan.Leaf leaf : refinements) {
                    pending.add(new RayPlan(leaf, leaf.refinementStartDistance(),
                            leaf.refinementEndDistance()));
                }
            }
            if (i == primaryPlanCount - 1 && rayMode.tracesRefinements()
                    && !sideScanCandidates.isEmpty()) {
                appendAllowedSideScanRefinements(pending, sideScanCandidates, settings);
            }
        }
        return List.copyOf(rays);
    }

    private static void appendAllowedSideScanRefinements(
            List<RayPlan> pending, List<SideScanRefinementCandidate> candidates,
            SonarAdaptiveTracePlan.Settings settings) {
        List<SideScanHitFilter.Cell> hits = new ArrayList<>(candidates.size());
        for (SideScanRefinementCandidate candidate : candidates) hits.add(candidate.cell());
        boolean[] refinementAllowed = SideScanHitFilter.refinementsAllowed(hits);
        for (int i = 0; i < candidates.size(); i++) {
            if (!refinementAllowed[i]) continue;
            SideScanRefinementCandidate candidate = candidates.get(i);
            for (SonarAdaptiveTracePlan.Leaf leaf :
                    SonarAdaptiveTracePlan.sideScanRefinementsForHit(
                            candidate.plan().leaf, candidate.end().distance, settings)) {
                pending.add(new RayPlan(leaf, leaf.refinementStartDistance(),
                        leaf.refinementEndDistance()));
            }
        }
    }

    private static SideScanHitFilter.Cell hitCell(Vec3 hitPosition, Vec3 direction) {
        Vec3 hitBlock = hitPosition.add(direction.scale(1.0e-4));
        return new SideScanHitFilter.Cell(
                (int) Math.floor(hitBlock.x),
                (int) Math.floor(hitBlock.y),
                (int) Math.floor(hitBlock.z));
    }

    private static List<Ray> traceEntityVisibilityRays(Level level, SonarBlockEntity sonar,
                                                        Vec3 origin, SonarOrientation orientation,
                                                        int range, int sector, int verticalSector) {
        List<Ray> rays = new ArrayList<>();
        var bounds = new net.minecraft.world.phys.AABB(origin, origin).inflate(range);
        for (Entity entity : level.getEntities((Entity) null, bounds, Entity::isAlive)) {
            Vec3 target = entity.getBoundingBox().getCenter();
            Vec3 relative = target.subtract(origin);
            if (!org.rassvet.create_echo_radars.content.sonar.SonarMath.insideCone(
                    relative, orientation, sector, verticalSector, range)) continue;
            if (sonar.isAutoHeight()
                    && Math.abs(org.rassvet.create_echo_radars.content.sonar.SonarMath
                    .project(relative, orientation).up()) > sonar.displayYRange()) continue;
            BlockHitResult hit = level.clip(new ClipContext(origin, target,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            boolean blocked = hit.getType() != HitResult.Type.MISS;
            rays.add(new Ray(origin, blocked ? hit.getLocation() : target, blocked,
                    0, 0, 1, RayKind.ENTITY));
        }
        return rays;
    }

    private static Vec3 direction(SonarOrientation orientation, SonarAdaptiveTracePlan.Settings settings,
                                  SonarAdaptiveTracePlan.Leaf leaf) {
        double yaw = SonarAdaptiveTracePlan.bearing(leaf, settings);
        double pitch = SonarAdaptiveTracePlan.pitch(leaf, settings);
        return orientation.direction(yaw, pitch);
    }

    private static TraceEnd findTraceEnd(Level level, Vec3 origin, Vec3 direction,
                                         double startDistance, double endDistance,
                                         SableSonarCompat.Snapshot sableSnapshot) {
        TraceState trace = new TraceState();
        trace.lastDistance = startDistance;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        SonarVoxelDda.traceCells(origin.x, origin.y, origin.z,
                direction.x, direction.y, direction.z, startDistance, endDistance,
                (x, y, z, distance, incidence) -> {
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (trace.chunk == null || trace.chunkX != chunkX || trace.chunkZ != chunkZ) {
                trace.chunkX = chunkX;
                trace.chunkZ = chunkZ;
                trace.chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            }
            if (trace.chunk == null) {
                trace.end = new TraceEnd(origin.add(direction.scale(trace.lastDistance)),
                        false, trace.lastDistance);
                return false;
            }
            pos.set(x, y, z);
            if (!trace.chunk.getFluidState(pos).is(FluidTags.WATER)) {
                double hitDistance = Math.min(endDistance, distance);
                trace.end = new TraceEnd(origin.add(direction.scale(hitDistance)), true, hitDistance);
                return false;
            }
            trace.lastDistance = Math.min(endDistance, distance);
            return true;
        });
        double sableTraceEnd = trace.end != null && trace.end.hit
                ? trace.end.position.subtract(origin).dot(direction)
                : endDistance;
        java.util.Optional<SableSonarCompat.Hit> sableHit =
                sableSnapshot.trace(origin, direction, startDistance, sableTraceEnd);
        if (sableHit.isPresent()) {
            return new TraceEnd(origin.add(direction.scale(sableHit.get().distance())),
                    true, sableHit.get().distance());
        }
        if (trace.end != null) return trace.end;
        return new TraceEnd(origin.add(direction.scale(endDistance)), false, endDistance);
    }

    private static void renderGeometry(PoseStack poseStack, Vec3 camera,
                                       Vec3 origin, SonarOrientation orientation, int range,
                                       int sector, int verticalSector, SonarType sonarType,
                                       List<Ray> rays, boolean renderTraceRays) {
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        drawSonarShell(matrix, origin, orientation, range, sector, verticalSector,
                sonarType, camera, renderTraceRays);
        if (renderTraceRays) drawRays(matrix, camera, rays);

        RenderSystem.lineWidth(1);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void drawSonarShell(Matrix4f matrix, Vec3 origin, SonarOrientation orientation,
                                       int range, int sector, int verticalSector, SonarType sonarType,
                                       Vec3 camera, boolean drawEdges) {
        if (sonarType == SonarType.SIDE_SCAN_D) {
            drawConeShell(matrix, origin.add(orientation.right().scale(-0.501)),
                    orientation, range, sector, verticalSector,
                    -90, -45, camera, drawEdges);
            drawConeShell(matrix, origin.add(orientation.right().scale(0.501)),
                    orientation, range, sector, verticalSector,
                    90, -45, camera, drawEdges);
            return;
        }
        drawConeShell(matrix, origin, orientation, range, sector, verticalSector,
                0, 0, camera, drawEdges);
    }

    private static void drawConeShell(Matrix4f matrix, Vec3 origin, SonarOrientation orientation,
                                      int range, int sector, int verticalSector,
                                      double centerYaw, double centerPitch,
                                      Vec3 camera, boolean drawEdges) {
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        double halfYaw = sector / 2.0;
        double halfPitch = Math.min(89.5, verticalSector / 2.0);
        Vec3 previousTop = orientation.direction(centerYaw - halfYaw,
                centerPitch + halfPitch).scale(range).add(origin);
        Vec3 previousBottom = orientation.direction(centerYaw - halfYaw,
                centerPitch - halfPitch).scale(range).add(origin);
        int shellTriangles = 0;
        for (int i = 1; i <= SHELL_SEGMENTS; i++) {
            double yaw = centerYaw - halfYaw + sector * i / (double) SHELL_SEGMENTS;
            Vec3 top = orientation.direction(yaw, centerPitch + halfPitch).scale(range).add(origin);
            Vec3 bottom = orientation.direction(yaw, centerPitch - halfPitch).scale(range).add(origin);

            shellTriangles += triangle(buffer, matrix, origin, previousTop, top, 0.08f);
            shellTriangles += triangle(buffer, matrix, origin, bottom, previousBottom, 0.08f);
            shellTriangles += quad(buffer, matrix, previousBottom, bottom, top, previousTop, 0.035f);
            previousTop = top;
            previousBottom = bottom;
        }

        Vec3 leftBottom = orientation.direction(centerYaw - halfYaw,
                centerPitch - halfPitch).scale(range).add(origin);
        Vec3 leftTop = orientation.direction(centerYaw - halfYaw,
                centerPitch + halfPitch).scale(range).add(origin);
        Vec3 rightBottom = orientation.direction(centerYaw + halfYaw,
                centerPitch - halfPitch).scale(range).add(origin);
        Vec3 rightTop = orientation.direction(centerYaw + halfYaw,
                centerPitch + halfPitch).scale(range).add(origin);
        shellTriangles += triangle(buffer, matrix, origin, leftBottom, leftTop, 0.1f, 0.3f, 1f, 0.18f);
        shellTriangles += triangle(buffer, matrix, origin, rightTop, rightBottom, 1f, 0.12f, 0.08f, 0.18f);
        if (shellTriangles > 0) BufferUploader.drawWithShader(buffer.buildOrThrow());

        if (!drawEdges) return;
        BufferBuilder edgeBuffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int edgeTriangles = 0;
        edgeTriangles += rayStrip(edgeBuffer, matrix, camera, origin,
                orientation.direction(centerYaw - halfYaw, centerPitch).scale(range).add(origin),
                EDGE_WIDTH, 0.1f, 0.35f, 1f, 0.95f);
        edgeTriangles += rayStrip(edgeBuffer, matrix, camera, origin,
                orientation.direction(centerYaw + halfYaw, centerPitch).scale(range).add(origin),
                EDGE_WIDTH, 1f, 0.08f, 0.05f, 0.95f);
        if (edgeTriangles > 0) BufferUploader.drawWithShader(edgeBuffer.buildOrThrow());
    }

    private static void drawRays(Matrix4f matrix, Vec3 camera, List<Ray> rays) {
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int triangles = 0;
        for (Ray ray : rays) {
            float red;
            float green;
            float blue;
            float alpha;
            double width = RAY_WIDTH;
            if (ray.kind == RayKind.ENTITY) {
                red = ray.hit ? 1f : 0.9f;
                green = ray.hit ? 0.05f : 0.2f;
                blue = ray.hit ? 0.1f : 1f;
                alpha = 0.9f;
                width = RAY_WIDTH * 1.35;
            } else if (ray.beam == 0) {
                red = 0.1f;
                green = 0.35f;
                blue = 1f;
                alpha = 0.78f;
                width = EDGE_WIDTH * 0.7;
            } else if (ray.beam == ray.beamCount - 1) {
                red = 1f;
                green = 0.08f;
                blue = 0.05f;
                alpha = 0.78f;
                width = EDGE_WIDTH * 0.7;
            } else if (ray.kind == RayKind.REFINEMENT) {
                red = 0.2f;
                green = 0.9f;
                blue = 1f;
                alpha = 0.72f;
                width = RAY_WIDTH * 0.8;
            } else if (ray.hit) {
                red = 1f;
                green = 0.48f;
                blue = 0.05f;
                alpha = 0.68f;
            } else {
                red = 0.15f;
                green = 1f;
                blue = 0.32f;
                alpha = 0.45f;
            }
            triangles += rayStrip(buffer, matrix, camera, ray.start, ray.end, width, red, green, blue, alpha);
        }
        if (triangles > 0) BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static int triangle(BufferBuilder buffer, Matrix4f matrix,
                                 Vec3 a, Vec3 b, Vec3 c, float alpha) {
        return triangle(buffer, matrix, a, b, c, 0.08f, 1, 0.18f, alpha);
    }

    private static int triangle(BufferBuilder buffer, Matrix4f matrix,
                                 Vec3 a, Vec3 b, Vec3 c,
                                 float red, float green, float blue, float alpha) {
        if (!validTriangle(a, b, c)) return 0;
        vertex(buffer, matrix, a, red, green, blue, alpha);
        vertex(buffer, matrix, b, red, green, blue, alpha);
        vertex(buffer, matrix, c, red, green, blue, alpha);
        return 1;
    }

    private static int quad(BufferBuilder buffer, Matrix4f matrix,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, float alpha) {
        return triangle(buffer, matrix, a, b, c, alpha)
                + triangle(buffer, matrix, a, c, d, alpha);
    }

    private static int rayStrip(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
                                 Vec3 start, Vec3 end, double width,
                                 float red, float green, float blue, float alpha) {
        Vec3 axis = end.subtract(start);
        if (!finite(start) || !finite(end) || axis.lengthSqr() < 1.0e-8) return 0;
        axis = axis.normalize();
        Vec3 midpoint = start.add(end).scale(0.5);
        Vec3 toCamera = camera.subtract(midpoint);
        if (toCamera.lengthSqr() < 1.0e-8) toCamera = new Vec3(0, 1, 0);
        Vec3 side = axis.cross(toCamera).normalize();
        if (side.lengthSqr() < 1.0e-8) side = axis.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < 1.0e-8) side = axis.cross(new Vec3(1, 0, 0)).normalize();
        if (!finite(side) || side.lengthSqr() < 1.0e-8) return 0;
        side = side.scale(width);

        Vec3 a = start.add(side);
        Vec3 b = end.add(side);
        Vec3 c = end.subtract(side);
        Vec3 d = start.subtract(side);
        return triangle(buffer, matrix, a, b, c, red, green, blue, alpha)
                + triangle(buffer, matrix, a, c, d, red, green, blue, alpha);
    }

    private static boolean validTriangle(Vec3 a, Vec3 b, Vec3 c) {
        return finite(a) && finite(b) && finite(c)
                && b.subtract(a).cross(c.subtract(a)).lengthSqr() > 1.0e-10;
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 point,
                               float red, float green, float blue, float alpha) {
        buffer.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, alpha);
    }

    private static final class TraceState {
        private int chunkX = Integer.MIN_VALUE;
        private int chunkZ = Integer.MIN_VALUE;
        private LevelChunk chunk;
        private double lastDistance;
        private TraceEnd end;
    }

    private record Ray(Vec3 start, Vec3 end, boolean hit,
                       int beam, int vertical, int beamCount, RayKind kind) {}

    private enum RayKind {
        MAIN,
        REFINEMENT,
        ENTITY
    }

    private record RayPlan(SonarAdaptiveTracePlan.Leaf leaf, double startDistance, double endDistance) {}

    private record TraceEnd(Vec3 position, boolean hit, double distance) {}

    private record SideScanRefinementCandidate(
            RayPlan plan, TraceEnd end, SideScanHitFilter.Cell cell) {}

    private record AnglePreview(BlockPos pos, net.minecraft.resources.ResourceKey<Level> dimension,
                                int range, int horizontalSector, int verticalSector, int tiltAngle,
                                long expiresAtMillis) {}
}
