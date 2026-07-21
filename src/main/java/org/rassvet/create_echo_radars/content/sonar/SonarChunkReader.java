package org.rassvet.create_echo_radars.content.sonar;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.tags.FluidTags;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.config.ServerConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class SonarChunkReader {
    private static final Codec<PalettedContainerRO<BlockState>> BLOCK_STATES =
            PalettedContainer.codecRO(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
                    PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());

    private final ServerLevel level;
    private final Map<Long, CompletableFuture<ChunkSnapshot>> snapshots = new ConcurrentHashMap<>();
    private final Queue<ChunkPos> queued = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeReads = new AtomicInteger();

    public SonarChunkReader(ServerLevel level) {
        this.level = level;
    }

    public Cell probe(BlockPos pos, LongSet usedChunks) {
        ChunkPos chunkPos = new ChunkPos(pos);
        LevelChunk loaded = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (loaded != null) return classify(loaded.getBlockState(pos), pos);

        long key = chunkPos.toLong();
        usedChunks.add(key);
        CompletableFuture<ChunkSnapshot> future = snapshots.get(key);
        if (future == null) {
            CompletableFuture<ChunkSnapshot> created = new CompletableFuture<>();
            if (snapshots.putIfAbsent(key, created) == null) queued.add(chunkPos);
            future = snapshots.get(key);
        }
        if (!future.isDone()) return Cell.pending();
        ChunkSnapshot snapshot = future.getNow(ChunkSnapshot.UNAVAILABLE);
        BlockState state = snapshot.getBlockState(pos);
        return state == null ? Cell.unknown() : classify(state, pos);
    }

    public Optional<SonarWorldSnapshot> prepareSnapshot(LongSet sections,
                                                       LongSet usedChunks) {
        Map<Long, Set<Integer>> sectionsByChunk = new HashMap<>();
        for (LongIterator iterator = sections.iterator(); iterator.hasNext();) {
            long section = iterator.nextLong();
            long chunkKey = ChunkPos.asLong(SectionPos.x(section), SectionPos.z(section));
            usedChunks.add(chunkKey);
            sectionsByChunk.computeIfAbsent(chunkKey, ignored -> new HashSet<>()).add(SectionPos.y(section));
        }

        Map<Long, ChunkSnapshot> ready = new HashMap<>();
        for (Map.Entry<Long, Set<Integer>> entry : sectionsByChunk.entrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            LevelChunk loaded = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (loaded != null) {
                ready.put(entry.getKey(), copyLoadedChunk(loaded, entry.getValue()));
                continue;
            }

            CompletableFuture<ChunkSnapshot> future = snapshots.get(entry.getKey());
            if (future == null) {
                CompletableFuture<ChunkSnapshot> created = new CompletableFuture<>();
                if (snapshots.putIfAbsent(entry.getKey(), created) == null) queued.add(chunkPos);
                future = snapshots.get(entry.getKey());
            }
            if (!future.isDone()) return Optional.empty();

            ChunkSnapshot snapshot = future.getNow(ChunkSnapshot.UNAVAILABLE);
            if (!snapshot.available()) return Optional.empty();
            ready.put(entry.getKey(), snapshot);
        }
        return Optional.of(new SonarWorldSnapshot(Map.copyOf(ready)));
    }

    public void tick() {
        int limit = ServerConfig.maxConcurrentChunkReads();
        while (activeReads.get() < limit) {
            ChunkPos pos = queued.poll();
            if (pos == null) break;
            CompletableFuture<ChunkSnapshot> target = snapshots.get(pos.toLong());
            if (target == null || target.isDone()) continue;
            activeReads.incrementAndGet();
            level.getChunkSource().chunkMap.read(pos)
                    .thenApplyAsync(this::decode, Util.backgroundExecutor())
                    .exceptionally(error -> {
                        CreateEchoRadars.LOGGER.warn("Could not read sonar chunk {}", pos, error);
                        return ChunkSnapshot.UNAVAILABLE;
                    })
                    .whenComplete((snapshot, error) -> {
                        target.complete(error == null ? snapshot : ChunkSnapshot.UNAVAILABLE);
                        activeReads.decrementAndGet();
                    });
        }
    }

    public void discard(LongSet chunks) {
        for (LongIterator iterator = chunks.iterator(); iterator.hasNext();)
            snapshots.remove(iterator.nextLong());
    }

    private ChunkSnapshot decode(Optional<CompoundTag> optional) {
        if (optional.isEmpty()) return ChunkSnapshot.UNAVAILABLE;
        try {
            Map<Integer, SectionSnapshot> sections = new ConcurrentHashMap<>();
            ListTag list = optional.get().getList("sections", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag section = list.getCompound(i);
                if (!section.contains("block_states", Tag.TAG_COMPOUND)) continue;
                PalettedContainerRO<BlockState> states = BLOCK_STATES
                        .parse(NbtOps.INSTANCE, section.getCompound("block_states"))
                        .resultOrPartial(message -> CreateEchoRadars.LOGGER.debug(
                                "Could not decode sonar block section: {}", message))
                        .orElse(null);
                if (states != null) sections.put((int) section.getByte("Y"), SectionSnapshot.decoded(states));
            }
            return new ChunkSnapshot(sections, true);
        } catch (RuntimeException error) {
            CreateEchoRadars.LOGGER.debug("Unsupported chunk data during sonar scan", error);
            return ChunkSnapshot.UNAVAILABLE;
        }
    }

    private ChunkSnapshot copyLoadedChunk(LevelChunk chunk, Set<Integer> requestedSections) {
        Map<Integer, SectionSnapshot> copied = new HashMap<>();
        for (int sectionY : requestedSections) {
            int index = chunk.getSectionIndexFromSectionY(sectionY);
            if (index < 0 || index >= chunk.getSections().length) {
                copied.put(sectionY, SectionSnapshot.air());
                continue;
            }
            copied.put(sectionY, SectionSnapshot.copy(chunk.getSections()[index].getStates()));
        }
        return new ChunkSnapshot(Map.copyOf(copied), true);
    }

    static Cell classify(BlockState state, BlockPos pos) {
        VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos);
        if (!collision.isEmpty()) return new Cell(CellType.OBSTACLE, state);
        if (state.getFluidState().is(FluidTags.WATER)) return new Cell(CellType.WATER, state);
        return new Cell(CellType.AIR_BOUNDARY, state);
    }

    public enum CellType {
        WATER, OBSTACLE, AIR_BOUNDARY, PENDING, UNKNOWN
    }

    public record Cell(CellType type, BlockState state) {
        static Cell pending() {
            return new Cell(CellType.PENDING, null);
        }

        static Cell unknown() {
            return new Cell(CellType.UNKNOWN, null);
        }
    }

    public static final class SonarWorldSnapshot {
        private final Map<Long, ChunkSnapshot> chunks;

        private SonarWorldSnapshot(Map<Long, ChunkSnapshot> chunks) {
            this.chunks = chunks;
        }

        Cell probe(BlockPos pos) {
            ChunkSnapshot chunk = chunks.get(ChunkPos.asLong(Math.floorDiv(pos.getX(), 16),
                    Math.floorDiv(pos.getZ(), 16)));
            if (chunk == null) return Cell.unknown();
            BlockState state = chunk.getBlockState(pos);
            return state == null ? Cell.unknown() : classify(state, pos);
        }
    }

    private record ChunkSnapshot(Map<Integer, SectionSnapshot> sections, boolean available) {
        private static final ChunkSnapshot UNAVAILABLE = new ChunkSnapshot(Map.of(), false);

        BlockState getBlockState(BlockPos pos) {
            if (!available) return null;
            SectionSnapshot states = sections.get(Math.floorDiv(pos.getY(), 16));
            if (states == null) return Blocks.AIR.defaultBlockState();
            return states.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }
    }

    private record SectionSnapshot(PalettedContainerRO<BlockState> states, boolean allAir) {
        private static final BlockState AIR = Blocks.AIR.defaultBlockState();
        private static final SectionSnapshot AIR_SECTION = new SectionSnapshot(null, true);

        static SectionSnapshot copy(PalettedContainer<BlockState> source) {
            return new SectionSnapshot(source.copy(), false);
        }

        static SectionSnapshot decoded(PalettedContainerRO<BlockState> source) {
            return new SectionSnapshot(source, false);
        }

        static SectionSnapshot air() {
            return AIR_SECTION;
        }

        BlockState get(int x, int y, int z) {
            return allAir ? AIR : states.get(x, y, z);
        }
    }
}
