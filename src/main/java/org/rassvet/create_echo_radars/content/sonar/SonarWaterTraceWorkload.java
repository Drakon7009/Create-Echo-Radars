package org.rassvet.create_echo_radars.content.sonar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import java.util.HashMap;
import java.util.Map;

/** Opt-in benchmark data: immutable vanilla water surrounded by stone, no world edits.
 * Exercises the same snapshot reads and section traversal as the production worker.
 */
public final class SonarWaterTraceWorkload {
    private final SonarChunkReader.SonarWorldSnapshot snapshot;

    public SonarWaterTraceWorkload() {
        var water = section(Blocks.WATER.defaultBlockState());
        var stone = section(Blocks.STONE.defaultBlockState());
        Map<Long, SonarChunkReader.ChunkSnapshot> chunks = new HashMap<>();
        for (int x=-5;x<=4;x++) for (int z=-5;z<=4;z++) {
            Map<Integer, SonarChunkReader.SectionSnapshot> sections = new HashMap<>();
            for (int y=-5;y<=4;y++) sections.put(y,
                    x>=-4&&x<4&&y>=-4&&y<4&&z>=-4&&z<4 ? water : stone);
            chunks.put(ChunkPos.asLong(x,z), new SonarChunkReader.ChunkSnapshot(Map.copyOf(sections),true));
        }
        snapshot = new SonarChunkReader.SonarWorldSnapshot(Map.copyOf(chunks));
    }

    private static SonarChunkReader.SectionSnapshot section(BlockState state) {
        return SonarChunkReader.SectionSnapshot.copy(new PalettedContainer<>(
                Block.BLOCK_STATE_REGISTRY,state,PalettedContainer.Strategy.SECTION_STATES));
    }

    public long run(double[] directions, boolean optimized) {
        var cursor = snapshot.cursor();
        var position = new BlockPos.MutableBlockPos();
        class Visitor implements SonarSectionSkippingDda.Visitor {
            long checksum;
            public boolean skipSection(int x,int y,int z) { return cursor.isWaterSection(x,y,z); }
            public boolean visit(int x,int y,int z,double distance,double incidence) {
                var cell = optimized ? cursor.probe(x,y,z) : snapshot.probe(x,y,z,position);
                if (cell.type()==SonarChunkReader.CellType.WATER) return true;
                checksum += (x*31L+y*17L+z) ^ Double.doubleToRawLongBits(distance)
                        ^ Double.doubleToRawLongBits(incidence) ^ cell.type().ordinal();
                return false;
            }
        }
        var visitor = new Visitor();
        for (int i=0;i<directions.length;i+=3) {
            if (optimized) SonarSectionSkippingDda.trace(.5,.5,.5,
                    directions[i],directions[i+1],directions[i+2],0,128,visitor);
            else SonarVoxelDda.traceCells(.5,.5,.5,
                    directions[i],directions[i+1],directions[i+2],0,128,visitor);
        }
        return visitor.checksum;
    }
}
