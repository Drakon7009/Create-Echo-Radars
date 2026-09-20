import java.util.Arrays;
import org.rassvet.create_echo_radars.content.sonar.SonarVoxelDda;
import org.rassvet.create_echo_radars.content.sonar.SonarSectionSkippingDda;

/** Standalone warm A/B benchmark, no Minecraft launch or external dependencies.
 * javac -cp build/classes/java/main -d build/trace-benchmark tools/benchmark/SonarTraversalBenchmark.java
 * java -cp "build/classes/java/main;build/trace-benchmark" SonarTraversalBenchmark
 */
public class SonarTraversalBenchmark {
    private static volatile long blackhole;
    private static final int RAYS = 32768;
    private static final double[] DIRECTIONS = new double[RAYS * 3];
    static {
        double angle = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < RAYS; i++) {
            double y = 1 - 2 * (i + .5) / RAYS;
            double radius = Math.sqrt(1 - y * y);
            DIRECTIONS[i*3] = Math.cos(i * angle) * radius;
            DIRECTIONS[i*3+1] = y;
            DIRECTIONS[i*3+2] = Math.sin(i * angle) * radius;
        }
    }

    private static class WaterScene implements SonarSectionSkippingDda.Visitor {
        long checksum;
        long cells;
        long sections;
        public boolean skipSection(int x, int y, int z) {
            sections++;
            return x >= -4 && x < 4 && y >= -4 && y < 4 && z >= -4 && z < 4;
        }
        public boolean visit(int x, int y, int z, double distance, double incidence) {
            cells++;
            if (x >= -64 && x < 64 && y >= -64 && y < 64 && z >= -64 && z < 64) return true;
            checksum += (x * 31L + y * 17L + z) ^ Double.doubleToRawLongBits(distance)
                    ^ Double.doubleToRawLongBits(incidence);
            return false;
        }
    }

    private static long run(boolean skipping, boolean report) {
        WaterScene scene = new WaterScene();
        long started = System.nanoTime();
        for (int i = 0; i < RAYS; i++) {
            int o = i * 3;
            if (skipping) SonarSectionSkippingDda.trace(.5,.5,.5,
                    DIRECTIONS[o],DIRECTIONS[o+1],DIRECTIONS[o+2],0,128,scene);
            else SonarVoxelDda.traceCells(.5,.5,.5,
                    DIRECTIONS[o],DIRECTIONS[o+1],DIRECTIONS[o+2],0,128,scene);
        }
        long elapsed = System.nanoTime() - started;
        blackhole = scene.checksum;
        if (report) System.out.printf("%s checksum=%d cellVisits=%d sectionChecks=%d%n",
                skipping ? "section" : "voxel", scene.checksum, scene.cells, scene.sections);
        return elapsed;
    }

    public static void main(String[] args) {
        run(false, true); long expected = blackhole;
        run(true, true);
        if (expected != blackhole) throw new AssertionError("Hit checksums differ");
        for (int i=0;i<12;i++) { run(false,false); run(true,false); }
        long[] a=new long[21], b=new long[21];
        for(int i=0;i<a.length;i++) {
            if ((i&1)==0) { a[i]=run(false,false); b[i]=run(true,false); }
            else { b[i]=run(true,false); a[i]=run(false,false); }
        }
        Arrays.sort(a); Arrays.sort(b);
        System.out.printf("%d rays water + wall, median: voxel=%.3f ms section=%.3f ms speedup=%.2fx%n",
                RAYS,a[10]/1e6,b[10]/1e6,(double)a[10]/b[10]);
    }
}
