package org.rassvet.create_echo_radars.content.sonar;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SonarWorldSnapshotTest {
    @BeforeAll
    static void bootstrap() {
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Standalone registry bootstrap does not load datapack tags.
        net.minecraft.core.registries.BuiltInRegistries.FLUID.bindTags(Map.of(
                net.minecraft.tags.FluidTags.WATER, java.util.List.of(
                        net.minecraft.core.registries.BuiltInRegistries.FLUID.wrapAsHolder(net.minecraft.world.level.material.Fluids.WATER),
                        net.minecraft.core.registries.BuiltInRegistries.FLUID.wrapAsHolder(net.minecraft.world.level.material.Fluids.FLOWING_WATER))));
    }

    private static PalettedContainer<BlockState> palette(BlockState state) {
        return new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, state,
                PalettedContainer.Strategy.SECTION_STATES);
    }

    @Test
    void onlyPureWaterCanBeSkippedAndSnapshotIsImmutable() {
        var source = palette(Blocks.WATER.defaultBlockState());
        var water = SonarChunkReader.SectionSnapshot.copy(source);
        assertTrue(water.allWater());
        source.set(3, 4, 5, Blocks.STONE.defaultBlockState());
        assertTrue(water.allWater());
        assertEquals(Blocks.WATER.defaultBlockState(), water.get(3,4,5));
        assertFalse(SonarChunkReader.SectionSnapshot.copy(source).allWater());
        assertFalse(SonarChunkReader.SectionSnapshot.copy(palette(Blocks.AIR.defaultBlockState())).allWater());
        var wet = Blocks.OAK_SLAB.defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
        assertFalse(SonarChunkReader.SectionSnapshot.copy(palette(wet)).allWater());
    }

    @Test
    void cursorMatchesClassicProbeAcrossSectionsAndUnknownChunks() {
        var source = palette(Blocks.WATER.defaultBlockState());
        source.set(3, 4, 5, Blocks.STONE.defaultBlockState());
        var snapshot = new SonarChunkReader.SonarWorldSnapshot(Map.of(ChunkPos.asLong(-1,0),
                new SonarChunkReader.ChunkSnapshot(Map.of(0, SonarChunkReader.SectionSnapshot.copy(source)),true)));
        var cursor = snapshot.cursor();
        for (int x=-17;x<=1;x++) for (int y=-1;y<=17;y++) for (int z=0;z<17;z++) {
            assertEquals(snapshot.probe(new BlockPos(x,y,z)), cursor.probe(x,y,z));
        }
        assertFalse(cursor.isWaterSection(0,0,0)); // unknown chunk
        assertFalse(cursor.isWaterSection(-1,1,0)); // missing section means air
        assertFalse(cursor.isWaterSection(-1,0,0)); // one obstacle in water
    }

    @Test
    void mixedWaterObstaclesAirAndUnknownMatchClassicHits() {
        var random = new java.util.Random(923841L);
        var water = SonarChunkReader.SectionSnapshot.copy(palette(Blocks.WATER.defaultBlockState()));
        Map<Long, SonarChunkReader.ChunkSnapshot> chunks = new HashMap<>();
        for (int x=-4;x<=3;x++) for (int z=-4;z<=3;z++) {
            if (random.nextInt(12)==0) continue; // unknown chunk, never transparent
            Map<Integer, SonarChunkReader.SectionSnapshot> sections = new HashMap<>();
            for (int y=-4;y<=3;y++) {
                int kind=random.nextInt(6);
                if (kind==0) continue; // air section, must stop a world ray
                if (kind<4) { sections.put(y,water); continue; }
                var mixed=palette(Blocks.WATER.defaultBlockState());
                for(int i=0;i<80;i++) mixed.set(random.nextInt(16),random.nextInt(16),random.nextInt(16),
                        (i%3==0 ? Blocks.AIR : Blocks.STONE).defaultBlockState());
                sections.put(y,SonarChunkReader.SectionSnapshot.copy(mixed));
            }
            chunks.put(ChunkPos.asLong(x,z),new SonarChunkReader.ChunkSnapshot(Map.copyOf(sections),true));
        }
        // Start in known water; rays must reach the heterogeneous neighbouring sections.
        chunks.put(ChunkPos.asLong(0,0),new SonarChunkReader.ChunkSnapshot(Map.of(0,water,-1,water),true));
        var snapshot=new SonarChunkReader.SonarWorldSnapshot(Map.copyOf(chunks));
        double[] directions=directions(8192);
        assertArrayEquals(run(snapshot,directions,false),run(snapshot,directions,true));
    }

    @Test
    void commandWorkloadChecksumsMatch() {
        var workload=new SonarWaterTraceWorkload();
        for(int count : new int[]{256,1024,8192}) {
            double[] directions=directions(count);
            assertEquals(workload.run(directions,false),workload.run(directions,true));
        }
    }

    @Test
    void waterBoxBenchmarkMatchesClassicHits() {
        var water = SonarChunkReader.SectionSnapshot.copy(palette(Blocks.WATER.defaultBlockState()));
        var stone = SonarChunkReader.SectionSnapshot.copy(palette(Blocks.STONE.defaultBlockState()));
        Map<Long, SonarChunkReader.ChunkSnapshot> chunks = new HashMap<>();
        for(int x=-5;x<=4;x++) for(int z=-5;z<=4;z++) {
            Map<Integer, SonarChunkReader.SectionSnapshot> sections = new HashMap<>();
            for(int y=-5;y<=4;y++) sections.put(y,
                    x>=-4&&x<4&&y>=-4&&y<4&&z>=-4&&z<4 ? water : stone);
            chunks.put(ChunkPos.asLong(x,z),new SonarChunkReader.ChunkSnapshot(Map.copyOf(sections),true));
        }
        var snapshot = new SonarChunkReader.SonarWorldSnapshot(Map.copyOf(chunks));
        int count=8192;
        double[] directions=directions(count);
        double[] expected=run(snapshot,directions,false);
        assertArrayEquals(expected,run(snapshot,directions,true));
        // Opt-in timings are diagnostic, never a flaky performance assertion.
        if(Boolean.getBoolean("echo.traceBenchmark")) {
            for(int i=0;i<8;i++) { run(snapshot,directions,false); run(snapshot,directions,true); }
            long[] a=new long[15], b=new long[15];
            for(int i=0;i<a.length;i++) {
                long t=System.nanoTime(); run(snapshot,directions,(i&1)!=0);
                long middle=System.nanoTime(); run(snapshot,directions,(i&1)==0);
                long end=System.nanoTime();
                a[i]=(i&1)==0?middle-t:end-middle; b[i]=(i&1)==0?end-middle:middle-t;
            }
            java.util.Arrays.sort(a); java.util.Arrays.sort(b);
            System.out.printf("SNAPSHOT %d rays: classic %.3f ms, section+cursor %.3f ms, %.2fx%n",
                    count,a[7]/1e6,b[7]/1e6,(double)a[7]/b[7]);
        }
    }

    private static double[] directions(int count) {
        double[] directions=new double[count*3];
        for(int i=0;i<count;i++) {
            double y=1-2*(i+.5)/count, r=Math.sqrt(1-y*y), a=i*Math.PI*(3-Math.sqrt(5));
            directions[i*3]=Math.cos(a)*r; directions[i*3+1]=y; directions[i*3+2]=Math.sin(a)*r;
        }
        return directions;
    }

    private static double[] run(SonarChunkReader.SonarWorldSnapshot snapshot, double[] directions, boolean fast) {
        var cursor=snapshot.cursor();
        var pos=new BlockPos.MutableBlockPos();
        double[] hits=new double[directions.length*2];
        class Visitor implements SonarSectionSkippingDda.Visitor {
            int index;
            public boolean skipSection(int x,int y,int z) { return cursor.isWaterSection(x,y,z); }
            public boolean visit(int x,int y,int z,double distance,double incidence) {
                var cell=fast?cursor.probe(x,y,z):snapshot.probe(x,y,z,pos);
                if(cell.type()==SonarChunkReader.CellType.WATER) return true;
                hits[index]=distance; hits[index+1]=incidence;
                hits[index+2]=x; hits[index+3]=y; hits[index+4]=z; hits[index+5]=cell.type().ordinal();
                return false;
            }
        }
        var visitor=new Visitor();
        for(int i=0;i<directions.length;i+=3) {
            visitor.index=i*2;
            if(fast) SonarSectionSkippingDda.trace(.5,.5,.5,directions[i],directions[i+1],directions[i+2],0,128,visitor);
            else SonarVoxelDda.traceCells(.5,.5,.5,directions[i],directions[i+1],directions[i+2],0,128,visitor);
        }
        return hits;
    }
}
