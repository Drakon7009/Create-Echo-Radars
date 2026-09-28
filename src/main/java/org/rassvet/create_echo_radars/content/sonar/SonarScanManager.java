package org.rassvet.create_echo_radars.content.sonar;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.config.ServerConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class SonarScanManager {
    private static final int SCAN_TIMEOUT_TICKS = 600;
    private static final float MECHANICAL_PREFETCH_TICKS = 6;
    private static final float MECHANICAL_MAX_PREFETCH_DEGREES = 120;
    private static final int MECHANICAL_COMPLETED_FRAME_RETENTION_TICKS = 20;
    private static final int MECHANICAL_COMPLETED_FRAME_LIMIT = 32;
    private static final int AUTO_DISPLAY_RANGE_HISTORY_FRAMES = 16;
    private static final int SIDE_SCAN_AUTO_DISPLAY_RANGE_HISTORY_FRAMES = 192;
    private static final double REVEAL_SAFETY_BLOCKS = 1.0;
    private static final Map<ServerLevel, SonarScanManager> INSTANCES = new WeakHashMap<>();

    private final ServerLevel level;
    private final SonarChunkReader chunkReader;
    private final Map<Long, ScanJob> jobs = new HashMap<>();
    private final Map<Long, Long> touches = new HashMap<>();
    private final Map<Long, CompletableFuture<Optional<OfflineSettings>>> offlineSettings = new HashMap<>();

    private SonarScanManager(ServerLevel level) {
        this.level = level;
        this.chunkReader = new SonarChunkReader(level);
    }

    public static SonarScanManager get(ServerLevel level) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(level, SonarScanManager::new);
        }
    }

    public void touch(SonarBlockEntity sonar) {
        BlockPos pos = sonar.getBlockPos();
        touches.put(pos.asLong(), level.getGameTime());
        offlineSettings.remove(pos.asLong());
        SonarOrientation orientation = sonar.orientation();
        SonarOrientation displayOrientation = sonar.displayOrientation();
        Descriptor descriptor = new Descriptor(pos, sonar.emitterPosition(), sonar.displayOrigin(),
                orientation.forward(), orientation.right(), orientation.up(),
                displayOrientation.forward(), displayOrientation.right(), displayOrientation.up(),
                sonar.getSonarType(), sonar.getSonarRange(), sonar.getHorizontalSector(),
                sonar.getVerticalSector(), sonar.getTiltAngle(), sonar.mechanicalAngle(),
                sonar.mechanicalAngularSpeed(), level.getGameTime(), sonar.isRunning(),
                ServerConfig.horizontalBeams(sonar.getSonarType()),
                ServerConfig.verticalBeams(sonar.getSonarType()),
                ServerConfig.additionalRays(), ServerConfig.refineOnlyUndetectedNeighbors(),
                ServerConfig.hitRefinementBacktrackBlocks(), sonar.isAutoHeight());
        ensureJob(descriptor);
    }

    public void touch(BlockPos sonarPos) {
        touches.put(sonarPos.asLong(), level.getGameTime());
    }

    public SonarMonitorSnapshot snapshot(BlockPos sonarPos) {
        ScanJob job = jobs.get(sonarPos.asLong());
        if (job == null) return null;
        return job.snapshot();
    }

    public void tick() {
        SonarTraceProfiler.tick(level.getServer());
        chunkReader.tick();
        long now = level.getGameTime();
        resolveTouchedSonars(now);

        Iterator<ScanJob> iterator = jobs.values().iterator();
        while (iterator.hasNext()) {
            ScanJob job = iterator.next();
            long lastTouch = touches.getOrDefault(job.descriptor.pos.asLong(), Long.MIN_VALUE);
            if (now - lastTouch > 40) {
                chunkReader.discard(job.usedChunks);
                iterator.remove();
                continue;
            }
            job.tick(now);
        }
        touches.entrySet().removeIf(entry -> now - entry.getValue() > 40);
    }

    private void resolveTouchedSonars(long now) {
        for (Map.Entry<Long, Long> entry : touches.entrySet()) {
            if (now - entry.getValue() > 40) continue;
            BlockPos pos = BlockPos.of(entry.getKey());
            if (level.getBlockEntity(pos) instanceof SonarBlockEntity sonar) {
                touch(sonar);
                continue;
            }
            CompletableFuture<Optional<OfflineSettings>> future = offlineSettings.computeIfAbsent(
                    entry.getKey(), ignored -> readOfflineSettings(pos));
            if (!future.isDone()) continue;
            Optional<OfflineSettings> settings = future.getNow(Optional.empty());
            if (settings.isEmpty()) continue;
            SonarChunkReader.Cell cell = chunkReader.probe(pos, new LongOpenHashSet());
            if (cell.type() == SonarChunkReader.CellType.PENDING
                    || cell.type() == SonarChunkReader.CellType.UNKNOWN
                    || cell.state() == null || !(cell.state().getBlock() instanceof SonarBlock sonarBlock)) continue;
            Direction facing = cell.state().getValue(SonarBlock.FACING);
            SonarOrientation baseOrientation = new SonarOrientation(
                    Vec3.atLowerCornerOf(facing.getNormal()),
                    Vec3.atLowerCornerOf(facing.getClockWise().getNormal()),
                    new Vec3(0, 1, 0));
            if (cell.state().getValue(SonarBlock.UPSIDE_DOWN)) {
                baseOrientation = new SonarOrientation(baseOrientation.forward().scale(-1),
                        baseOrientation.right(), baseOrientation.up().scale(-1));
            }
            SonarOrientation unTilted = switch (sonarBlock.sonarType()) {
                case ECHO_SOUNDER_A -> new SonarOrientation(baseOrientation.up().scale(-1),
                        baseOrientation.right(), baseOrientation.forward());
                case MECHANICAL_IMAGING_C -> new SonarOrientation(
                        baseOrientation.direction(settings.get().mechanicalAngle, 0),
                        baseOrientation.direction(settings.get().mechanicalAngle + 90, 0), baseOrientation.up());
                case SIDE_SCAN_D, FORWARD_LOOKING_F -> baseOrientation;
            };
            int resolvedTiltAngle = SonarBlockEntity.clampTilt(
                    sonarBlock.sonarType(), settings.get().tiltAngle);
            SonarOrientation orientation = sonarBlock.sonarType() == SonarType.SIDE_SCAN_D
                    ? unTilted : SonarBlockEntity.applyTilt(unTilted, resolvedTiltAngle);
            Vec3 origin = SonarBlockEntity.emitterPosition(Vec3.atCenterOf(pos),
                    sonarBlock.sonarType(), unTilted, settings.get().mechanicalAngle);
            Vec3 displayOrigin = sonarBlock.sonarType() == SonarType.MECHANICAL_IMAGING_C
                    ? Vec3.atCenterOf(pos).add(unTilted.up().scale(0.25)) : origin;
            SonarOrientation displayOrientation = sonarBlock.sonarType() == SonarType.MECHANICAL_IMAGING_C
                    ? baseOrientation : orientation;
            int resolvedHorizontalSector = clamp(settings.get().sector,
                    SonarBlockEntity.MIN_ANGLE, sonarBlock.sonarType().maximumHorizontalAngle());
            int resolvedVerticalSector = clamp(settings.get().verticalSector,
                    SonarBlockEntity.MIN_ANGLE, sonarBlock.sonarType().maximumVerticalAngle());
            int effectiveRange = Math.min(settings.get().range,
                    SonarRangeLimit.effectiveMaximumRange(sonarBlock.sonarType(),
                            resolvedHorizontalSector, resolvedVerticalSector,
                            ServerConfig.maximumSonarRange(), ServerConfig.angleRangeReduction()));
            ensureJob(new Descriptor(pos, origin, displayOrigin, orientation.forward(),
                    orientation.right(), orientation.up(),
                    displayOrientation.forward(), displayOrientation.right(), displayOrientation.up(),
                    sonarBlock.sonarType(), effectiveRange, resolvedHorizontalSector,
                    resolvedVerticalSector, resolvedTiltAngle, settings.get().mechanicalAngle,
                    0, level.getGameTime(),
                    sonarBlock.sonarType() != SonarType.MECHANICAL_IMAGING_C,
                    ServerConfig.horizontalBeams(sonarBlock.sonarType()),
                    ServerConfig.verticalBeams(sonarBlock.sonarType()),
                    ServerConfig.additionalRays(), ServerConfig.refineOnlyUndetectedNeighbors(),
                    ServerConfig.hitRefinementBacktrackBlocks(), settings.get().autoHeight));
        }
    }

    private CompletableFuture<Optional<OfflineSettings>> readOfflineSettings(BlockPos pos) {
        return level.getChunkSource().chunkMap.read(new net.minecraft.world.level.ChunkPos(pos))
                .thenApply(optional -> optional.flatMap(tag -> {
                    ListTag blockEntities = tag.getList("block_entities", Tag.TAG_COMPOUND);
                    for (int i = 0; i < blockEntities.size(); i++) {
                        CompoundTag blockEntity = blockEntities.getCompound(i);
                        if (blockEntity.getInt("x") == pos.getX()
                                && blockEntity.getInt("y") == pos.getY()
                                && blockEntity.getInt("z") == pos.getZ()) {
                            int range = clamp(blockEntity.contains("Range") ? blockEntity.getInt("Range")
                                    : SonarBlockEntity.MAX_RANGE,
                                    SonarBlockEntity.MIN_RANGE, ServerConfig.MAXIMUM_SONAR_RANGE_LIMIT);
                            int sector = clamp(blockEntity.contains("Sector") ? blockEntity.getInt("Sector")
                                    : SonarType.FORWARD_LOOKING_F.defaultHorizontalAngle(),
                                    SonarBlockEntity.MIN_ANGLE, SonarBlockEntity.MAX_ANGLE);
                            int verticalSector = clamp(blockEntity.contains("VerticalSector")
                                            ? blockEntity.getInt("VerticalSector")
                                            : SonarType.FORWARD_LOOKING_F.defaultVerticalAngle(),
                                     SonarBlockEntity.MIN_ANGLE, SonarBlockEntity.MAX_ANGLE);
                            int tiltAngle = clamp(blockEntity.getInt("TiltAngle"),
                                    SonarBlockEntity.MIN_TILT, SonarBlockEntity.MAX_TILT);
                            boolean autoHeight = !blockEntity.contains("AutoHeight")
                                    || blockEntity.getBoolean("AutoHeight");
                            return Optional.of(new OfflineSettings(range, sector, verticalSector, tiltAngle,
                                    blockEntity.getFloat("MechanicalAngle"), autoHeight));
                        }
                    }
                    return Optional.empty();
                })).exceptionally(error -> {
                    CreateEchoRadars.LOGGER.debug("Could not read offline sonar {}", pos, error);
                    return Optional.empty();
                });
    }

    private void ensureJob(Descriptor descriptor) {
        long key = descriptor.pos.asLong();
        ScanJob current = jobs.get(key);
        if (current == null || !current.descriptor.sameScanSettings(descriptor)) {
            if (current != null) chunkReader.discard(current.usedChunks);
            jobs.put(key, new ScanJob(descriptor));
        } else {
            current.updateDescriptor(descriptor);
        }
    }

    private final class ScanJob {
        private Descriptor descriptor;
        private Descriptor scanDescriptor;
        private final LongSet usedChunks = new LongOpenHashSet();
        private final Map<EchoKey, EchoAccumulator> echoes = new HashMap<>();
        private final NearbyBlockTracker detectedHitBlocks = new NearbyBlockTracker();
        private final List<SideScanRefinementCandidate> sideScanRefinementCandidates =
                new ArrayList<>();
        private List<RayState> rays = List.of();
        private List<SonarReturn> cachedReturns;
        private SonarFrame cachedCurrentFrame;
        private List<SonarFrame> cachedVisibleFrames;
        private SonarMonitorSnapshot cachedSnapshot;
        private CompletableFuture<SonarTraceExecutor.BatchResult> activeBatch;
        private long activeBatchId;
        private long nextBatchId;
        private long epoch;
        private SonarFrame lastCompletedFrame;
        private final MechanicalFrameWindow<SonarFrame> mechanicalCompletedFrames =
                new MechanicalFrameWindow<>(MECHANICAL_COMPLETED_FRAME_RETENTION_TICKS,
                        MECHANICAL_COMPLETED_FRAME_LIMIT);
        private int displayRange;
        private final AutoDisplayRangeTracker autoDisplayRange;
        private long startedTick;
        private long holdUntil;
        private boolean holding;
        private Vec3 lastSideScanOrigin;
        private float mechanicalScanCursor = Float.NaN;
        private int mechanicalScanDirection;
        private List<Float> mechanicalSampleAngles = List.of();

        private ScanJob(Descriptor descriptor) {
            this.descriptor = descriptor;
            displayRange = descriptor.range;
            autoDisplayRange = new AutoDisplayRangeTracker(
                    descriptor.type == SonarType.SIDE_SCAN_D
                            ? SIDE_SCAN_AUTO_DISPLAY_RANGE_HISTORY_FRAMES
                            : AUTO_DISPLAY_RANGE_HISTORY_FRAMES,
                    descriptor.range);
            startScan(level.getGameTime());
        }

        private void updateDescriptor(Descriptor descriptor) {
            if (!this.descriptor.equals(descriptor)) {
                this.descriptor = descriptor;
                invalidateSnapshot();
            }
        }

        private void tick(long now) {
            if (descriptor.type == SonarType.MECHANICAL_IMAGING_C
                    && mechanicalCompletedFrames.prune(now)) {
                invalidateVisibleFrames();
            }
            EmitterState emitterState = emitterState();
            if (emitterState == EmitterState.PENDING) {
                return;
            }
            if (emitterState == EmitterState.DRY) {
                clearEchoes();
                return;
            }
            if (!descriptor.running) {
                holding = true;
                holdUntil = now;
                return;
            }
            if (holding) {
                if (descriptor.type == SonarType.MECHANICAL_IMAGING_C) {
                    if (now >= holdUntil) startScan(now);
                    else return;
                } else {
                    double sideScanMovement = lastSideScanOrigin == null ? 0
                            : descriptor.origin.distanceTo(lastSideScanOrigin);
                    if (now >= holdUntil && (descriptor.type != SonarType.SIDE_SCAN_D
                            || SideScanGeometry.readyForNextPing(
                            ServerConfig.sideScanMovementOnly(), lastSideScanOrigin != null,
                            sideScanMovement))) startScan(now);
                    return;
                }
            }

            completeActiveBatch();
            if (activeBatch == null) startBatch();
            completeActiveBatch();
            if (activeBatch != null) {
                return;
            }

            boolean complete = true;
            for (RayState ray : rays) {
                if (!ray.finished) {
                    complete = false;
                    break;
                }
            }
            boolean timedOut = now - startedTick >= SCAN_TIMEOUT_TICKS;
            if (complete || timedOut) {
                if (timedOut && !sideScanRefinementCandidates.isEmpty()) {
                    flushSideScanCandidatesAsEchoes();
                }
                SonarFrame completedFrame = buildFrame(now, complete);
                if (descriptor.type == SonarType.MECHANICAL_IMAGING_C) {
                    mechanicalCompletedFrames.add(now, completedFrame);
                } else {
                    lastCompletedFrame = completedFrame;
                }
                if (completedFrame.complete()) updateDisplayRange(completedFrame);
                invalidateVisibleFrames();
                holding = true;
                holdUntil = descriptor.type == SonarType.MECHANICAL_IMAGING_C
                        ? now : now + (descriptor.type == SonarType.SIDE_SCAN_D
                        ? ServerConfig.sideScanPingPauseTicks() : ServerConfig.pingPauseTicks());
                chunkReader.discard(usedChunks);
                usedChunks.clear();
            }
        }

        private EmitterState emitterState() {
            if (descriptor.type != SonarType.SIDE_SCAN_D) {
                return emitterState(chunkReader.probe(
                        BlockPos.containing(descriptor.origin), usedChunks));
            }

            boolean pending = false;
            Vec3 emitterAxis = descriptor.forward;
            for (double forward : new double[]{-SideScanGeometry.EMITTER_FORWARD_OFFSET,
                    SideScanGeometry.EMITTER_FORWARD_OFFSET}) {
                Vec3 emitter = descriptor.origin.add(emitterAxis.scale(forward));
                EmitterState state = emitterState(chunkReader.probe(
                        BlockPos.containing(emitter), usedChunks));
                if (state == EmitterState.WATER) return EmitterState.WATER;
                pending |= state == EmitterState.PENDING;
            }
            return pending ? EmitterState.PENDING : EmitterState.DRY;
        }

        private EmitterState emitterState(SonarChunkReader.Cell cell) {
            return switch (cell.type()) {
                case WATER -> EmitterState.WATER;
                case PENDING, UNKNOWN -> EmitterState.PENDING;
                default -> EmitterState.DRY;
            };
        }

        private void startScan(long now) {
            if (descriptor.type == SonarType.MECHANICAL_IMAGING_C && !prepareMechanicalSamples()) {
                holding = true;
                holdUntil = now;
                return;
            }
            scanDescriptor = descriptor;
            epoch++;
            startedTick = now;
            holding = false;
            if (scanDescriptor.type != SonarType.MECHANICAL_IMAGING_C
                    && level.getBlockEntity(scanDescriptor.pos) instanceof SonarBlockEntity sonar) {
                SonarOrientation scanOrientation = new SonarOrientation(
                        scanDescriptor.forward, scanDescriptor.right, scanDescriptor.up);
                sonar.scanEntitiesAtScanStart(scanDescriptor.origin, scanOrientation,
                        scanDescriptor.range, scanDescriptor.sector, scanDescriptor.verticalSector,
                        scanDescriptor.autoHeight);
            }
            if (scanDescriptor.type == SonarType.SIDE_SCAN_D) lastSideScanOrigin = scanDescriptor.origin;
            sideScanRefinementCandidates.clear();
            clearEchoes();
            usedChunks.clear();
            activeBatch = null;
            rays = createRays(scanDescriptor, mechanicalSampleAngles);
            invalidateReturns();
        }

        private boolean prepareMechanicalSamples() {
            int direction = Float.compare(descriptor.mechanicalAngularSpeed, 0);
            if (direction == 0) return false;
            float prefetchAngle = SonarRotation.prefetchAngle(descriptor.mechanicalAngle,
                    descriptor.mechanicalAngularSpeed, SonarRotation.MECHANICAL_SCAN_STEP_DEGREES,
                    MECHANICAL_PREFETCH_TICKS, MECHANICAL_MAX_PREFETCH_DEGREES);
            if (Float.isNaN(mechanicalScanCursor) || direction != mechanicalScanDirection) {
                mechanicalScanDirection = direction;
                mechanicalScanCursor = SonarRotation.scanStep(
                        descriptor.mechanicalAngle, SonarRotation.MECHANICAL_SCAN_STEP_DEGREES);
            }
            mechanicalSampleAngles = SonarRotation.crossedScanSteps(mechanicalScanCursor,
                    prefetchAngle, descriptor.mechanicalAngularSpeed,
                    SonarRotation.MECHANICAL_SCAN_STEP_DEGREES);
            if (mechanicalSampleAngles.isEmpty()) return false;
            mechanicalScanCursor = mechanicalSampleAngles.get(mechanicalSampleAngles.size() - 1);
            return true;
        }

        private void startBatch() {
            List<TraceRay> traceRays = new ArrayList<>();
            LongSet sections = new LongOpenHashSet();
            int blocksPerTick = ServerConfig.blocksPerTick();
            SonarAdaptiveTracePlan.Settings settings = scanDescriptor.traceSettings();
            for (int i = 0; i < rays.size(); i++) {
                RayState ray = rays.get(i);
                if (ray.finished) continue;
                Vec3 rayOrigin = ray.origin(scanDescriptor);
                Vec3 direction = ray.direction(scanDescriptor);
                // Waterfall sonars need one measurement from one boat pose. Advancing
                // them a few blocks per tick makes a single ping take many seconds.
                double end = scanDescriptor.type == SonarType.MECHANICAL_IMAGING_C
                        || scanDescriptor.type == SonarType.SIDE_SCAN_D
                        || scanDescriptor.type == SonarType.ECHO_SOUNDER_A
                        ? scanDescriptor.range
                        : SonarAdaptiveTracePlan.nextTraceEnd(ray.distance,
                        settings, ray.leaf, blocksPerTick);
                traceRays.add(new TraceRay(i, rayOrigin.x, rayOrigin.y, rayOrigin.z,
                        direction.x, direction.y, direction.z, ray.distance, end));
                collectSections(rayOrigin, direction, ray.distance, end, sections);
            }
            if (traceRays.isEmpty()) return;

            Optional<SonarChunkReader.SonarWorldSnapshot> snapshot =
                    chunkReader.prepareSnapshot(sections, usedChunks);
            if (snapshot.isEmpty()) return;

            SableSonarCompat.Snapshot sableSnapshot = SableSonarCompat.emptySnapshot();
            if (SableSonarCompat.hasLoadedSubLevels(level)) {
                List<SableSonarCompat.RaySegment> sableRays = new ArrayList<>(traceRays.size());
                for (TraceRay ray : traceRays) {
                    sableRays.add(new SableSonarCompat.RaySegment(
                            ray.originX, ray.originY, ray.originZ,
                            ray.directionX, ray.directionY, ray.directionZ,
                            ray.startDistance, ray.endDistance));
                }
                Vec3 ownerSample = switch (scanDescriptor.type) {
                    case ECHO_SOUNDER_A -> scanDescriptor.origin.subtract(
                            scanDescriptor.forward.scale(0.501));
                    case SIDE_SCAN_D -> scanDescriptor.origin.add(
                            scanDescriptor.up.scale(1.0 / 32.0));
                    default -> scanDescriptor.origin;
                };
                sableSnapshot = SableSonarCompat.capture(level,
                        scanDescriptor.origin, ownerSample,
                        new SonarOrientation(scanDescriptor.forward,
                                scanDescriptor.right, scanDescriptor.up),
                        scanDescriptor.range,
                        scanDescriptor.type == SonarType.SIDE_SCAN_D ? 180 : scanDescriptor.sector,
                        scanDescriptor.type == SonarType.SIDE_SCAN_D ? 180 : scanDescriptor.verticalSector,
                        sableRays);
            }

            long batchId = ++nextBatchId;
            activeBatchId = batchId;
            long batchEpoch = epoch;
            activeBatch = traceAsync(level.getServer(),
                    level.dimension().location() + ":" + scanDescriptor.pos.asLong(),
                    batchEpoch, batchId, traceRays, snapshot.get(), sableSnapshot);
        }

        private void completeActiveBatch() {
            if (activeBatch == null || !activeBatch.isDone()) return;
            SonarTraceExecutor.BatchResult result;
            try {
                result = activeBatch.join();
            } catch (CompletionException error) {
                CreateEchoRadars.LOGGER.warn("Sonar trace batch failed for {}", scanDescriptor.pos, error);
                activeBatch = null;
                return;
            }
            activeBatch = null;
            if (!result.matches(epoch, activeBatchId)) return;
            mergeBatch(result);
        }

        private void mergeBatch(SonarTraceExecutor.BatchResult result) {
            List<RayState> refinements = null;
            boolean echoesChanged = false;
            SonarAdaptiveTracePlan.Settings settings = scanDescriptor.traceSettings();
            for (SonarTraceExecutor.RayResult rayResult : result.rays()) {
                if (rayResult.rayIndex() < 0 || rayResult.rayIndex() >= rays.size()) continue;
                RayState ray = rays.get(rayResult.rayIndex());
                if (ray.finished) continue;
                ray.distance = rayResult.distance();
                if (rayResult.hit()) {
                    if (shouldDelaySideScanRefinement(ray)) {
                        sideScanRefinementCandidates.add(new SideScanRefinementCandidate(
                                ray, rayResult, hitCell(ray, rayResult.distance())));
                        ray.finished = true;
                        continue;
                    }
                    boolean canCreateAdditionalRays =
                            scanDescriptor.type != SonarType.MECHANICAL_IMAGING_C;
                    if (canCreateAdditionalRays && !ray.leaf.refinement()
                            && scanDescriptor.refineOnlyUndetectedNeighbors) {
                        canCreateAdditionalRays = !hasNearbyDetectedHit(ray, rayResult.distance());
                    }
                    List<SonarAdaptiveTracePlan.Leaf> children = canCreateAdditionalRays
                            ? (scanDescriptor.type == SonarType.SIDE_SCAN_D
                            ? SonarAdaptiveTracePlan.sideScanRefinementsForHit(
                            ray.leaf, rayResult.distance(), settings)
                            : SonarAdaptiveTracePlan.refinementsForHit(
                            ray.leaf, rayResult.distance(), settings)) : List.of();
                    if (!children.isEmpty()) {
                        if (refinements == null) refinements = new ArrayList<>(children.size());
                        for (SonarAdaptiveTracePlan.Leaf leaf : children) {
                            refinements.add(new RayState(leaf, leaf.refinementStartDistance()));
                        }
                    } else {
                        addEcho(ray, rayResult, settings);
                        echoesChanged = true;
                    }
                    ray.finished = true;
                } else if (ray.distance >= scanDescriptor.range - 1.0e-5) {
                    ray.finished = true;
                } else if (ray.leaf.refinement()
                        && ray.distance >= ray.leaf.refinementEndDistance() - 1.0e-5) {
                    ray.finished = true;
                }
            }
            if (allPrimaryRaysFinished() && !sideScanRefinementCandidates.isEmpty()) {
                if (refinements == null) refinements = new ArrayList<>();
                echoesChanged |= resolveSideScanRefinementCandidates(refinements, settings);
            }
            if (refinements != null) {
                List<RayState> next = new ArrayList<>(rays.size() + refinements.size());
                next.addAll(rays);
                next.addAll(refinements);
                rays = next;
            }
            if (echoesChanged) invalidateReturns();
            else invalidateProgress();
        }

        private boolean shouldDelaySideScanRefinement(RayState ray) {
            return scanDescriptor.type == SonarType.SIDE_SCAN_D
                    && scanDescriptor.refineOnlyUndetectedNeighbors
                    && !ray.leaf.refinement();
        }

        private boolean allPrimaryRaysFinished() {
            for (RayState ray : rays) {
                if (!ray.leaf.refinement() && !ray.finished) return false;
            }
            return true;
        }

        private boolean resolveSideScanRefinementCandidates(
                List<RayState> refinements, SonarAdaptiveTracePlan.Settings settings) {
            List<SideScanHitFilter.Cell> hits = new ArrayList<>(
                    sideScanRefinementCandidates.size());
            for (SideScanRefinementCandidate candidate : sideScanRefinementCandidates) {
                hits.add(candidate.cell());
            }
            boolean[] refinementAllowed = SideScanHitFilter.refinementsAllowed(hits);
            boolean echoesChanged = false;
            for (int i = 0; i < sideScanRefinementCandidates.size(); i++) {
                SideScanRefinementCandidate candidate = sideScanRefinementCandidates.get(i);
                List<SonarAdaptiveTracePlan.Leaf> children = refinementAllowed[i]
                        ? SonarAdaptiveTracePlan.sideScanRefinementsForHit(
                        candidate.ray().leaf, candidate.result().distance(), settings)
                        : List.of();
                if (children.isEmpty()) {
                    addEcho(candidate.ray(), candidate.result(), settings);
                    echoesChanged = true;
                } else {
                    for (SonarAdaptiveTracePlan.Leaf leaf : children) {
                        refinements.add(new RayState(leaf, leaf.refinementStartDistance()));
                    }
                }
            }
            sideScanRefinementCandidates.clear();
            return echoesChanged;
        }

        private void flushSideScanCandidatesAsEchoes() {
            SonarAdaptiveTracePlan.Settings settings = scanDescriptor.traceSettings();
            for (SideScanRefinementCandidate candidate : sideScanRefinementCandidates) {
                addEcho(candidate.ray(), candidate.result(), settings);
            }
            sideScanRefinementCandidates.clear();
            invalidateReturns();
        }

        private SideScanHitFilter.Cell hitCell(RayState ray, double distance) {
            Vec3 hit = ray.origin(scanDescriptor).add(
                    ray.direction(scanDescriptor).scale(distance + 1.0e-4));
            int x = (int) Math.floor(hit.x);
            int y = (int) Math.floor(hit.y);
            int z = (int) Math.floor(hit.z);
            return new SideScanHitFilter.Cell(x, y, z);
        }

        private boolean hasNearbyDetectedHit(RayState ray, double distance) {
            SideScanHitFilter.Cell hit = hitCell(ray, distance);
            return detectedHitBlocks.hasNearbyAndRecord(hit.x(), hit.y(), hit.z());
        }

        private void addEcho(RayState ray, SonarTraceExecutor.RayResult rayResult,
                             SonarAdaptiveTracePlan.Settings settings) {
            int bin = clamp((int) Math.floor(rayResult.distance()), 0, scanDescriptor.range - 1);
            float reflectivity = rayResult.airBoundary() ? 1 : materialReflectivity(rayResult.state());
            float intensity = SonarKinematics.reflectedIntensity(
                    (float) rayResult.incidence(), (float) (rayResult.distance() / scanDescriptor.range))
                    * reflectivity;
            float bearing = (float) SonarAdaptiveTracePlan.bearing(ray.leaf, settings);
            float elevation = (float) SonarAdaptiveTracePlan.pitch(ray.leaf, settings);
            if (scanDescriptor.type == SonarType.MECHANICAL_IMAGING_C) {
                bearing = MechanicalScanPlan.absoluteBearing(ray.leaf,
                        scanDescriptor.mechanicalAngle, settings);
            }
            float angularResolution = ray.leaf.angularResolutionDegrees() > 0
                    ? (float) ray.leaf.angularResolutionDegrees()
                    : SonarAdaptiveTracePlan.baseAngularResolutionDegrees(settings);
            float verticalAngularResolution = scanDescriptor.type == SonarType.MECHANICAL_IMAGING_C
                    ? SonarAdaptiveTracePlan.baseVerticalAngularResolutionDegrees(settings)
                    : (ray.leaf.angularResolutionDegrees() > 0
                    ? (float) ray.leaf.angularResolutionDegrees()
                    : SonarAdaptiveTracePlan.baseVerticalAngularResolutionDegrees(settings));
            EchoKey key = new EchoKey(ray.leaf.beam(), ray.leaf.vertical(), bin,
                    Math.round(bearing * 1000), Math.round(elevation * 1000));
            echoes.computeIfAbsent(key, ignored -> new EchoAccumulator())
                    .add(intensity, (float) (rayResult.distance() / scanDescriptor.range),
                            bearing, elevation, angularResolution, verticalAngularResolution);
        }

        private SonarMonitorSnapshot snapshot() {
            if (cachedSnapshot == null) {
                cachedSnapshot = new SonarMonitorSnapshot(visibleFrames(), descriptor.range, displayRange,
                        descriptor.sector, descriptor.verticalSector, descriptor.horizontalBeams,
                        descriptor.type, descriptor.mechanicalAngle, descriptor.mechanicalAngularSpeed,
                        descriptor.mechanicalAngleTick, descriptor.autoHeight,
                        descriptor.displayOrigin, descriptor.displayForward,
                        descriptor.displayRight, descriptor.displayUp);
            }
            return cachedSnapshot;
        }

        private SonarFrame buildFrame(long completedTick, boolean complete) {
            return new SonarFrame(epoch, startedTick, completedTick, complete,
                    safeRevealProgress(complete), buildReturns(), frameScanAngles());
        }

        private SonarFrame currentFrame() {
            if (cachedCurrentFrame == null) {
                cachedCurrentFrame = new SonarFrame(epoch, startedTick, 0, false,
                        safeRevealProgress(false), buildReturns(), frameScanAngles());
            }
            return cachedCurrentFrame;
        }

        private List<Float> frameScanAngles() {
            return scanDescriptor != null && scanDescriptor.type == SonarType.MECHANICAL_IMAGING_C
                    ? mechanicalSampleAngles : List.of();
        }

        private float safeRevealProgress(boolean complete) {
            if (complete || rays.isEmpty()) return 1;
            double safeDistance = scanDescriptor.range;
            boolean unfinished = false;
            for (RayState ray : rays) {
                if (ray.finished) continue;
                unfinished = true;
                safeDistance = Math.min(safeDistance, ray.distance);
            }
            if (!unfinished) return 1;
            return (float) Math.max(0, Math.min(1,
                    (safeDistance - REVEAL_SAFETY_BLOCKS) / Math.max(1, scanDescriptor.range)));
        }

        private List<SonarReturn> buildReturns() {
            if (cachedReturns != null) return cachedReturns;
            List<SonarReturn> returns = new ArrayList<>(echoes.size());
            echoes.forEach((key, accumulator) -> {
                returns.add(new SonarReturn(key.beam(), key.rangeBin(), accumulator.bearing(),
                        accumulator.elevation(), accumulator.normalizedDistance(), accumulator.average(),
                        accumulator.angularResolution(), accumulator.verticalAngularResolution()));
            });
            returns.sort(Comparator.comparingInt(SonarReturn::beam).thenComparingInt(SonarReturn::rangeBin));
            cachedReturns = List.copyOf(returns);
            return cachedReturns;
        }

        private List<SonarFrame> visibleFrames() {
            if (cachedVisibleFrames != null) return cachedVisibleFrames;
            List<SonarFrame> frames = new ArrayList<>();
            if (descriptor.type == SonarType.MECHANICAL_IMAGING_C) {
                frames.addAll(mechanicalCompletedFrames.values());
            } else if (lastCompletedFrame != null) {
                frames.add(lastCompletedFrame);
            }
            if (!holding) frames.add(currentFrame());
            cachedVisibleFrames = List.copyOf(frames);
            return cachedVisibleFrames;
        }

        private void updateDisplayRange(SonarFrame frame) {
            int nextDisplayRange = scanDescriptor.autoHeight
                    ? autoDisplayRange.update(detectedRange(frame, scanDescriptor.range),
                    scanDescriptor.range) : scanDescriptor.range;
            if (displayRange == nextDisplayRange) return;
            displayRange = nextDisplayRange;
            invalidateSnapshot();
        }

        private void clearEchoes() {
            detectedHitBlocks.clear();
            if (echoes.isEmpty()) return;
            echoes.clear();
            invalidateReturns();
        }

        private void invalidateReturns() {
            cachedReturns = null;
            cachedCurrentFrame = null;
            invalidateVisibleFrames();
        }

        private void invalidateVisibleFrames() {
            cachedVisibleFrames = null;
            invalidateSnapshot();
        }

        private void invalidateSnapshot() {
            cachedSnapshot = null;
        }

        private void invalidateProgress() {
            cachedCurrentFrame = null;
            if (!holding) invalidateVisibleFrames();
        }
    }

    private static List<RayState> createRays(Descriptor descriptor, List<Float> mechanicalSampleAngles) {
        if (descriptor.type == SonarType.MECHANICAL_IMAGING_C) {
            List<SonarAdaptiveTracePlan.Leaf> leaves = MechanicalScanPlan.createLeaves(
                    mechanicalSampleAngles, descriptor.mechanicalAngle, descriptor.traceSettings());
            List<RayState> rays = new ArrayList<>(leaves.size());
            for (SonarAdaptiveTracePlan.Leaf leaf : leaves) rays.add(new RayState(leaf, 0));
            return rays;
        }
        if (descriptor.type == SonarType.SIDE_SCAN_D) {
            List<SonarAdaptiveTracePlan.Leaf> leaves = SideScanGeometry.createLeaves(
                    descriptor.horizontalBeams, descriptor.verticalBeams);
            List<RayState> rays = new ArrayList<>(leaves.size());
            for (SonarAdaptiveTracePlan.Leaf leaf : leaves) rays.add(new RayState(leaf, 0));
            return rays;
        }
        List<RayState> rays = new ArrayList<>(descriptor.horizontalBeams * descriptor.verticalBeams);
        for (int beam = 0; beam < descriptor.horizontalBeams; beam++) {
            for (int vertical = 0; vertical < descriptor.verticalBeams; vertical++) {
                rays.add(new RayState(new SonarAdaptiveTracePlan.Leaf(beam, vertical,
                        0, 0), 0));
            }
        }
        return rays;
    }

    static void collectSections(Vec3 origin, Vec3 direction, double startDistance, double endDistance,
                                LongSet sections) {
        collectSections(origin.x, origin.y, origin.z, direction.x, direction.y, direction.z,
                startDistance, endDistance, sections);
    }

    static void collectSections(double originX, double originY, double originZ,
                                double directionX, double directionY, double directionZ,
                                double startDistance, double endDistance,
                                LongSet sections) {
        SonarSectionDda.traceSections(originX, originY, originZ,
                directionX, directionY, directionZ, startDistance, endDistance,
                (x, y, z) -> sections.add(SectionPos.asLong(x, y, z)));
    }

    private static CompletableFuture<SonarTraceExecutor.BatchResult> traceAsync(
            net.minecraft.server.MinecraftServer server,
            String sonarId,
            long epoch,
            long batchId,
            List<TraceRay> rays,
            SonarChunkReader.SonarWorldSnapshot snapshot,
            SableSonarCompat.Snapshot sableSnapshot
    ) {
        List<SonarTraceExecutor.Range> ranges =
                SonarTraceExecutor.splitRanges(rays.size(), ServerConfig.traceWorkerThreads());
        long batchStartedNanos = System.nanoTime();
        List<CompletableFuture<TimedRange>> futures = new ArrayList<>(ranges.size());
        for (SonarTraceExecutor.Range range : ranges) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> {
                        long startedNanos = System.nanoTime();
                        List<SonarTraceExecutor.RayResult> results =
                                traceRange(rays, snapshot, sableSnapshot, range);
                        return new TimedRange(results, System.nanoTime() - startedNanos,
                                range.endExclusive() - range.startInclusive());
                    }, SonarTraceExecutor.get()));
        }
        CompletableFuture<?>[] futureArray = futures.toArray(CompletableFuture[]::new);
        CompletableFuture<Void> all = CompletableFuture.allOf(futureArray);
        return all.thenApply(ignored -> {
            List<SonarTraceExecutor.RayResult> results = new ArrayList<>(rays.size());
            long workerNanos = 0;
            int tracedRays = 0;
            for (CompletableFuture<TimedRange> future : futures) {
                TimedRange timed = future.join();
                results.addAll(timed.results);
                workerNanos += timed.workerNanos;
                tracedRays += timed.rays;
            }
            SonarTraceProfiler.record(server, sonarId, workerNanos,
                    System.nanoTime() - batchStartedNanos, futures.size(), tracedRays);
            return new SonarTraceExecutor.BatchResult(epoch, batchId, List.copyOf(results));
        });
    }

    private static List<SonarTraceExecutor.RayResult> traceRange(
            List<TraceRay> rays,
            SonarChunkReader.SonarWorldSnapshot snapshot,
            SableSonarCompat.Snapshot sableSnapshot,
            SonarTraceExecutor.Range range
    ) {
        List<SonarTraceExecutor.RayResult> results = new ArrayList<>(range.endExclusive() - range.startInclusive());
        WorldRayVisitor worldVisitor = new WorldRayVisitor(snapshot.cursor());
        for (int i = range.startInclusive(); i < range.endExclusive(); i++) {
            TraceRay ray = rays.get(i);
            MarchResult result = traceRay(ray, worldVisitor, sableSnapshot);
            if (result.waiting) continue;
            results.add(new SonarTraceExecutor.RayResult(ray.rayIndex, result.distance, result.hit,
                    result.airBoundary, result.incidence, result.state));
        }
        return results;
    }

    private record TimedRange(List<SonarTraceExecutor.RayResult> results,
                              long workerNanos, int rays) {}

    private static MarchResult traceRay(TraceRay ray, WorldRayVisitor worldVisitor,
                                        SableSonarCompat.Snapshot sableSnapshot) {
        MarchResult worldResult = worldVisitor.trace(ray);
        if (worldResult.waiting) return worldResult;
        if (sableSnapshot.isEmpty()) return worldResult;

        Vec3 origin = new Vec3(ray.originX, ray.originY, ray.originZ);
        Vec3 direction = new Vec3(ray.directionX, ray.directionY, ray.directionZ);
        double sableTraceEnd = worldResult.hit ? worldResult.distance : ray.endDistance;
        Optional<SableSonarCompat.Hit> sableHit =
                sableSnapshot.trace(origin, direction, ray.startDistance, sableTraceEnd);
        if (sableHit.isPresent() && SonarTraceSupport.firstDistanceBeatsSecond(
                true, sableHit.get().distance(), worldResult.hit, worldResult.distance)) {
            return MarchResult.hit(sableHit.get().distance(), sableHit.get().incidence(),
                    false, sableHit.get().state());
        }
        return worldResult;
    }

    private static final class WorldRayVisitor implements SonarSectionSkippingDda.Visitor {
        private final SonarChunkReader.SonarWorldSnapshot.Cursor cursor;
        private MarchResult result;
        private double start;

        private WorldRayVisitor(SonarChunkReader.SonarWorldSnapshot.Cursor cursor) {
            this.cursor = cursor;
        }

        private MarchResult trace(TraceRay ray) {
            result = null;
            start = ray.startDistance;
            SonarSectionSkippingDda.trace(ray.originX, ray.originY, ray.originZ,
                    ray.directionX, ray.directionY, ray.directionZ, start, ray.endDistance, this);
            return result == null ? MarchResult.clear(ray.endDistance) : result;
        }

        @Override
        public boolean skipSection(int x, int y, int z) {
            return cursor.isWaterSection(x, y, z);
        }

        @Override
        public boolean visit(int x, int y, int z, double distance, double incidence) {
            SonarChunkReader.Cell cell = cursor.probe(x, y, z);
            if (cell.type() == SonarChunkReader.CellType.PENDING
                    || cell.type() == SonarChunkReader.CellType.UNKNOWN) {
                result = MarchResult.waiting(start);
                return false;
            }
            if (cell.type() == SonarChunkReader.CellType.OBSTACLE
                    || cell.type() == SonarChunkReader.CellType.AIR_BOUNDARY) {
                if (SonarTraceSupport.isOwnEmitterBlock(cell.state() != null
                        && cell.state().getBlock() instanceof SonarBlock, distance)) return true;
                result = MarchResult.hit(distance, incidence,
                        cell.type() == SonarChunkReader.CellType.AIR_BOUNDARY, cell.state());
                return false;
            }
            return true;
        }
    }

    private static float materialReflectivity(BlockState state) {
        if (state == null) return 1;
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return 1;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) return 0.45f;
        if (state.is(BlockTags.WOOL)) return 0.25f;
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return 0.6f;
        return 0.75f;
    }

    private static int detectedRange(SonarFrame frame, int range) {
        int max = 0;
        for (SonarReturn sonarReturn : frame.returns()) {
            int distance = Math.max(sonarReturn.rangeBin() + 1,
                    (int) Math.ceil(sonarReturn.normalizedDistance() * range));
            max = Math.max(max, distance);
        }
        return max == 0 ? 0 : clamp(max, 1, range);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

        private record Descriptor(BlockPos pos, Vec3 origin, Vec3 displayOrigin,
                               Vec3 forward, Vec3 right, Vec3 up,
                               Vec3 displayForward, Vec3 displayRight, Vec3 displayUp,
                               SonarType type, int range, int sector, int verticalSector,
                               int tiltAngle, float mechanicalAngle, float mechanicalAngularSpeed,
                              long mechanicalAngleTick, boolean running,
                              int horizontalBeams, int verticalBeams,
                              int additionalRays, boolean refineOnlyUndetectedNeighbors,
                              int hitRefinementBacktrackBlocks, boolean autoHeight) {
        boolean sameScanSettings(Descriptor other) {
            return pos.equals(other.pos) && type == other.type && range == other.range
                    && sector == other.sector && verticalSector == other.verticalSector
                    && tiltAngle == other.tiltAngle
                    && horizontalBeams == other.horizontalBeams
                    && verticalBeams == other.verticalBeams
                    && additionalRays == other.additionalRays
                    && refineOnlyUndetectedNeighbors == other.refineOnlyUndetectedNeighbors
                    && hitRefinementBacktrackBlocks == other.hitRefinementBacktrackBlocks
                    && autoHeight == other.autoHeight;
        }

        SonarAdaptiveTracePlan.Settings traceSettings() {
            return SonarAdaptiveTracePlan.settings(range, sector, verticalSector,
                    horizontalBeams, verticalBeams,
                    additionalRays, hitRefinementBacktrackBlocks);
        }
    }

    private record OfflineSettings(int range, int sector, int verticalSector, int tiltAngle,
                                    float mechanicalAngle, boolean autoHeight) {}

    private record TraceRay(int rayIndex, double originX, double originY, double originZ,
                            double directionX, double directionY, double directionZ,
                            double startDistance, double endDistance) {}

    private record SideScanRefinementCandidate(
            RayState ray, SonarTraceExecutor.RayResult result, SideScanHitFilter.Cell cell) {}

    private static final class RayState {
        private final SonarAdaptiveTracePlan.Leaf leaf;
        private double distance;
        private boolean finished;

        private RayState(SonarAdaptiveTracePlan.Leaf leaf, double distance) {
            this.leaf = leaf;
            this.distance = distance;
        }

        private Vec3 direction(Descriptor descriptor) {
            SonarAdaptiveTracePlan.Settings settings = descriptor.traceSettings();
            double bearing = SonarAdaptiveTracePlan.bearing(leaf, settings);
            double pitch = SonarAdaptiveTracePlan.pitch(leaf, settings);
            if (descriptor.type == SonarType.MECHANICAL_IMAGING_C) {
                SonarOrientation displayOrientation = new SonarOrientation(
                        descriptor.displayForward, descriptor.displayRight, descriptor.displayUp);
                float absoluteBearing = MechanicalScanPlan.absoluteBearing(
                        leaf, descriptor.mechanicalAngle, settings);
                SonarOrientation sampleOrientation = MechanicalScanPlan.sampleOrientation(
                        displayOrientation, absoluteBearing, descriptor.tiltAngle);
                return sampleOrientation.direction(0, pitch);
            }
            SonarOrientation orientation = new SonarOrientation(descriptor.forward,
                    descriptor.right, descriptor.up);
            if (descriptor.type == SonarType.SIDE_SCAN_D) {
                return SideScanGeometry.rayDirection(
                        orientation, settings, leaf, descriptor.tiltAngle);
            }
            return orientation.direction(bearing, pitch);
        }

        private Vec3 origin(Descriptor descriptor) {
            if (descriptor.type == SonarType.MECHANICAL_IMAGING_C) {
                SonarAdaptiveTracePlan.Settings settings = descriptor.traceSettings();
                float absoluteBearing = MechanicalScanPlan.absoluteBearing(
                        leaf, descriptor.mechanicalAngle, settings);
                SonarOrientation displayOrientation = new SonarOrientation(
                        descriptor.displayForward, descriptor.displayRight, descriptor.displayUp);
                return MechanicalScanPlan.emitterOrigin(
                        descriptor.displayOrigin, displayOrientation, absoluteBearing);
            }
            if (descriptor.type != SonarType.SIDE_SCAN_D) return descriptor.origin;
            return descriptor.origin.add(descriptor.forward.scale(
                    SideScanGeometry.emitterForwardOffset(leaf)));
        }
    }

    private enum EmitterState {
        WATER,
        PENDING,
        DRY
    }

    record EchoKey(int beam, int vertical, int rangeBin,
                   int bearingMilliDegrees, int elevationMilliDegrees) {
        EchoKey(int beam, int rangeBin, int bearingMilliDegrees) {
            this(beam, 0, rangeBin, bearingMilliDegrees, 0);
        }
    }

    private static final class EchoAccumulator {
        private float sum;
        private float distanceSum;
        private float bearingSum;
        private float elevationSum;
        private int count;
        private float angularResolution = Float.MAX_VALUE;
        private float verticalAngularResolution = Float.MAX_VALUE;

        void add(float intensity, float normalizedDistance, float bearing,
                 float elevation, float angularResolution,
                 float verticalAngularResolution) {
            sum += intensity;
            distanceSum += normalizedDistance;
            bearingSum += bearing;
            elevationSum += elevation;
            this.angularResolution = Math.min(this.angularResolution, angularResolution);
            this.verticalAngularResolution = Math.min(
                    this.verticalAngularResolution, verticalAngularResolution);
            count++;
        }

        float average() {
            return count == 0 ? 0 : Math.min(1, sum / count);
        }

        float normalizedDistance() {
            return count == 0 ? 0 : distanceSum / count;
        }

        float bearing() {
            return count == 0 ? 0 : bearingSum / count;
        }

        float elevation() {
            return count == 0 ? 0 : elevationSum / count;
        }

        float angularResolution() {
            return angularResolution == Float.MAX_VALUE ? 0 : angularResolution;
        }

        float verticalAngularResolution() {
            return verticalAngularResolution == Float.MAX_VALUE ? 0 : verticalAngularResolution;
        }
    }

    private record MarchResult(boolean hit, boolean waiting, boolean airBoundary,
                               double distance, double incidence, BlockState state) {
        static MarchResult hit(double distance, double incidence, boolean airBoundary, BlockState state) {
            return new MarchResult(true, false, airBoundary, distance, incidence, state);
        }

        static MarchResult waiting(double distance) {
            return new MarchResult(false, true, false, distance, 0, null);
        }

        static MarchResult clear(double distance) {
            return new MarchResult(false, false, false, distance, 0, null);
        }
    }
}
