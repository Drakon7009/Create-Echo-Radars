package org.rassvet.create_echo_radars.content.sonar;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
        if (rays.isEmpty() || !ModList.get().isLoaded(SABLE_MOD_ID)) return Snapshot.EMPTY;
        try {
            return LoadedSable.captureLoadedSubLevels(level, origin, orientation, range,
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

    public static List<VisibilityTarget> visibilityTargets(Level level, Vec3 sourcePosition) {
        if (!ModList.get().isLoaded(SABLE_MOD_ID)) return List.of();
        try {
            return LoadedSable.visibilityTargets(level, sourcePosition);
        } catch (LinkageError | RuntimeException ignored) {
            return List.of();
        }
    }

    public record RaySegment(Vec3 origin, Vec3 direction, double startDistance, double endDistance) {}

    public record VisibilityTarget(String id, Vec3 position) {}

    public static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(List.of());
        private final List<SubLevelTrace> subLevels;

        private Snapshot(List<SubLevelTrace> subLevels) {
            this.subLevels = subLevels;
        }

        public Optional<Hit> trace(Vec3 origin, Vec3 direction, double startDistance, double endDistance) {
            if (subLevels.isEmpty()) return Optional.empty();
            AABB rayBounds = SonarTraceSupport.segmentBounds(origin, direction, startDistance, endDistance).inflate(1.0e-3);
            Hit best = null;
            for (SubLevelTrace subLevel : subLevels) {
                if (!subLevel.globalBounds().intersects(rayBounds)) continue;
                Optional<Hit> hit = subLevel.trace(origin, direction, startDistance, endDistance);
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

    private record ObstacleCell(BlockState state) {}

    private static final class LoadedSable {
        private LoadedSable() {}

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

        private static Snapshot captureLoadedSubLevels(Level level, Vec3 origin, SonarOrientation orientation,
                                                       int range, int horizontalSector, int verticalSector,
                                                       Collection<RaySegment> rays) {
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null || container.getLoadedCount() == 0) return Snapshot.EMPTY;

            AABB batchBounds = null;
            for (RaySegment ray : rays) {
                batchBounds = SonarTraceSupport.expand(batchBounds,
                        SonarTraceSupport.segmentBounds(ray.origin(), ray.direction(),
                                ray.startDistance(), ray.endDistance()));
            }
            if (batchBounds == null) return Snapshot.EMPTY;
            batchBounds = batchBounds.inflate(1);

            List<SubLevelTrace> snapshots = new ArrayList<>();
            for (dev.ryanhcode.sable.sublevel.SubLevel subLevel : List.copyOf(container.getAllSubLevels())) {
                if (subLevel.isRemoved()) continue;
                AABB globalBounds = subLevel.boundingBox().toMojang();
                if (!globalBounds.intersects(batchBounds)) continue;
                if (!SonarTraceSupport.boxMayIntersectCone(globalBounds, origin, orientation,
                        horizontalSector, verticalSector, range)) continue;

                SubLevelSnapshot snapshot = copySubLevel(subLevel, rays);
                if (!snapshot.obstacles().isEmpty()) snapshots.add(snapshot);
            }
            return snapshots.isEmpty() ? Snapshot.EMPTY : new Snapshot(List.copyOf(snapshots));
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

            LongSet sections = collectLocalSections(pose, rays);
            if (sections.isEmpty()) {
                return new SubLevelSnapshot(pose, subLevel.boundingBox().toMojang(), Map.of());
            }

            Map<Long, ObstacleCell> obstacles = new HashMap<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
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

                int minY = Math.max(sectionMinY, chunk.getMinBuildHeight());
                int maxY = Math.min(sectionMaxY, chunk.getMaxBuildHeight() - 1);
                for (int y = minY; y <= maxY; y++) {
                    for (int z = sectionMinZ; z <= sectionMaxZ; z++) {
                        for (int x = sectionMinX; x <= sectionMaxX; x++) {
                            pos.set(x, y, z);
                            SonarChunkReader.Cell cell = SonarChunkReader.classify(chunk.getBlockState(pos), pos);
                            if (cell.type() == SonarChunkReader.CellType.OBSTACLE) {
                                obstacles.put(BlockPos.asLong(x, y, z), new ObstacleCell(cell.state()));
                            }
                        }
                    }
                }
            }
            return new SubLevelSnapshot(pose, subLevel.boundingBox().toMojang(), Map.copyOf(obstacles));
        }

        private static LongSet collectLocalSections(
                dev.ryanhcode.sable.companion.math.Pose3d pose, Collection<RaySegment> rays) {
            LongSet sections = new LongOpenHashSet();
            for (RaySegment ray : rays) {
                Vec3 worldStart = ray.origin().add(ray.direction().scale(ray.startDistance()));
                Vec3 worldEnd = ray.origin().add(ray.direction().scale(ray.endDistance()));
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
                                    Map<Long, ObstacleCell> obstacles) implements SubLevelTrace {
        public Optional<Hit> trace(Vec3 origin, Vec3 direction, double startDistance, double endDistance) {
            Vec3 worldStart = origin.add(direction.scale(startDistance));
            Vec3 worldEnd = origin.add(direction.scale(endDistance));
            Vec3 localStart = pose.transformPositionInverse(worldStart);
            Vec3 localEnd = pose.transformPositionInverse(worldEnd);
            Vec3 localDelta = localEnd.subtract(localStart);
            double localLength = localDelta.length();
            if (localLength <= EPSILON) return Optional.empty();
            Vec3 localDirection = localDelta.scale(1 / localLength);

            TraceState state = new TraceState();
            SonarVoxelDda.traceCells(localStart.x, localStart.y, localStart.z,
                    localDirection.x, localDirection.y, localDirection.z, 0, localLength,
                    (x, y, z, localDistance, incidence) -> {
                        ObstacleCell cell = obstacles.get(BlockPos.asLong(x, y, z));
                        if (cell == null) return true;
                        Vec3 localHit = localStart.add(localDirection.scale(localDistance));
                        Vec3 worldHit = pose.transformPosition(localHit);
                        double worldDistance = worldHit.subtract(origin).dot(direction);
                        if (worldDistance < startDistance - EPSILON || worldDistance > endDistance + EPSILON) {
                            return true;
                        }
                        state.hit = new Hit(Math.max(startDistance, worldDistance), incidence, cell.state());
                        return false;
                    });
            return Optional.ofNullable(state.hit);
        }
    }

    private static final class TraceState {
        private Hit hit;
    }
}
