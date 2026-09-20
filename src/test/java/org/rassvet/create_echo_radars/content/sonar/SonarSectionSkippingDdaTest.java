package org.rassvet.create_echo_radars.content.sonar;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SonarSectionSkippingDdaTest {
    private static boolean transparent(int x, int y, int z) {
        return Math.floorMod(x * 31 + y * 13 + z * 7, 5) != 0;
    }

    @Test
    void skippedSectionsPreserveEveryOtherCellAndExactBoundaryTimes() {
        Random random = new Random(749183);
        for (int i = 0; i < 10_000; i++) {
            double ox = random.nextDouble() * 1024 - 512;
            double oy = random.nextDouble() * 256 - 128;
            double oz = random.nextDouble() * 1024 - 512;
            double dx = random.nextDouble() * 2 - 1;
            double dy = random.nextDouble() * 2 - 1;
            double dz = random.nextDouble() * 2 - 1;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double from = random.nextDouble() * 32;
            compare(ox, oy, oz, dx / length, dy / length, dz / length,
                    from, from + random.nextDouble() * 512);
        }
    }

    @Test
    void handlesTiedAxesZeroAxesAndNegativeSectionEdges() {
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++)
            for (int z = -1; z <= 1; z++) {
                double length = Math.sqrt(x * x + y * y + z * z);
                if (length == 0) continue;
                for (double start : new double[]{-32, -16.5, -16, -0.5, 0, 0.5, 16, 32}) {
                    compare(start, start, start, x / length, y / length, z / length, 0, 256);
                    compare(start, start, start, x / length, y / length, z / length, 1, 1);
                }
            }
    }

    @Test
    void emptyIntervalAndStationaryTransparentRayTerminate() {
        SonarSectionSkippingDda.Visitor visitor = new SonarSectionSkippingDda.Visitor() {
            public boolean skipSection(int x, int y, int z) { return true; }
            public boolean visit(int x, int y, int z, double d, double incidence) {
                fail("Transparent cells should not be visited");
                return false;
            }
        };
        SonarSectionSkippingDda.trace(0, 0, 0, 1, 0, 0, 3, 2, visitor);
        SonarSectionSkippingDda.trace(0, 0, 0, 0, 0, 0, 0, 128, visitor);
    }

    @Test
    void firstOpaqueCellStopsTraversal() {
        List<SonarVoxelDda.Cell> cells = new ArrayList<>();
        SonarSectionSkippingDda.trace(.5, .5, .5, 1, 0, 0, 0, 128,
                new SonarSectionSkippingDda.Visitor() {
                    public boolean skipSection(int x, int y, int z) { return x < 3; }
                    public boolean visit(int x, int y, int z, double d, double incidence) {
                        cells.add(new SonarVoxelDda.Cell(x, y, z, d, incidence));
                        return false;
                    }
                });
        assertEquals(List.of(new SonarVoxelDda.Cell(48, 0, 0, 47.5, 1)), cells);
    }

    private static void compare(double ox, double oy, double oz, double dx, double dy, double dz,
                                double from, double to) {
        List<SonarVoxelDda.Cell> expected = new ArrayList<>();
        ReferenceVoxelDda.traceCells(ox, oy, oz, dx, dy, dz, from, to, (x,y,z,d,i) -> {
            if (!transparent(x >> 4, y >> 4, z >> 4)) expected.add(new SonarVoxelDda.Cell(x,y,z,d,i));
            return true;
        });
        List<SonarVoxelDda.Cell> actual = new ArrayList<>();
        SonarSectionSkippingDda.trace(ox, oy, oz, dx, dy, dz, from, to,
                new SonarSectionSkippingDda.Visitor() {
                    public boolean skipSection(int x, int y, int z) { return transparent(x,y,z); }
                    public boolean visit(int x, int y, int z, double d, double i) {
                        actual.add(new SonarVoxelDda.Cell(x,y,z,d,i)); return true;
                    }
                });
        assertEquals(expected, actual);
        List<SonarVoxelDda.Cell> raw = new ArrayList<>();
        SonarVoxelDda.traceCells(ox,oy,oz,dx,dy,dz,from,to,(x,y,z,d,i) -> {
            if (!transparent(x >> 4,y >> 4,z >> 4)) raw.add(new SonarVoxelDda.Cell(x,y,z,d,i));
            return true;
        });
        assertEquals(expected,raw);
    }
}
