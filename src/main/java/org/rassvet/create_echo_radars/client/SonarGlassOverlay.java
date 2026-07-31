package org.rassvet.create_echo_radars.client;

import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.content.glass.SonarGlass;
import org.rassvet.create_echo_radars.content.glass.SonarGlassAnimation;
import org.rassvet.create_echo_radars.content.glass.SonarGlassBlockEntity;
import org.rassvet.create_echo_radars.content.glass.SonarGlassNetwork;
import org.rassvet.create_echo_radars.content.glass.SonarGlassPaneBlock;
import org.rassvet.create_echo_radars.content.glass.SonarGlassState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Depth-based sonar-glass rendering.
 *
 * <p>The terrain grid is generated entirely by a fragment shader from a copy
 * of Minecraft's world depth. Java only keeps a small, static aperture VBO for
 * each connected window and updates its world/Sable transform.</p>
 */
public final class SonarGlassOverlay {
    private static final boolean SODIUM_LOADED =
            ModList.get().isLoaded("sodium");
    private static final Map<Long, SonarGlassBlockEntity> ENDPOINTS = new HashMap<>();
    private static final Map<Long, Long> PULSES = new HashMap<>();
    private static final Map<Long, MaskLayout> MASK_LAYOUTS = new HashMap<>();
    private static final Map<Long, DepthAperture> DEPTH_APERTURES = new HashMap<>();

    private static Matrix4f viewProjection;
    private static Matrix4f projectionMatrix;
    private static Matrix4f modelViewMatrix;
    private static Vec3 cameraPosition = Vec3.ZERO;
    private static float framePartialTick;
    private static long geometryGeneration;
    private static long lastGeometryCheckTick = Long.MIN_VALUE;
    private static long lastGeometrySignature = Long.MIN_VALUE;
    private static boolean sodiumOpaqueDepthCaptured;
    private static boolean sodiumWorldCutoutDepthCaptured;
    private static boolean sodiumOpaqueCaptureLogged;
    private static boolean sodiumWorldCutoutCaptureLogged;
    private static net.minecraft.world.level.Level fallbackNotifiedLevel;
    private static String fallbackNotifiedReason;

    private SonarGlassOverlay() {
    }

    public static void track(SonarGlassBlockEntity blockEntity) {
        ENDPOINTS.put(blockEntity.getBlockPos().asLong(), blockEntity);
        invalidate();
    }

    public static void untrack(SonarGlassBlockEntity blockEntity) {
        long key = blockEntity.getBlockPos().asLong();
        ENDPOINTS.remove(key);
        MASK_LAYOUTS.remove(key);
        closeDepthAperture(DEPTH_APERTURES.remove(key));
        invalidate();
    }

    public static void invalidate() {
        geometryGeneration++;
    }

    public static void pulse(BlockPos anchor, long serverTick) {
        PULSES.put(anchor.asLong(), serverTick);
    }

    public static boolean hasActiveDisplay() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.player != null
                && !renderableEndpoints(minecraft).isEmpty();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            if ((!SODIUM_LOADED || !sodiumOpaqueDepthCaptured)
                    && minecraft.level != null && minecraft.player != null
                    && hasActiveDisplay()) {
                // This is the actual surface on which the grid is projected.
                // The fallback also keeps the renderer alive if an unknown
                // Sodium version moves the internal injection point.
                SonarGlassDepthCapture.captureOpaqueDepth();
            }
            sodiumOpaqueDepthCaptured = false;
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            if (minecraft.level != null && minecraft.player != null
                    && hasActiveDisplay()) {
                if (!SODIUM_LOADED || !sodiumWorldCutoutDepthCaptured) {
                    SonarGlassDepthCapture.captureWorldCutoutDepth();
                }
                // With Sodium+Sable this contains sublevel construction
                // geometry drawn after the main world's cutout pass.
                SonarGlassDepthCapture.captureSceneDepth();
            }
            sodiumWorldCutoutDepthCaptured = false;
            return;
        }
        if (event.getStage()
                == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            projectionMatrix = new Matrix4f(event.getProjectionMatrix());
            modelViewMatrix = new Matrix4f(event.getModelViewMatrix());
            viewProjection = new Matrix4f(projectionMatrix).mul(modelViewMatrix);
            Camera camera = event.getCamera();
            cameraPosition = camera.getPosition();
            framePartialTick =
                    event.getPartialTick().getGameTimeDeltaPartialTick(true);
            renderPulses(event);
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            minecraft.getMainRenderTarget().bindWrite(true);
            refreshCopycatGeometry(minecraft);
            renderDepthWorld(framePartialTick);
        }
    }

    private static void refreshCopycatGeometry(Minecraft minecraft) {
        if (minecraft.level == null) return;
        long now = minecraft.level.getGameTime();
        if (!ModList.get().isLoaded("copycats")
                || (lastGeometryCheckTick != Long.MIN_VALUE
                && now - lastGeometryCheckTick < 20)) return;
        lastGeometryCheckTick = now;

        long signature = 1;
        List<SonarGlassBlockEntity> endpoints = new ArrayList<>(
                activeEndpoints(minecraft));
        endpoints.sort(java.util.Comparator.comparingLong(
                endpoint -> endpoint.getBlockPos().asLong()));
        for (SonarGlassBlockEntity endpoint : endpoints) {
            List<BlockPos> blocks = new ArrayList<>(SonarGlassNetwork.find(
                    minecraft.level, endpoint.getBlockPos()).blocks());
            blocks.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
            for (BlockPos pos : blocks) {
                signature = signature * 31 + pos.asLong();
                signature = signature * 31
                        + minecraft.level.getBlockState(pos).hashCode();
            }
        }
        if (signature != lastGeometrySignature) {
            lastGeometrySignature = signature;
            invalidate();
        }
    }

    /**
     * Called by the optional Sodium mixin after Sodium's solid terrain pass
     * and before its combined cutout pass.
     */
    public static void captureSodiumOpaqueDepth() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!SODIUM_LOADED || minecraft.level == null
                || minecraft.player == null || !hasActiveDisplay()) return;
        SonarGlassDepthCapture.captureOpaqueDepth();
        sodiumOpaqueDepthCaptured = true;
        if (!sodiumOpaqueCaptureLogged) {
            sodiumOpaqueCaptureLogged = true;
            CreateEchoRadars.LOGGER.info(
                    "Sonar glass captured Sodium solid-only terrain depth "
                            + "before the cutout pass");
        }
    }

    /**
     * Called after Sodium's main-world cutout pass but before Sable appends
     * transformed sublevel construction geometry.
     */
    public static void captureSodiumWorldCutoutDepth() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!SODIUM_LOADED || minecraft.level == null
                || minecraft.player == null || !hasActiveDisplay()) return;
        SonarGlassDepthCapture.captureWorldCutoutDepth();
        sodiumWorldCutoutDepthCaptured = true;
        if (!sodiumWorldCutoutCaptureLogged) {
            sodiumWorldCutoutCaptureLogged = true;
            CreateEchoRadars.LOGGER.info(
                    "Sonar glass captured Sodium main-world cutout depth "
                            + "before Sable sublevel rendering");
        }
    }

    private static List<SonarGlassBlockEntity> activeEndpoints(Minecraft minecraft) {
        return List.copyOf(ENDPOINTS.values()).stream()
                .filter(endpoint -> !endpoint.isRemoved()
                        && endpoint.getLevel() == minecraft.level)
                .toList();
    }

    private static List<SonarGlassBlockEntity> renderableEndpoints(
            Minecraft minecraft) {
        if (minecraft.player == null) return List.of();
        Vec3 viewer = minecraft.gameRenderer.getMainCamera().getPosition();
        double maximumDistance = ClientConfig.sonarGlassActivationDistance();
        return activeEndpoints(minecraft).stream()
                .filter(endpoint -> endpoint.currentState() != null)
                .filter(endpoint -> isWithinActivationDistance(
                        endpoint, viewer, maximumDistance))
                .toList();
    }

    /**
     * Measures against every block in the connected aperture rather than the
     * endpoint block. Large windows therefore remain active when the endpoint
     * itself is farther away, and the local-space calculation also follows
     * moving Sable structures.
     */
    private static boolean isWithinActivationDistance(
            SonarGlassBlockEntity endpoint, Vec3 viewer,
            double maximumDistance) {
        MaskLayout layout = maskLayout(endpoint);
        WorldTransform transform = worldTransform(endpoint);
        Vec3 localViewer = transform.toLocal(viewer);
        double maximumDistanceSquared = maximumDistance * maximumDistance;
        if (layout.masks.isEmpty()) {
            return distanceToBlockSquared(localViewer,
                    endpoint.getBlockPos()) <= maximumDistanceSquared;
        }
        for (LocalMask mask : layout.masks) {
            if (distanceToBlockSquared(localViewer, mask.pos)
                    <= maximumDistanceSquared) return true;
        }
        return false;
    }

    private static double distanceToBlockSquared(Vec3 point, BlockPos block) {
        double dx = axisDistance(point.x, block.getX(), block.getX() + 1);
        double dy = axisDistance(point.y, block.getY(), block.getY() + 1);
        double dz = axisDistance(point.z, block.getZ(), block.getZ() + 1);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double value, double minimum,
                                       double maximum) {
        if (value < minimum) return minimum - value;
        if (value > maximum) return value - maximum;
        return 0;
    }

    private static void renderDepthWorld(float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || viewProjection == null || projectionMatrix == null
                || modelViewMatrix == null) return;

        List<SonarGlassBlockEntity> endpoints = renderableEndpoints(minecraft);
        notifyFallbackIfNeeded(minecraft, !endpoints.isEmpty());
        if (endpoints.isEmpty()) return;

        Matrix4f inverseViewProjection = new Matrix4f(viewProjection).invert();
        long now = minecraft.level.getGameTime();
        List<SonarGlassDepthDraw> draws = new ArrayList<>();
        for (SonarGlassBlockEntity endpoint : endpoints) {
            SonarGlassState current = endpoint.currentState();
            if (current == null) continue;
            DepthAperture aperture = depthAperture(endpoint);
            if (aperture == null || aperture.buffer.isInvalid()) continue;

            float disconnect = endpoint.disconnectStartTick() < 0 ? 1
                    : SonarGlassAnimation.disconnectAlpha(
                            (long) (now + partial - endpoint.disconnectStartTick()));
            if (disconnect <= 0) continue;
            float age = Mth.clamp(now + partial - endpoint.cycleStartTick(), 0,
                    SonarGlassAnimation.CYCLE_TICKS);
            Matrix4f modelView = depthModelView(worldTransform(endpoint));
            draws.add(new SonarGlassDepthDraw(aperture.buffer, modelView,
                    projectionMatrix, inverseViewProjection, cameraPosition, current,
                    endpoint.previousState(), age, disconnect));
        }
        SonarGlassRenderBackend.renderDepth(draws);
    }

    private static void notifyFallbackIfNeeded(Minecraft minecraft, boolean glassActive) {
        if (!glassActive || minecraft.player == null
                || SonarGlassRenderBackend.isVeilEnabled()) return;
        String reason = SonarGlassRenderBackend.fallbackReason();
        if (fallbackNotifiedLevel == minecraft.level
                && reason.equals(fallbackNotifiedReason)) return;

        Component reasonText = "missing".equals(reason)
                ? Component.translatable(
                        "message.create_echo_radars.sonar_glass_fallback_reason_missing")
                : Component.translatable(
                        "message.create_echo_radars.sonar_glass_fallback_reason_error",
                        reason);
        minecraft.player.displayClientMessage(Component.translatable(
                "message.create_echo_radars.sonar_glass_fallback", reasonText), false);
        fallbackNotifiedLevel = minecraft.level;
        fallbackNotifiedReason = reason;
    }

    private static MaskLayout maskLayout(SonarGlassBlockEntity endpoint) {
        long key = endpoint.getBlockPos().asLong();
        MaskLayout layout = MASK_LAYOUTS.get(key);
        if (layout == null || layout.generation != geometryGeneration) {
            layout = buildMaskLayout(endpoint);
            MASK_LAYOUTS.put(key, layout);
        }
        return layout;
    }

    private static MaskLayout buildMaskLayout(SonarGlassBlockEntity endpoint) {
        if (endpoint.getLevel() == null) {
            return new MaskLayout(geometryGeneration, List.of());
        }
        List<LocalMask> result = new ArrayList<>();
        SonarGlassNetwork.Component component = SonarGlassNetwork.find(
                endpoint.getLevel(), endpoint.getBlockPos());
        for (BlockPos pos : component.blocks()) {
            BlockState state = endpoint.getLevel().getBlockState(pos);
            if (state.getBlock() instanceof SonarGlassPaneBlock) {
                boolean northSouth = state.getValue(CrossCollisionBlock.NORTH)
                        || state.getValue(CrossCollisionBlock.SOUTH);
                boolean eastWest = state.getValue(CrossCollisionBlock.EAST)
                        || state.getValue(CrossCollisionBlock.WEST);
                double minY = SonarGlass.isGlass(endpoint.getLevel(),
                        pos.below()) ? 0 : .05;
                double maxY = SonarGlass.isGlass(endpoint.getLevel(),
                        pos.above()) ? 1 : .95;
                if (!northSouth && !eastWest) {
                    northSouth = true;
                    eastWest = true;
                }
                if (northSouth) {
                    result.add(new LocalMask(pos, new Vec3[]{
                            new Vec3(.5, minY, 0), new Vec3(.5, maxY, 0),
                            new Vec3(.5, maxY, 1), new Vec3(.5, minY, 1)
                    }));
                }
                if (eastWest) {
                    result.add(new LocalMask(pos, new Vec3[]{
                            new Vec3(0, minY, .5), new Vec3(0, maxY, .5),
                            new Vec3(1, maxY, .5), new Vec3(1, minY, .5)
                    }));
                }
            } else if (!addCopycatSlopeMasks(result, pos, state)) {
                result.add(new LocalMask(pos, null));
            }
        }
        return new MaskLayout(geometryGeneration, List.copyOf(result));
    }

    /**
     * Copycats slopes are wedges, so a cube face would project the sonar image
     * outside the visible glass. Match the same sloped plane and rotations used
     * by Copycats' slope model cores.
     */
    private static boolean addCopycatSlopeMasks(
            List<LocalMask> output, BlockPos pos, BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("copycats")) return false;
        String path = id.getPath();
        if (!path.equals("copycat_slope")
                && !path.equals("copycat_vertical_slope")) return false;

        Direction facing = state.getOptionalValue(
                BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
        int rotation = Math.round(facing.toYRot());
        Vec3[] corners = {
                new Vec3(0, 0, 0),
                new Vec3(1, 0, 0),
                new Vec3(1, 1, 1),
                new Vec3(0, 1, 1)
        };
        boolean vertical = path.equals("copycat_vertical_slope");
        boolean flipY = !vertical && state.getOptionalValue(
                BlockStateProperties.HALF).orElse(Half.BOTTOM) == Half.TOP;
        for (int i = 0; i < corners.length; i++) {
            Vec3 point = corners[i];
            if (flipY) point = new Vec3(point.x, 1 - point.y, point.z);
            if (vertical) point = new Vec3(1 - point.y, point.x, point.z);
            corners[i] = rotateCopycatY(point, rotation);
        }
        output.add(new LocalMask(pos, corners));
        return true;
    }

    private static Vec3 rotateCopycatY(Vec3 point, int rotation) {
        int normalized = Math.floorMod(rotation, 360);
        return switch (normalized) {
            case 90 -> new Vec3(1 - point.z, point.y, point.x);
            case 180 -> new Vec3(1 - point.x, point.y, 1 - point.z);
            case 270 -> new Vec3(point.z, point.y, 1 - point.x);
            default -> point;
        };
    }

    private static DepthAperture depthAperture(SonarGlassBlockEntity endpoint) {
        long key = endpoint.getBlockPos().asLong();
        DepthAperture cached = DEPTH_APERTURES.get(key);
        if (cached != null && cached.generation == geometryGeneration
                && !cached.buffer.isInvalid()) return cached;

        closeDepthAperture(cached);
        if (endpoint.getLevel() == null) return null;
        MaskLayout layout = maskLayout(endpoint);
        Vec3 localBase = Vec3.atLowerCornerOf(endpoint.getBlockPos());
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (LocalMask mask : layout.masks) {
            if (mask.corners != null) {
                addLocalApertureQuad(buffer, localBase, mask.pos, mask.corners, true);
                continue;
            }
            for (Direction direction : Direction.values()) {
                if (SonarGlass.isGlass(endpoint.getLevel(),
                        mask.pos.relative(direction))) continue;
                addLocalApertureQuad(buffer, localBase, mask.pos,
                        connectedFaceQuad(endpoint.getLevel(), mask.pos,
                                direction, .07), false);
            }
        }

        MeshData mesh = buffer.build();
        if (mesh == null) return null;
        VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vertexBuffer.bind();
        vertexBuffer.upload(mesh);
        VertexBuffer.unbind();
        DepthAperture replacement = new DepthAperture(geometryGeneration, vertexBuffer);
        DEPTH_APERTURES.put(key, replacement);
        return replacement;
    }

    private static void addLocalApertureQuad(BufferBuilder buffer, Vec3 localBase,
                                             BlockPos pos, Vec3[] corners,
                                             boolean doubleSided) {
        for (Vec3 corner : corners) {
            Vec3 point = corner.add(pos.getX(), pos.getY(), pos.getZ())
                    .subtract(localBase);
            buffer.addVertex((float) point.x, (float) point.y, (float) point.z);
        }
        if (!doubleSided) return;
        for (int i = corners.length - 1; i >= 0; i--) {
            Vec3 point = corners[i].add(pos.getX(), pos.getY(), pos.getZ())
                    .subtract(localBase);
            buffer.addVertex((float) point.x, (float) point.y, (float) point.z);
        }
    }

    private static Matrix4f depthModelView(WorldTransform transform) {
        Vec3 translation = transform.worldBase.subtract(cameraPosition);
        Matrix4f object = new Matrix4f();
        object.m00((float) transform.axisX.x);
        object.m01((float) transform.axisX.y);
        object.m02((float) transform.axisX.z);
        object.m10((float) transform.axisY.x);
        object.m11((float) transform.axisY.y);
        object.m12((float) transform.axisY.z);
        object.m20((float) transform.axisZ.x);
        object.m21((float) transform.axisZ.y);
        object.m22((float) transform.axisZ.z);
        object.m30((float) translation.x);
        object.m31((float) translation.y);
        object.m32((float) translation.z);
        return new Matrix4f(modelViewMatrix).mul(object);
    }

    private static List<MaskQuad> maskQuads(SonarGlassBlockEntity endpoint) {
        if (endpoint.getLevel() == null) return List.of();
        MaskLayout layout = maskLayout(endpoint);
        WorldTransform transform = worldTransform(endpoint);
        List<MaskQuad> result = new ArrayList<>(layout.masks.size());
        for (LocalMask mask : layout.masks) {
            Vec3[] corners = mask.corners;
            if (corners == null) {
                corners = connectedFaceQuad(endpoint.getLevel(), mask.pos,
                        nearestFace(mask.pos, transform), .07);
            }
            addMaskQuad(result, transform, mask.pos,
                    corners[0], corners[1], corners[2], corners[3]);
        }
        return result;
    }

    private static Direction nearestFace(BlockPos pos, WorldTransform transform) {
        Direction best = Direction.NORTH;
        double bestDistance = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal()).scale(.5);
            Vec3 center = transform.apply(Vec3.atCenterOf(pos).add(normal));
            double distance = center.distanceToSqr(cameraPosition);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = direction;
            }
        }
        return best;
    }

    private static Vec3[] connectedFaceQuad(
            net.minecraft.world.level.Level level, BlockPos pos,
            Direction face, double inset) {
        boolean west = SonarGlass.isGlass(level, pos.west());
        boolean east = SonarGlass.isGlass(level, pos.east());
        boolean down = SonarGlass.isGlass(level, pos.below());
        boolean up = SonarGlass.isGlass(level, pos.above());
        boolean north = SonarGlass.isGlass(level, pos.north());
        boolean south = SonarGlass.isGlass(level, pos.south());

        double minX = west ? 0 : inset;
        double maxX = east ? 1 : 1 - inset;
        double minY = down ? 0 : inset;
        double maxY = up ? 1 : 1 - inset;
        double minZ = north ? 0 : inset;
        double maxZ = south ? 1 : 1 - inset;
        return switch (face) {
            case NORTH -> new Vec3[]{
                    new Vec3(minX, minY, 0), new Vec3(minX, maxY, 0),
                    new Vec3(maxX, maxY, 0), new Vec3(maxX, minY, 0)};
            case SOUTH -> new Vec3[]{
                    new Vec3(maxX, minY, 1), new Vec3(maxX, maxY, 1),
                    new Vec3(minX, maxY, 1), new Vec3(minX, minY, 1)};
            case WEST -> new Vec3[]{
                    new Vec3(0, minY, maxZ), new Vec3(0, maxY, maxZ),
                    new Vec3(0, maxY, minZ), new Vec3(0, minY, minZ)};
            case EAST -> new Vec3[]{
                    new Vec3(1, minY, minZ), new Vec3(1, maxY, minZ),
                    new Vec3(1, maxY, maxZ), new Vec3(1, minY, maxZ)};
            case DOWN -> new Vec3[]{
                    new Vec3(minX, 0, maxZ), new Vec3(minX, 0, minZ),
                    new Vec3(maxX, 0, minZ), new Vec3(maxX, 0, maxZ)};
            case UP -> new Vec3[]{
                    new Vec3(minX, 1, minZ), new Vec3(minX, 1, maxZ),
                    new Vec3(maxX, 1, maxZ), new Vec3(maxX, 1, minZ)};
        };
    }

    private static void addMaskQuad(List<MaskQuad> output, WorldTransform transform,
                                    BlockPos pos, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        output.add(new MaskQuad(new Vec3[]{
                transform.apply(a.add(pos.getX(), pos.getY(), pos.getZ())),
                transform.apply(b.add(pos.getX(), pos.getY(), pos.getZ())),
                transform.apply(c.add(pos.getX(), pos.getY(), pos.getZ())),
                transform.apply(d.add(pos.getX(), pos.getY(), pos.getZ()))
        }));
    }

    private static WorldTransform worldTransform(SonarGlassBlockEntity endpoint) {
        Vec3 localBase = Vec3.atLowerCornerOf(endpoint.getBlockPos());
        Vec3 worldBase = world(endpoint, localBase);
        Vec3 axisX = world(endpoint, localBase.add(1, 0, 0)).subtract(worldBase);
        Vec3 axisY = world(endpoint, localBase.add(0, 1, 0)).subtract(worldBase);
        Vec3 axisZ = world(endpoint, localBase.add(0, 0, 1)).subtract(worldBase);
        return new WorldTransform(localBase, worldBase, axisX, axisY, axisZ);
    }

    private static Vec3 world(SonarGlassBlockEntity endpoint, Vec3 localPosition) {
        return endpoint.getLevel() == null ? localPosition
                : PhysicsHandler.getWorldVec(endpoint.getLevel(), localPosition);
    }

    private static void renderPulses(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        long now = minecraft.level.getGameTime();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        List<WorldLine> lines = new ArrayList<>();
        appendPulseLines(lines, now + partial);
        if (!lines.isEmpty()) drawWorld(event.getPoseStack(), lines);
        PULSES.entrySet().removeIf(
                entry -> now - entry.getValue() > SonarGlassAnimation.PULSE_TICKS + 5);
    }

    private static void appendPulseLines(List<WorldLine> output, float now) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        for (Map.Entry<Long, Long> entry : PULSES.entrySet()) {
            float age = now - entry.getValue();
            if (age < 0 || age > SonarGlassAnimation.PULSE_TICKS) continue;
            BlockPos anchor = BlockPos.of(entry.getKey());
            if (!SonarGlass.isGlass(minecraft.level, anchor)) continue;
            float alpha = (float) Math.sin(
                    Math.PI * age / SonarGlassAnimation.PULSE_TICKS) * .85f;
            for (MaskQuad mask : maskQuadsAt(anchor)) {
                Vec3 normal = mask.corners[1].subtract(mask.corners[0])
                        .cross(mask.corners[3].subtract(mask.corners[0])).normalize();
                for (int i = 0; i < mask.corners.length; i++) {
                    output.add(new WorldLine(mask.corners[i],
                            mask.corners[(i + 1) % mask.corners.length],
                            normal, alpha));
                }
            }
        }
    }

    private static List<MaskQuad> maskQuadsAt(BlockPos anchor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !SonarGlass.isGlass(minecraft.level, anchor)) return List.of();
        if (minecraft.level.getBlockEntity(anchor)
                instanceof SonarGlassBlockEntity blockEntity) {
            return isWithinActivationDistance(blockEntity, cameraPosition,
                    ClientConfig.sonarGlassActivationDistance())
                    ? maskQuads(blockEntity) : List.of();
        }
        for (BlockPos pos : SonarGlassNetwork.find(
                minecraft.level, anchor).blocks()) {
            if (minecraft.level.getBlockEntity(pos)
                    instanceof SonarGlassBlockEntity blockEntity) {
                return isWithinActivationDistance(blockEntity, cameraPosition,
                        ClientConfig.sonarGlassActivationDistance())
                        ? maskQuads(blockEntity) : List.of();
            }
        }
        return List.of();
    }

    private static void drawWorld(PoseStack poseStack, List<WorldLine> lines) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        drawWorldPass(poseStack, lines, 3.4f, .22f, 185, 8, 12);
        drawWorldPass(poseStack, lines, 1.15f, .94f, 255, 92, 78);
        poseStack.popPose();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void drawWorldPass(PoseStack poseStack, List<WorldLine> lines,
                                      float pixelThickness, float alphaFactor,
                                      int red, int green, int blue) {
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        int screenHeight = Math.max(1,
                Minecraft.getInstance().getWindow().getGuiScaledHeight());
        for (WorldLine line : lines) {
            Vec3 direction = line.b.subtract(line.a);
            double length = direction.length();
            if (length < 1.0e-5) continue;
            direction = direction.scale(1 / length);
            Vec3 midpoint = line.a.add(line.b).scale(.5);
            Vec3 normal = line.normal;
            if (normal.dot(cameraPosition.subtract(midpoint)) < 0) {
                normal = normal.scale(-1);
            }
            double thickness = Math.max(.0015,
                    midpoint.distanceTo(cameraPosition)
                            * pixelThickness / screenHeight * .72);
            Vec3 side = normal.cross(direction).normalize().scale(thickness * .5);
            Vec3 offset = normal.scale(.006);
            Vec3 a = line.a.add(offset);
            Vec3 b = line.b.add(offset);
            int alpha = (int) (255 * Mth.clamp(line.alpha * alphaFactor, 0, 1));
            buffer.addVertex(matrix, (float) (a.x + side.x),
                    (float) (a.y + side.y), (float) (a.z + side.z))
                    .setColor(red, green, blue, alpha);
            buffer.addVertex(matrix, (float) (b.x + side.x),
                    (float) (b.y + side.y), (float) (b.z + side.z))
                    .setColor(red, green, blue, alpha);
            buffer.addVertex(matrix, (float) (b.x - side.x),
                    (float) (b.y - side.y), (float) (b.z - side.z))
                    .setColor(red, green, blue, alpha);
            buffer.addVertex(matrix, (float) (a.x - side.x),
                    (float) (a.y - side.y), (float) (a.z - side.z))
                    .setColor(red, green, blue, alpha);
        }
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static void closeDepthAperture(DepthAperture aperture) {
        if (aperture != null) aperture.buffer.close();
    }

    private record LocalMask(BlockPos pos, Vec3[] corners) {
    }

    private record MaskLayout(long generation, List<LocalMask> masks) {
    }

    private record DepthAperture(long generation, VertexBuffer buffer) {
    }

    private record WorldTransform(Vec3 localBase, Vec3 worldBase,
                                  Vec3 axisX, Vec3 axisY, Vec3 axisZ) {
        private Vec3 apply(Vec3 local) {
            Vec3 delta = local.subtract(localBase);
            return worldBase.add(axisX.scale(delta.x))
                    .add(axisY.scale(delta.y))
                    .add(axisZ.scale(delta.z));
        }

        private Vec3 toLocal(Vec3 world) {
            Vec3 delta = world.subtract(worldBase);
            return localBase.add(project(delta, axisX),
                    project(delta, axisY), project(delta, axisZ));
        }

        private static double project(Vec3 value, Vec3 axis) {
            double lengthSquared = axis.lengthSqr();
            return lengthSquared < 1.0e-9 ? 0
                    : value.dot(axis) / lengthSquared;
        }
    }

    private record MaskQuad(Vec3[] corners) {
    }

    private record WorldLine(Vec3 a, Vec3 b, Vec3 normal, float alpha) {
    }
}
