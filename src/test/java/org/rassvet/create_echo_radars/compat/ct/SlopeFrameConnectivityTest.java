package org.rassvet.create_echo_radars.compat.ct;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopeFrameConnectivityTest {
    @Test
    void northAndSouthRegularSlopesHaveEastWestTriangleCaps() {
        for (SlopeFrameConnectivity.HorizontalFacing facing
                : new SlopeFrameConnectivity.HorizontalFacing[]{
                        SlopeFrameConnectivity.HorizontalFacing.NORTH,
                        SlopeFrameConnectivity.HorizontalFacing.SOUTH}) {
            SlopeFrameConnectivity.Slope slope = regular(facing);
            assertTrue(isEnd(slope, SlopeFrameConnectivity.Face.EAST));
            assertTrue(isEnd(slope, SlopeFrameConnectivity.Face.WEST));
            assertFalse(isEnd(slope, SlopeFrameConnectivity.Face.NORTH));
            assertFalse(isEnd(slope, SlopeFrameConnectivity.Face.SOUTH));
            assertFalse(isEnd(slope, SlopeFrameConnectivity.Face.UP));
            assertFalse(isEnd(slope, SlopeFrameConnectivity.Face.DOWN));
        }
    }

    @Test
    void eastAndWestRegularSlopesHaveNorthSouthTriangleCaps() {
        for (SlopeFrameConnectivity.HorizontalFacing facing
                : new SlopeFrameConnectivity.HorizontalFacing[]{
                        SlopeFrameConnectivity.HorizontalFacing.EAST,
                        SlopeFrameConnectivity.HorizontalFacing.WEST}) {
            SlopeFrameConnectivity.Slope slope = regular(facing);
            assertTrue(isEnd(slope, SlopeFrameConnectivity.Face.NORTH));
            assertTrue(isEnd(slope, SlopeFrameConnectivity.Face.SOUTH));
            assertFalse(isEnd(slope, SlopeFrameConnectivity.Face.EAST));
            assertFalse(isEnd(slope, SlopeFrameConnectivity.Face.WEST));
            assertFalse(isEnd(slope, SlopeFrameConnectivity.Face.UP));
            assertFalse(isEnd(slope, SlopeFrameConnectivity.Face.DOWN));
        }
    }

    @Test
    void everyVerticalSlopeHasTopAndBottomTriangleCaps() {
        for (SlopeFrameConnectivity.HorizontalFacing facing
                : SlopeFrameConnectivity.HorizontalFacing.values()) {
            SlopeFrameConnectivity.Slope slope = vertical(facing);
            assertTrue(isEnd(slope, SlopeFrameConnectivity.Face.UP));
            assertTrue(isEnd(slope, SlopeFrameConnectivity.Face.DOWN));
            assertFalse(isEnd(slope, SlopeFrameConnectivity.Face.NORTH));
            assertFalse(isEnd(slope, SlopeFrameConnectivity.Face.SOUTH));
            assertFalse(isEnd(slope, SlopeFrameConnectivity.Face.EAST));
            assertFalse(isEnd(slope, SlopeFrameConnectivity.Face.WEST));
        }
    }

    private static boolean isEnd(SlopeFrameConnectivity.Slope slope,
                                 SlopeFrameConnectivity.Face face) {
        return SlopeFrameConnectivity.isTriangularEndFace(slope, face);
    }

    private static SlopeFrameConnectivity.Slope regular(
            SlopeFrameConnectivity.HorizontalFacing facing) {
        return new SlopeFrameConnectivity.Slope(
                SlopeFrameConnectivity.Kind.REGULAR, facing);
    }

    private static SlopeFrameConnectivity.Slope vertical(
            SlopeFrameConnectivity.HorizontalFacing facing) {
        return new SlopeFrameConnectivity.Slope(
                SlopeFrameConnectivity.Kind.VERTICAL, facing);
    }
}
