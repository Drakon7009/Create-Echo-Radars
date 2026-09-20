package org.rassvet.create_echo_radars.content.sonar;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SableTraceSnapshotTest {
    @BeforeAll static void bootstrap() { SonarWorldSnapshotTest.bootstrap(); }

    @Test
    void broadPhaseRejectsDiagonalMissesAndAcceptsParallelAndInsideRays() {
        AABB box=new AABB(10,10,10,12,12,12);
        assertFalse(SonarTraceSupport.segmentIntersectsBox(box,0,0,0,1,1,.1,0,20));
        assertTrue(SonarTraceSupport.segmentIntersectsBox(box,0,11,11,1,0,0,0,20));
        assertTrue(SonarTraceSupport.segmentIntersectsBox(box,11,11,11,-1,0,0,0,20));
        assertFalse(SonarTraceSupport.segmentIntersectsBox(box,0,11,11,1,0,0,0,9));
        assertTrue(SonarTraceSupport.segmentIntersectsBox(box,0,10,10,1,0,0,0,10));
    }

    @Test
    void sparseSublevelAndRotatedScaledPoseReturnSamePhysicalHit() throws Exception {
        for (double scale : new double[]{1,1.5}) {
            Pose3d pose=new Pose3d();
            pose.position().set(100,20,-50);
            pose.orientation().rotationYXZ(.7,.3,-.2);
            pose.scale().set(scale);
            Vec3 localStart=new Vec3(.5,.5,.5);
            Vec3 origin=pose.transformPosition(localStart);
            Vec3 direction=pose.transformPosition(localStart.add(1,0,0)).subtract(origin).normalize();
            var hit=trace(pose,35,origin,direction,128);
            assertTrue(hit.isPresent());
            assertEquals(34.5*scale,hit.get().distance(),1e-8);
            assertEquals(1,hit.get().incidence(),1e-8);
            assertEquals(Blocks.STONE.defaultBlockState(),hit.get().state());
            assertTrue(trace(pose,35,origin,direction,20).isEmpty());
        }
    }

    @Test
    void clippedPlotBoundsDoNotExposeBlocksOutsideTheConstruction() throws Exception {
        assertTrue(trace(new Pose3d(),36,new Vec3(.5,.5,.5),new Vec3(1,0,0),128).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static Optional<SableSonarCompat.Hit> trace(Pose3d pose,int minX,Vec3 origin,Vec3 direction,double to)
            throws Exception {
        var states=new PalettedContainer<BlockState>(Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(),PalettedContainer.Strategy.SECTION_STATES);
        states.set(3,0,0,Blocks.STONE.defaultBlockState()); // local x=35 in section x=2
        Class<?> sectionClass=Class.forName(SableSonarCompat.class.getName()+"$SectionSnapshot");
        var sectionConstructor=sectionClass.getDeclaredConstructors()[0];
        sectionConstructor.setAccessible(true);
        Object section=sectionConstructor.newInstance(states,minX,0,0,47,15,15);
        Class<?> snapshotClass=Class.forName(SableSonarCompat.class.getName()+"$SubLevelSnapshot");
        var constructor=snapshotClass.getDeclaredConstructors()[0];constructor.setAccessible(true);
        Object snapshot=constructor.newInstance(pose,new AABB(-1000,-1000,-1000,1000,1000,1000),
                Map.of(SectionPos.asLong(2,0,0),section));
        var trace=snapshotClass.getDeclaredMethod("trace",Vec3.class,Vec3.class,double.class,double.class);
        trace.setAccessible(true);
        return (Optional<SableSonarCompat.Hit>)trace.invoke(snapshot,origin,direction,0,to);
    }
}
