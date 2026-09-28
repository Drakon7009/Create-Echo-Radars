package org.rassvet.create_echo_radars.content.sonar;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SableSonarCompat {
    private static final String SABLE_MOD_ID = "sable";
    private static final double EPSILON = 1.0e-5;

    private SableSonarCompat() {}

    public static Snapshot capture(Level level, Vec3 origin, SonarOrientation orientation,
                                   int range, int sector, Collection<RaySegment> rays) {
        return capture(level, origin, orientation, range, sector, 20, rays);
    }

    public static Snapshot capture(Level level, Vec3 origin, SonarOrientation orientation,
                                   int range, int horizontalSector, int verticalSector,
                                   Collection<RaySegment> rays) {
        return capture(level, origin, origin, orientation, range, horizontalSector,
                verticalSector, rays);
    }

    public static Snapshot capture(Level level, Vec3 origin, Vec3 ownerSample,
                                   SonarOrientation orientation, int range,
                                   int horizontalSector, int verticalSector,
                                   Collection<RaySegment> rays) {
        if (rays.isEmpty() || !ModList.get().isLoaded(SABLE_MOD_ID)) return Snapshot.EMPTY;
        try {
            return LoadedSable.captureLoadedSubLevels(level, origin, ownerSample, orientation, range,
                    horizontalSector, verticalSector, rays);
        } catch (LinkageError | RuntimeException error) {
            return Snapshot.EMPTY;
        }
    }

    /**
     * Prevents Sable's projected block raycast from treating the tracked
     * construction itself as a wall. This is only applied to visibility rays
     * for Sable tracks; ordinary entity occlusion still checks every sublevel.
     */
    public static void ignoreTrackedSubLevel(ClipContext context, String trackId) {
        if (!ModList.get().isLoaded(SABLE_MOD_ID)) return;
        try {
            LoadedSable.ignoreTrackedSubLevel(context, trackId);
        } catch (LinkageError | RuntimeException ignored) {
            // Sable is optional. Fall back to the normal raycast if its API is unavailable.
        }
    }

    public static boolean hasLoadedSubLevels(Level level) {
        if (!ModList.get().isLoaded(SABLE_MOD_ID)) return false;
        try {
            return LoadedSable.hasLoadedSubLevels(level);
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static Snapshot emptySnapshot() {
        return Snapshot.EMPTY;
    }

    public static List<VisibilityTarget> visibilityTargets(Level level, Vec3 sourcePosition) {
        if (!ModList.get().isLoaded(SABLE_MOD_ID)) return List.of();
        try {
            return LoadedSable.visibilityTargets(level, sourcePosition);
        } catch (LinkageError | RuntimeException ignored) {
            return List.of();
        }
    }

    public record RaySegment(double originX, double originY, double originZ,
                             double directionX, double directionY, double directionZ,
                             double startDistance, double endDistance) {
        public RaySegment(Vec3 origin, Vec3 direction, double startDistance, double endDistance) {
            this(origin.x, origin.y, origin.z, direction.x, direction.y, direction.z,
                    startDistance, endDistance);
        }

        public Vec3 origin() {
            return new Vec3(originX, originY, originZ);
        }

        public Vec3 direction() {
            return new Vec3(directionX, directionY, directionZ);
        }
    }

    public record VisibilityTarget(String id, Vec3 position) {}

    public static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(List.of());
        private final List<SubLevelTrace> subLevels;

        private Snapshot(List<SubLevelTrace> subLevels) {
            this.subLevels = subLevels;
        }

        public boolean isEmpty() {
            return subLevels.isEmpty();
        }

        public Optional<Hit> trace(Vec3 origin, Vec3 direction, double startDistance, double endDistance) {
            if (subLevels.isEmpty()) return Optional.empty();
            Hit best = null;
            for (SubLevelTrace subLevel : subLevels) {
                double limit = best == null ? endDistance : Math.min(endDistance, best.distance());
                if (!SonarTraceSupport.segmentIntersectsBox(subLevel.globalBounds(),
                        origin.x, origin.y, origin.z, direction.x, direction.y, direction.z,
                        startDistance, limit)) continue;
                Optional<Hit> hit = subLevel.trace(origin, direction, startDistance, limit);
                if (hit.isPresent() && (best == null || hit.get().distance() < best.distance())) {
                    best = hit.get();
                }
            }
            return Optional.ofNullable(best);
        }
    }

    public record Hit(double distance, double incidence, BlockState state) {}

    private interface SubLevelTrace {
        AABB globalBounds();

        Optional<Hit> trace(Vec3 origin, Vec3 direction, double startDistance, double endDistance);
    }

    private static final class LoadedSable {
        private LoadedSable() {}

        private static boolean hasLoadedSubLevels(Level level) {
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            return container != null && container.getLoadedCount() > 0;
        }

        private static void ignoreTrackedSubLevel(ClipContext context, String trackId) {
            if (!(context instanceof dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension extension)) {
                return;
            }
            extension.sable$setSubLevelIgnoring(subLevel ->
                    subLevel.getUniqueId().toString().equals(trackId));
        }

        private static List<VisibilityTarget> visibilityTargets(Level level, Vec3 sourcePosition) {
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null || container.getLoadedCount() == 0) return List.of();

            dev.ryanhcode.sable.companion.SubLevelAccess owner =
                    dev.ryanhcode.sable.companion.SableCompanion.INSTANCE
                            .getContaining(level, sourcePosition);
            java.util.UUID ownerId = owner == null ? null : owner.getUniqueId();
            List<VisibilityTarget> targets = new ArrayList<>();
            for (dev.ryanhcode.sable.sublevel.SubLevel subLevel :
                    List.copyOf(container.getAllSubLevels())) {
                if (subLevel.isRemoved() || subLevel.getUniqueId().equals(ownerId)) continue;
                AABB bounds = subLevel.boundingBox().toMojang();
                targets.add(new VisibilityTarget(
                        subLevel.getUniqueId().toString(), bounds.getCenter()));
            }
            return List.copyOf(targets);
        }

        private static Snapshot captureLoadedSubLevels(Level level, Vec3 origin, Vec3 ownerSample,
                                                       SonarOrientation orientation,
                                                       int range, int horizontalSector, int verticalSector,
                                                       Collection<RaySegment> rays) {
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null || container.getLoadedCount() == 0) return Snapshot.EMPTY;
            dev.ryanhcode.sable.companion.SubLevelAccess owner =
                    dev.ryanhcode.sable.companion.SableCompanion.INSTANCE
                            .getContaining(level, ownerSample);
            java.util.UUID ownerId = owner == null ? null : owner.getUniqueId();

            AABB batchBounds = null;
            for (RaySegment ray : rays) {
                batchBounds = SonarTraceSupport.expand(batchBounds,
                        SonarTraceSupport.segmentBounds(
                                ray.originX(), ray.originY(), ray.originZ(),
                                ray.directionX(), ray.directionY(), ray.directionZ(),
                                ray.startDistance(), ray.endDistance()));
            }
            if (batchBounds == null) return Snapshot.EMPTY;
            batchBounds = batchBounds.inflate(1);

            List<SubLevelTrace> snapshots = new ArrayList<>();
            for (dev.ryanhcode.sable.sublevel.SubLevel subLevel : List.copyOf(container.getAllSubLevels())) {
                if (subLevel.isRemoved() || subLevel.getUniqueId().equals(ownerId)) continue;
                AABB globalBounds = subLevel.boundingBox().toMojang();
                if (!globalBounds.intersects(batchBounds)) continue;
                if (!SonarTraceSupport.boxMayIntersectCone(globalBounds, origin, orientation,
                        horizontalSector, verticalSector, range)) continue;
                // A diving vessel can move between the sonar pose and Sable's
                // containment lookup. Identify its sublevel by the sonar block
                // itself before allowing its hull into the trace snapshot.
                if (containsSourceSonar(subLevel, ownerSample)) continue;

                SubLevelSnapshot snapshot = copySubLevel(subLevel, rays);
                if (!snapshot.sections().isEmpty()) snapshots.add(snapshot);
            }
            return snapshots.isEmpty() ? Snapshot.EMPTY : new Snapshot(List.copyOf(snapshots));
        }

        private static boolean containsSourceSonar(dev.ryanhcode.sable.sublevel.SubLevel subLevel,
                                                   Vec3 ownerSample) {
            dev.ryanhcode.sable.companion.math.Pose3d pose =
                    new dev.ryanhcode.sable.companion.math.Pose3d(subLevel.logicalPose());
            BlockPos local = BlockPos.containing(pose.transformPositionInverse(ownerSample));
            dev.ryanhcode.sable.sublevel.plot.LevelPlot plot = subLevel.getPlot();
            dev.ryanhcode.sable.companion.math.BoundingBox3ic bounds = plot.getBoundingBox();
            if (local.getX() < bounds.minX() - 1 || local.getX() > bounds.maxX() + 1
                    || local.getY() < bounds.minY() - 1 || local.getY() > bounds.maxY() + 1
                    || local.getZ() < bounds.minZ() - 1 || local.getZ() > bounds.maxZ() + 1) {
                return false;
            }
            for (int x = local.getX() - 1; x <= local.getX() + 1; x++) {
                for (int y = local.getY() - 1; y <= local.getY() + 1; y++) {
                    for (int z = local.getZ() - 1; z <= local.getZ() + 1; z++) {
                        BlockPos position = new BlockPos(x, y, z);
                        net.minecraft.world.level.ChunkPos chunkPos =
                                new net.minecraft.world.level.ChunkPos(position);
                        net.minecraft.world.level.chunk.LevelChunk chunk =
                                plot.getChunk(plot.toLocal(chunkPos));
                        if (chunk != null && chunk.getBlockState(position).getBlock() instanceof SonarBlock) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static SubLevelSnapshot copySubLevel(dev.ryanhcode.sable.sublevel.SubLevel subLevel,
                                                     Collection<RaySegment> rays) {
            dev.ryanhcode.sable.companion.math.Pose3d pose =
                    new dev.ryanhcode.sable.companion.math.Pose3d(subLevel.logicalPose());
            dev.ryanhcode.sable.sublevel.plot.LevelPlot plot = subLevel.getPlot();
            dev.ryanhcode.sable.companion.math.BoundingBox3ic plotBounds = plot.getBoundingBox();
            if (plotBounds.volume() <= 0) {
                return new SubLevelSnapshot(pose, subLevel.boundingBox().toMojang(), Map.of());
            }

            LongSet sections = collectLocalSections(pose, subLevel.boundingBox().toMojang(), rays);
            if (sections.isEmpty()) {
                return new SubLevelSnapshot(pose, subLevel.boundingBox().toMojang(), Map.of());
            }

            Map<Long, SectionSnapshot> sectionsByPosition = new HashMap<>();
            for (LongIterator iterator = sections.iterator(); iterator.hasNext();) {
                long section = iterator.nextLong();
                int sectionX = SectionPos.x(section);
                int sectionY = SectionPos.y(section);
                int sectionZ = SectionPos.z(section);
                net.minecraft.world.level.ChunkPos chunkPos =
                        new net.minecraft.world.level.ChunkPos(sectionX, sectionZ);
                net.minecraft.world.level.chunk.LevelChunk chunk = plot.getChunk(plot.toLocal(chunkPos));
                if (chunk == null) continue;
                int sectionMinX = Math.max(plotBounds.minX(), sectionX << 4);
                int sectionMaxX = Math.min(plotBounds.maxX(), (sectionX << 4) + 15);
                int sectionMinY = Math.max(plotBounds.minY(), sectionY << 4);
                int sectionMaxY = Math.min(plotBounds.maxY(), (sectionY << 4) + 15);
                int sectionMinZ = Math.max(plotBounds.minZ(), sectionZ << 4);
                int sectionMaxZ = Math.min(plotBounds.maxZ(), (sectionZ << 4) + 15);
                if (sectionMinX > sectionMaxX || sectionMinY > sectionMaxY || sectionMinZ > sectionMaxZ) continue;
                int sectionIndex = chunk.getSectionIndexFromSectionY(sectionY);
                if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) continue;
                var states = chunk.getSections()[sectionIndex].getStates();
                if (!states.maybeHas(state -> !state.is(Blocks.AIR) && !state.is(Blocks.CAVE_AIR)
                        && !state.is(Blocks.VOID_AIR) && !state.is(Blocks.WATER))) continue;
                sectionsByPosition.put(section, new SectionSnapshot(states.copy(),
                        sectionMinX, sectionMinY, sectionMinZ, sectionMaxX, sectionMaxY, sectionMaxZ));
            }
            return new SubLevelSnapshot(pose, subLevel.boundingBox().toMojang(),
                    Map.copyOf(sectionsByPosition));
        }

        private static LongSet collectLocalSections(
                dev.ryanhcode.sable.companion.math.Pose3d pose, AABB bounds, Collection<RaySegment> rays) {
            LongSet sections = new LongOpenHashSet();
            for (RaySegment ray : rays) {
                if (!SonarTraceSupport.segmentIntersectsBox(bounds,
                        ray.originX(), ray.originY(), ray.originZ(),
                        ray.directionX(), ray.directionY(), ray.directionZ(),
                        ray.startDistance(), ray.endDistance())) continue;
                Vec3 origin = ray.origin();
                Vec3 direction = ray.direction();
                Vec3 worldStart = origin.add(direction.scale(ray.startDistance()));
                Vec3 worldEnd = origin.add(direction.scale(ray.endDistance()));
                Vec3 localStart = pose.transformPositionInverse(worldStart);
                Vec3 localEnd = pose.transformPositionInverse(worldEnd);
                Vec3 localDelta = localEnd.subtract(localStart);
                double localLength = localDelta.length();
                if (localLength <= EPSILON) continue;
                Vec3 localDirection = localDelta.scale(1 / localLength);
                SonarSectionDda.traceSections(localStart.x, localStart.y, localStart.z,
                        localDirection.x, localDirection.y, localDirection.z, 0, localLength,
                        (x, y, z) -> sections.add(SectionPos.asLong(x, y, z)));
            }
            return sections;
        }
    }

    private record SubLevelSnapshot(dev.ryanhcode.sable.companion.math.Pose3d pose,
                                    AABB globalBounds,
                                    Map<Long, SectionSnapshot> sections) implements SubLevelTrace {
        public Optional<Hit> trace(Vec3 origin, Vec3 direction, double startDistance, double endDistance) {
            Vec3 worldStart = origin.add(direction.scale(startDistance));
            Vec3 worldEnd = origin.add(direction.scale(endDistance));
            Vec3 localStart = pose.transformPositionInverse(worldStart);
            Vec3 localEnd = pose.transformPositionInverse(worldEnd);
            Vec3 localDelta = localEnd.subtract(localStart);
            double localLength = localDelta.length();
            if (localLength <= EPSILON) return Optional.empty();
            Vec3 localDirection = localDelta.scale(1 / localLength);

            SubLevelVisitor visitor = new SubLevelVisitor(sections, pose, localStart, localDirection,
                    origin, direction, startDistance, endDistance);
            SonarSectionSkippingDda.trace(localStart.x, localStart.y, localStart.z,
                    localDirection.x, localDirection.y, localDirection.z, 0, localLength,
                    visitor);
            return Optional.ofNullable(visitor.hit);
        }
    }

    private record SectionSnapshot(PalettedContainerRO<BlockState> states,
                                    int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private static final class SubLevelVisitor implements SonarSectionSkippingDda.Visitor {
        private final Map<Long, SectionSnapshot> sections;
        private final dev.ryanhcode.sable.companion.math.Pose3d pose;
        private final Vec3 localStart, localDirection, origin, direction;
        private final double from, to;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private SectionSnapshot section;
        private Hit hit;

        private SubLevelVisitor(Map<Long, SectionSnapshot> sections,
                                dev.ryanhcode.sable.companion.math.Pose3d pose,
                                Vec3 localStart, Vec3 localDirection, Vec3 origin, Vec3 direction,
                                double from, double to) {
            this.sections = sections; this.pose = pose;
            this.localStart = localStart; this.localDirection = localDirection;
            this.origin = origin; this.direction = direction; this.from = from; this.to = to;
        }

        public boolean skipSection(int x, int y, int z) {
            section = sections.get(SectionPos.asLong(x,y,z));
            return section == null;
        }

        public boolean visit(int x, int y, int z, double distance, double incidence) {
            if (section == null || !section.contains(x,y,z)) return true;
            BlockState block = section.states().get(x & 15, y & 15, z & 15);
            pos.set(x,y,z);
            if (SonarChunkReader.classify(block,pos).type() != SonarChunkReader.CellType.OBSTACLE) return true;
            Vec3 worldHit = pose.transformPosition(localStart.add(localDirection.scale(distance)));
            double worldDistance = worldHit.subtract(origin).dot(direction);
            if (worldDistance < from - EPSILON || worldDistance > to + EPSILON) return true;
            if (SonarTraceSupport.isOwnEmitterBlock(
                    block.getBlock() instanceof SonarBlock, worldDistance)) return true;
            hit = new Hit(Math.max(from,worldDistance),incidence,block);
            return false;
        }
    }
}
