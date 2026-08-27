package org.rassvet.create_echo_radars.content.sonar;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SonarMathTest {
    @Test
    void coneUsesStaticTwentyDegreeVerticalAperture() {
        assertTrue(SonarKinematics.insideCone(0, 2, -30, 0, -1, 120, 128));
        assertFalse(SonarKinematics.insideCone(0, 20, -30, 0, -1, 120, 128));
        assertFalse(SonarKinematics.insideCone(40, 0, -10, 0, -1, 60, 128));
        assertFalse(SonarKinematics.insideCone(0, 0, 10, 0, -1, 120, 128));
    }

    @Test
    void coneUsesTiltedLocalBasisInsteadOfWorldY() {
        double pitch = Math.toRadians(35);

        assertTrue(SonarKinematics.insideCone(
                new SonarMath.Projection(30, 0, 0, 30), 120, 128));
        assertFalse(SonarKinematics.insideCone(
                new SonarMath.Projection(Math.cos(pitch) * 30, 0,
                        -Math.sin(pitch) * 30, 30), 120, 128));
    }

    @Test
    void reflectedIntensityFavorsHeadOnNearbySurfaces() {
        float headOn = SonarKinematics.reflectedIntensity(1, 0.1f);
        float grazing = SonarKinematics.reflectedIntensity(0.2f, 0.1f);
        float distant = SonarKinematics.reflectedIntensity(1, 0.9f);
        float shallowFloor = SonarKinematics.reflectedIntensity(
                (float) Math.sin(Math.toRadians(5)), 0.5f);
        assertTrue(headOn > grazing);
        assertTrue(headOn > distant);
        assertTrue(shallowFloor > 0.1f,
                "A confirmed floor return from an elevation beam must remain visible");
        assertTrue(headOn <= 1 && grazing >= 0);
    }

    @Test
    void displayProjectionMapsApexCenterAndSectorEdges() {
        SonarDisplayProjection.Point apex = SonarDisplayProjection.project(0, 0, 120);
        assertEquals(0, apex.x(), 1.0e-6);
        assertEquals(0.9, apex.z(), 1.0e-6);

        SonarDisplayProjection.Point edge =
                SonarDisplayProjection.project(1, Math.toRadians(60), 120);
        assertEquals(0.9, edge.x(), 1.0e-6);
        assertEquals(0, edge.z(), 1.0e-6);

        SonarDisplayProjection.Point center =
                SonarDisplayProjection.project(1, 0, 120);
        assertEquals(0, center.x(), 1.0e-6);
        assertEquals(-0.9, center.z(), 1.0e-6);
    }

    @Test
    void monitorDimensionsFallBackToTheFormedSquareSize() {
        assertEquals(new SonarMonitorDimensions(8, 8),
                SonarDisplayLayout.resolveDimensions(1, 1, 8));
        assertEquals(new SonarMonitorDimensions(1, 1),
                SonarDisplayLayout.resolveDimensions(1, 1, 1));
    }

    @Test
    void monitorDimensionsPreserveStoredRectangles() {
        assertEquals(new SonarMonitorDimensions(6, 3),
                SonarDisplayLayout.resolveDimensions(6, 3, 8));
        assertEquals(new SonarMonitorDimensions(1, 4),
                SonarDisplayLayout.resolveDimensions(1, 4, 8));
    }

    @Test
    void monitorAreaStaysAnchoredToTheControllerCorner() {
        for (SonarMonitorDimensions dimensions : List.of(
                new SonarMonitorDimensions(1, 1),
                new SonarMonitorDimensions(2, 2),
                new SonarMonitorDimensions(10, 6))) {
            SonarDisplayLayout.Area area = SonarDisplayLayout.area(dimensions);
            assertEquals(1, area.right(), 1.0e-6);
            assertEquals(1, area.top(), 1.0e-6);
            assertEquals(1 - dimensions.width(), area.left(), 1.0e-6);
            assertEquals(1 - dimensions.height(), area.bottom(), 1.0e-6);
            assertEquals(dimensions.width(), area.width(), 1.0e-6);
            assertEquals(dimensions.height(), area.height(), 1.0e-6);
        }
    }

    @Test
    void tracksOutsideTheVisibleAutoRangeStayOffTheDisplay() {
        assertTrue(SonarDisplayLayout.trackInsideDisplayRange(64, 64));
        assertFalse(SonarDisplayLayout.trackInsideDisplayRange(64.01, 64));
        assertFalse(SonarDisplayLayout.trackInsideDisplayRange(Double.NaN, 64));
    }

    @Test
    void sideScanWaterfallUsesAdjacentRowsFromNewestToOldest() {
        SonarDisplayLayout.Area area =
                SonarDisplayLayout.area(new SonarMonitorDimensions(4, 4));
        assertEquals(96, SonarDisplayLayout.sideScanRowCapacity(area));

        SonarDisplayLayout.SideScanCell newest =
                SonarDisplayLayout.sideScanCell(area, 10, 128, true, 0, false);
        SonarDisplayLayout.SideScanCell previous =
                SonarDisplayLayout.sideScanCell(area, 10, 128, true, 1, false);
        assertEquals(area.top(), newest.top(), 1.0e-6);
        assertEquals(newest.bottom(), previous.top(), 1.0e-6);
        assertTrue(previous.bottom() < newest.bottom());
    }

    @Test
    void sideScanRangeCellsFillBothSidesWithoutCrossingTheCenter() {
        SonarDisplayLayout.Area area =
                SonarDisplayLayout.area(new SonarMonitorDimensions(4, 4));
        SonarDisplayLayout.SideScanCell left =
                SonarDisplayLayout.sideScanCell(area, 0, 128, false, 0, false);
        SonarDisplayLayout.SideScanCell right =
                SonarDisplayLayout.sideScanCell(area, 0, 128, true, 0, false);
        SonarDisplayLayout.SideScanCell nextRight =
                SonarDisplayLayout.sideScanCell(area, 1, 128, true, 0, false);

        assertEquals(area.centerX(), left.right(), 1.0e-6);
        assertEquals(area.centerX(), right.left(), 1.0e-6);
        assertEquals(right.right(), nextRight.left(), 1.0e-6);
    }

    @Test
    void sideScanWaterfallCanEnterFromEveryScreenEdge() {
        SonarDisplayLayout.Area area =
                SonarDisplayLayout.area(new SonarMonitorDimensions(4, 4));
        SonarDisplayLayout.SideScanCell bottom = SonarDisplayLayout.sideScanCell(
                area, 0, 128, true, 0, false, false, true);
        SonarDisplayLayout.SideScanCell top = SonarDisplayLayout.sideScanCell(
                area, 0, 128, true, 0, false, false, false);
        SonarDisplayLayout.SideScanCell left = SonarDisplayLayout.sideScanCell(
                area, 0, 128, true, 0, false, true, true);
        SonarDisplayLayout.SideScanCell right = SonarDisplayLayout.sideScanCell(
                area, 0, 128, true, 0, false, true, false);

        assertEquals(area.bottom(), bottom.bottom(), 1.0e-6);
        assertEquals(area.top(), top.top(), 1.0e-6);
        assertEquals(area.left(), left.left(), 1.0e-6);
        assertEquals(area.right(), right.right(), 1.0e-6);
    }

    @Test
    void sideScanHistoryWaitsForDataBeforeAnActiveFrameOccupiesARow() {
        SonarFrame emptyActive = new SonarFrame(1, 100, 0,
                false, 0.2f, List.of());
        SonarFrame activeWithData = new SonarFrame(1, 100, 0,
                false, 0.2f, List.of(new SonarReturn(0, 4, 0, 0.04f, 1)));
        SonarFrame completedEmpty = new SonarFrame(1, 100, 110,
                true, List.of());

        assertFalse(SonarDisplayLayout.sideScanFrameOccupiesRow(emptyActive));
        assertTrue(SonarDisplayLayout.sideScanFrameOccupiesRow(activeWithData));
        assertTrue(SonarDisplayLayout.sideScanFrameOccupiesRow(completedEmpty));
    }

    @Test
    void onePixelSideIsSharedByAllEchoLocations() {
        SonarDisplayLayout.Area area = SonarDisplayLayout.area(new SonarMonitorDimensions(10, 6));
        float pitch = SonarDisplayLayout.echoPixelPitch(area, 51, 128);
        float solid = SonarDisplayLayout.echoPixelSide(pitch, false);
        float gapped = SonarDisplayLayout.echoPixelSide(pitch, true);

        assertTrue(solid > 0);
        assertTrue(gapped > 0);
        assertEquals(pitch, solid, 1.0e-6);
        assertTrue(gapped < solid);
        assertEquals(gapped, SonarDisplayLayout.echoPixelSide(pitch, true), 1.0e-6);
    }

    @Test
    void dynamicPixelSidesShrinkOnlyCrowdedEchoes() {
        List<SonarDisplayLayout.PixelPoint> points = List.of(
                new SonarDisplayLayout.PixelPoint(0, 0),
                new SonarDisplayLayout.PixelPoint(0.4f, 0),
                new SonarDisplayLayout.PixelPoint(2, 0));
        float[] sides = SonarDisplayLayout.dynamicPixelSides(points, 1, false);

        assertEquals(0.4f, sides[0], 1.0e-6);
        assertEquals(0.4f, sides[1], 1.0e-6);
        assertEquals(1, sides[2], 1.0e-6);
        assertTrue((sides[0] + sides[1]) * 0.5f <= 0.4f);
    }

    @Test
    void circularPixelsScaleWithRangeAndStayInsideTheDisplay() {
        SonarDisplayLayout.Area area = SonarDisplayLayout.area(new SonarMonitorDimensions(6, 3));
        SonarDisplayLayout.CircularGeometry near = SonarDisplayLayout.circularGeometry(area, 32);
        SonarDisplayLayout.CircularGeometry far = SonarDisplayLayout.circularGeometry(area, 256);

        assertTrue(far.pixelHalfSize() < near.pixelHalfSize());
        assertTrue(far.pixelCenterRadius() + far.pixelHalfSize() * Math.sqrt(2)
                <= far.radius() + 1.0e-5);
        assertTrue(far.radius() <= area.minSize() * 0.5f);
    }

    @Test
    void circularAutoRangeUsesTheWholeVisibleComposite() {
        List<SonarReturn> visiblePicture = List.of(
                new SonarReturn(0, 15, 10, 0.125f, 0.5f),
                new SonarReturn(1, 95, 190, 0.75f, 0.8f),
                new SonarReturn(2, 47, 300, 0.375f, 0.6f));

        assertEquals(96, SonarDisplayLayout.compositeDisplayRange(visiblePicture, 128, 16));
        assertEquals(32, SonarDisplayLayout.compositeDisplayRange(List.of(), 128, 32));
    }

    @Test
    void sideScanAutoRangeDropsReturnsThatLeftTheVisibleHistory() {
        SonarFrame oldFarFrame = new SonarFrame(1, 0, 1, true,
                List.of(new SonarReturn(0, 123, 0, 124 / 128f, 1)));
        SonarFrame visibleNearFrame = new SonarFrame(2, 2, 3, true,
                List.of(new SonarReturn(0, 23, 0, 24 / 128f, 1)));

        assertEquals(124, SonarDisplayLayout.compositeDisplayRangeFromFrames(
                List.of(oldFarFrame, visibleNearFrame), 128, 128));
        assertEquals(24, SonarDisplayLayout.compositeDisplayRangeFromFrames(
                List.of(visibleNearFrame), 128, 124));
    }

    @Test
    void automaticDisplayRangeUsesRecentHistoryInsteadOfOnlyTheLastPing() {
        AutoDisplayRangeTracker tracker = new AutoDisplayRangeTracker(3, 128);

        assertEquals(80, tracker.update(80, 128));
        assertEquals(80, tracker.update(20, 128));
        assertEquals(80, tracker.update(30, 128));
        assertEquals(40, tracker.update(40, 128));
        assertEquals(40, tracker.update(0, 128),
                "an empty ping must not reset automatic scaling");
        assertEquals(100, tracker.update(100, 128),
                "a newly detected farther return must expand the scale immediately");
    }

    @Test
    void dynamicPixelSidesLeaveRequestedGaps() {
        List<SonarDisplayLayout.PixelPoint> points = List.of(
                new SonarDisplayLayout.PixelPoint(0, 0),
                new SonarDisplayLayout.PixelPoint(0.5f, 0));
        float[] solid = SonarDisplayLayout.dynamicPixelSides(points, 1, false);
        float[] gapped = SonarDisplayLayout.dynamicPixelSides(points, 1, true);

        assertEquals(0.5f, solid[0], 1.0e-6);
        assertTrue(gapped[0] < solid[0]);
        assertEquals(gapped[0], gapped[1], 1.0e-6);
    }

    @Test
    void subpixelDuplicateReturnsDoNotCollapsePixelSize() {
        List<SonarDisplayLayout.PixelPoint> points = List.of(
                new SonarDisplayLayout.PixelPoint(0, 0),
                new SonarDisplayLayout.PixelPoint(0.01f, 0.01f),
                new SonarDisplayLayout.PixelPoint(2, 2));
        float[] sides = SonarDisplayLayout.dynamicPixelSides(points, 1, false);

        assertEquals(1, sides[0], 1.0e-6);
        assertEquals(1, sides[1], 1.0e-6);
        assertEquals(1, sides[2], 1.0e-6);
    }

    @Test
    void dynamicPixelsNeverShrinkBelowRenderableFloor() {
        List<SonarDisplayLayout.PixelPoint> points = List.of(
                new SonarDisplayLayout.PixelPoint(0, 0),
                new SonarDisplayLayout.PixelPoint(0.31f, 0));
        float[] sides = SonarDisplayLayout.dynamicPixelSides(points, 1, true);

        assertTrue(sides[0] >= 0.30f);
        assertTrue(sides[1] >= 0.30f);
    }

    @Test
    void verticalExtentsFillResolvableGapsWithinOneBeam() {
        List<SonarDisplayLayout.PixelPoint> points = List.of(
                new SonarDisplayLayout.PixelPoint(0, 0, 7),
                new SonarDisplayLayout.PixelPoint(0, 0.3f, 7),
                new SonarDisplayLayout.PixelPoint(0, 0.6f, 7));
        SonarDisplayLayout.PixelExtent[] extents =
                SonarDisplayLayout.dynamicPixelExtents(points, 0.1f, false);

        assertEquals(0.15f, extents[0].upperHalfHeight(), 1.0e-6);
        assertEquals(0.15f, extents[1].lowerHalfHeight(), 1.0e-6);
        assertEquals(0.15f, extents[1].upperHalfHeight(), 1.0e-6);
        assertEquals(0.15f, extents[2].lowerHalfHeight(), 1.0e-6);
    }

    @Test
    void verticalExtentsDoNotBridgeDifferentBeamsOrLargeEmptyRanges() {
        List<SonarDisplayLayout.PixelPoint> differentBeams = List.of(
                new SonarDisplayLayout.PixelPoint(0, 0, 1),
                new SonarDisplayLayout.PixelPoint(0, 0.3f, 2));
        SonarDisplayLayout.PixelExtent[] separated =
                SonarDisplayLayout.dynamicPixelExtents(differentBeams, 0.1f, false);
        assertEquals(0.05f, separated[0].upperHalfHeight(), 1.0e-6);

        List<SonarDisplayLayout.PixelPoint> largeGap = List.of(
                new SonarDisplayLayout.PixelPoint(0, 0, 1),
                new SonarDisplayLayout.PixelPoint(0, 0.5f, 1));
        SonarDisplayLayout.PixelExtent[] capped =
                SonarDisplayLayout.dynamicPixelExtents(largeGap, 0.1f, false);
        assertEquals(0.05f, capped[0].upperHalfHeight(), 1.0e-6);
    }

    @Test
    void displayEchoFootprintShrinksWithBeamDensity() {
        float lowDensity = SonarDisplayProjection.echoAngularHalfWidth(120, 51, true);
        float highDensity = SonarDisplayProjection.echoAngularHalfWidth(120, 121, true);
        float highDensitySpacing = 120f / 120f;

        assertTrue(highDensity < lowDensity);
        assertTrue(highDensity * 2 < highDensitySpacing,
                "Dense beam settings must leave adjacent sonar cells visually separated");
    }

    @Test
    void displayEchoFootprintCanFillOrGapAdjacentCells() {
        int range = 128;
        float binSpacing = 1f / range;
        float solidHalfWidth = SonarDisplayProjection.echoRangeHalfWidth(range);
        float gappedHalfWidth = SonarDisplayProjection.echoRangeHalfWidth(range, true);

        assertEquals(binSpacing, solidHalfWidth * 2, 1.0e-6,
                "Solid range cells should meet without overlapping");
        assertTrue(gappedHalfWidth * 2 < binSpacing,
                "Gapped range cells must leave space between neighbouring range bins");
    }

    @Test
    void echoCellsFollowPolarGridAndStayInsideSector() {
        SonarDisplayProjection.Cell center = SonarDisplayProjection.echoCell(
                31, 128, 0, 2, 120, false);
        SonarDisplayProjection.Cell edge = SonarDisplayProjection.echoCell(
                31, 128, 60, 2, 120, false);

        assertEquals(center.innerLeft().z(), center.innerRight().z(), 1.0e-6);
        assertTrue(edge.innerLeft().z() != edge.innerRight().z(),
                "An edge cell must rotate with its beam instead of staying axis-aligned");
        assertTrue(edge.innerRight().x() <= 0.90 + 1.0e-6);
        assertTrue(edge.outerRight().x() <= 0.90 + 1.0e-6);
    }

    @Test
    void adjacentPolarCellsShareEdgesWithoutOverlap() {
        SonarDisplayProjection.Cell near = SonarDisplayProjection.echoCell(
                31, 128, 0, 2, 120, false);
        SonarDisplayProjection.Cell far = SonarDisplayProjection.echoCell(
                32, 128, 0, 2, 120, false);
        SonarDisplayProjection.Cell left = SonarDisplayProjection.echoCell(
                31, 128, -2, 2, 120, false);

        assertEquals(near.outerLeft().x(), far.innerLeft().x(), 1.0e-6);
        assertEquals(near.outerLeft().z(), far.innerLeft().z(), 1.0e-6);
        assertEquals(left.innerRight().x(), near.innerLeft().x(), 1.0e-6);
        assertEquals(left.innerRight().z(), near.innerLeft().z(), 1.0e-6);
        assertEquals(left.outerRight().x(), near.outerLeft().x(), 1.0e-6);
        assertEquals(left.outerRight().z(), near.outerLeft().z(), 1.0e-6);
    }

    @Test
    void adaptiveHorizontalCellsShrinkToActualNeighbourSpacing() {
        List<SonarDisplayLayout.AngularPoint> points = List.of(
                new SonarDisplayLayout.AngularPoint(40, -0.6f, 0.6f),
                new SonarDisplayLayout.AngularPoint(40, 0, 2.4f),
                new SonarDisplayLayout.AngularPoint(40, 0.6f, 0.6f),
                new SonarDisplayLayout.AngularPoint(41, 0, 2.4f));
        float[] resolutions = SonarDisplayLayout.angularResolutions(points, 2.4f);

        assertEquals(0.6f, resolutions[0], 1.0e-6);
        assertEquals(0.6f, resolutions[1], 1.0e-6,
                "A wide base echo must not cover its refined horizontal neighbours");
        assertEquals(0.6f, resolutions[2], 1.0e-6);
        assertEquals(2.4f, resolutions[3], 1.0e-6,
                "Returns in another range row must not affect this row");
        float leftHalfWidth = SonarDisplayProjection.echoAngularHalfWidth(resolutions[0], true);
        float centerHalfWidth = SonarDisplayProjection.echoAngularHalfWidth(resolutions[1], true);
        assertTrue(leftHalfWidth + centerHalfWidth < 0.6f,
                "Gap mode must leave visible space between adaptive neighbours");
    }

    @Test
    void duplicateBearingsDoNotHideNearestDistinctNeighbour() {
        List<SonarDisplayLayout.AngularPoint> points = List.of(
                new SonarDisplayLayout.AngularPoint(40, 0, 2.4f),
                new SonarDisplayLayout.AngularPoint(40, 0, 2.4f),
                new SonarDisplayLayout.AngularPoint(40, 0.6f, 0.6f));
        float[] resolutions = SonarDisplayLayout.angularResolutions(points, 2.4f);

        assertEquals(0.6f, resolutions[0], 1.0e-6);
        assertEquals(0.6f, resolutions[1], 1.0e-6);
        assertEquals(0.6f, resolutions[2], 1.0e-6);
    }

    @Test
    void isolatedAngularEchoKeepsReadableMinimumWidth() {
        List<SonarDisplayLayout.AngularPoint> points = List.of(
                new SonarDisplayLayout.AngularPoint(40, 0, 0.05f));

        float[] resolutions = SonarDisplayLayout.angularResolutions(points, 2.4f);

        assertEquals(0.72f, resolutions[0], 1.0e-6);
    }

    @Test
    void readableAngularLayoutKeepsStrongestNonOverlappingEchoes() {
        List<SonarDisplayLayout.AngularPoint> points = List.of(
                new SonarDisplayLayout.AngularPoint(40, 0, 0.05f, 0.2f),
                new SonarDisplayLayout.AngularPoint(40, 0.2f, 0.05f, 0.9f),
                new SonarDisplayLayout.AngularPoint(40, 1.0f, 0.05f, 0.5f));

        SonarDisplayLayout.AngularLayout layout =
                SonarDisplayLayout.readableAngularLayout(points, 2.4f, false);

        assertArrayEquals(new boolean[] {false, true, true}, layout.visible());
        assertEquals(0.72f, layout.resolutions()[1], 1.0e-6);
        assertEquals(0.72f, layout.resolutions()[2], 1.0e-6);
    }

    @Test
    void readableAngularLayoutDoesNotRemoveSeparatedSparseEchoes() {
        List<SonarDisplayLayout.AngularPoint> points = List.of(
                new SonarDisplayLayout.AngularPoint(40, -3, 0.05f, 0.2f),
                new SonarDisplayLayout.AngularPoint(40, 0, 0.05f, 0.9f),
                new SonarDisplayLayout.AngularPoint(40, 3, 0.05f, 0.5f));

        SonarDisplayLayout.AngularLayout layout =
                SonarDisplayLayout.readableAngularLayout(points, 2.4f, false);

        assertArrayEquals(new boolean[] {true, true, true}, layout.visible());
    }

    @Test
    void oneBlockPixelsMergeDenseReturnsAtTheirDisplayedDistance() {
        List<SonarDisplayLayout.AngularPoint> points = List.of(
                new SonarDisplayLayout.AngularPoint(9, -2, 1, 0.2f),
                new SonarDisplayLayout.AngularPoint(9, 0, 1, 0.9f),
                new SonarDisplayLayout.AngularPoint(9, 2, 1, 0.5f),
                new SonarDisplayLayout.AngularPoint(9, 8, 1, 0.4f));

        SonarDisplayLayout.AngularLayout layout =
                SonarDisplayLayout.readableAngularLayout(points, 1, false, true);

        assertEquals(6.0256f, layout.resolutions()[0], 1.0e-3);
        assertArrayEquals(new boolean[] {false, true, false, true}, layout.visible());
    }

    @Test
    void oneBlockAngularPixelNarrowsWithDistance() {
        assertEquals(90, SonarDisplayLayout.oneBlockAngularResolutionDegrees(0), 1.0e-6);
        assertTrue(SonarDisplayLayout.oneBlockAngularResolutionDegrees(9)
                > SonarDisplayLayout.oneBlockAngularResolutionDegrees(99));
    }

    @Test
    void blockPixelLayoutKeepsOnlyStrongestReturnInWorldSizedCell() {
        SonarDisplayLayout.BlockPoint nearCenter =
                SonarDisplayLayout.blockPoint(0.5, 0, 0);
        assertEquals(new SonarDisplayLayout.BlockPoint(0, 1), nearCenter);

        SonarDisplayLayout.BlockLayout layout = SonarDisplayLayout.strongestBlockPixels(List.of(
                new SonarDisplayLayout.BlockPixelPoint(3, 7, 0.2f),
                new SonarDisplayLayout.BlockPixelPoint(3, 7, 0.9f),
                new SonarDisplayLayout.BlockPixelPoint(4, 7, 0.4f)));
        assertArrayEquals(new boolean[] {false, true, true}, layout.visible());
    }

    @Test
    void solidBlockCellsShareEdgesWithoutDisplayGaps() {
        SonarDisplayProjection.Cell left =
                SonarDisplayProjection.blockCell(0, 10, 50, 120, false);
        SonarDisplayProjection.Cell right =
                SonarDisplayProjection.blockCell(1, 10, 50, 120, false);
        SonarDisplayProjection.Cell forward =
                SonarDisplayProjection.blockCell(0, 11, 50, 120, false);

        assertEquals(left.innerRight().x(), right.innerLeft().x(), 1.0e-9);
        assertEquals(left.outerRight().x(), right.outerLeft().x(), 1.0e-9);
        assertEquals(left.outerLeft().z(), forward.innerLeft().z(), 1.0e-9);
    }

    @Test
    void polarBlockPixelRotatesAroundSonar() {
        SonarDisplayProjection.Cell cell =
                SonarDisplayProjection.polarBlockCell(4, 10, 50, 120, false);

        assertNotEquals(cell.innerLeft().z(), cell.innerRight().z(), 1.0e-6,
                "A block pixel away from the center ray must rotate with its bearing");
        assertNotEquals(cell.outerLeft().z(), cell.outerRight().z(), 1.0e-6);
    }

    @Test
    void polarBlockGridSharesBoundariesWithoutOverlap() {
        List<SonarDisplayLayout.AngularPoint> points = List.of(
                new SonarDisplayLayout.AngularPoint(9, 0, 1, 0.9f),
                new SonarDisplayLayout.AngularPoint(9, 4, 1, 0.8f),
                new SonarDisplayLayout.AngularPoint(9, 4.5f, 1, 0.2f));
        SonarDisplayLayout.AngularLayout layout =
                SonarDisplayLayout.polarBlockLayout(points, 120);

        assertArrayEquals(new boolean[] {true, true, false}, layout.visible());
        SonarDisplayProjection.Cell left = SonarDisplayProjection.echoCell(9, 50,
                layout.bearings()[0], layout.resolutions()[0], 120, false);
        SonarDisplayProjection.Cell right = SonarDisplayProjection.echoCell(9, 50,
                layout.bearings()[1], layout.resolutions()[1], 120, false);
        assertEquals(left.innerRight().x(), right.innerLeft().x(), 1.0e-7);
        assertEquals(left.innerRight().z(), right.innerLeft().z(), 1.0e-7);
        assertEquals(left.outerRight().x(), right.outerLeft().x(), 1.0e-7);
        assertEquals(left.outerRight().z(), right.outerLeft().z(), 1.0e-7);
    }

    @Test
    void indexedAngularOverlapSelectionMatchesQuadraticReference() {
        Random random = new Random(0xEC_A0_51L);
        for (int sample = 0; sample < 200; sample++) {
            List<SonarDisplayLayout.AngularPoint> points = new java.util.ArrayList<>();
            for (int index = 0; index < 120; index++) {
                points.add(new SonarDisplayLayout.AngularPoint(
                        random.nextInt(8),
                        random.nextInt(-120, 121) * 0.25f,
                        random.nextFloat(0.05f, 3f),
                        random.nextFloat()));
            }
            SonarDisplayLayout.AngularLayout layout =
                    SonarDisplayLayout.readableAngularLayout(points, 2.4f, true);
            assertArrayEquals(quadraticVisible(points, layout.resolutions(), true), layout.visible(),
                    "indexed overlap selection changed greedy layout at sample " + sample);
        }
    }

    @Test
    void oldPixelLifetimeMinusOneClearsOnlyWhenNewScanStarts() {
        assertEquals(1, SonarDisplayLayout.oldFrameAlpha(-1, 100, false, 0, true), 1.0e-6);
        assertEquals(0, SonarDisplayLayout.oldFrameAlpha(-1, 0, true, 0, true), 1.0e-6);
    }

    @Test
    void oldPixelLifetimeZeroCrossfadesAtNewSweepFront() {
        assertEquals(1, SonarDisplayLayout.oldFrameAlpha(0, 100, false, 0, true), 1.0e-6);
        assertEquals(0.75f, SonarDisplayLayout.oldFrameAlpha(0, 100, true, 0.25f, true), 1.0e-6);
        assertEquals(0, SonarDisplayLayout.oldFrameAlpha(0, 100, true, 1, true), 1.0e-6);
        assertEquals(0, SonarDisplayLayout.oldFrameAlpha(0, 0, true, 0, false), 1.0e-6);
    }

    @Test
    void positiveOldPixelLifetimeFadesByConfiguredTicks() {
        assertEquals(1, SonarDisplayLayout.oldFrameAlpha(80, 0, true, 1, false), 1.0e-6);
        assertEquals(0.5f, SonarDisplayLayout.oldFrameAlpha(80, 40, true, 1, false), 1.0e-6);
        assertEquals(0, SonarDisplayLayout.oldFrameAlpha(80, 80, false, 0, false), 1.0e-6);
    }

    @Test
    void latestCompletedFrameWaitsForTheNextSweepBeforeClearing() {
        assertEquals(1, SonarDisplayLayout.oldFrameAlpha(60, 200,
                false, 0, true), 1.0e-6);
        assertEquals(1, SonarDisplayLayout.oldFrameAlpha(60, 200,
                true, 0, true), 1.0e-6);
        assertEquals(0.5f, SonarDisplayLayout.oldFrameAlpha(60, 200,
                true, 0.5f, true), 1.0e-6);
        assertEquals(0, SonarDisplayLayout.oldFrameAlpha(60, 200,
                true, 1, true), 1.0e-6);
    }

    @Test
    void revealSpeedTracksConfiguredBlocksPerTick() {
        assertEquals(1f / 128, SonarDisplayLayout.revealProgressPerTick(1, 128), 1.0e-6);
        assertEquals(10f / 128, SonarDisplayLayout.revealProgressPerTick(10, 128), 1.0e-6);
        assertEquals(16f / 128, SonarDisplayLayout.revealProgressPerTick(16, 128), 1.0e-6);
    }

    @Test
    void revealAnimationCatchesUpAfterAClientFrameStall() {
        assertEquals(0.5f, SonarDisplayLayout.advanceRevealProgress(
                0, 1, 10, 0.05f), 1.0e-6);
        assertEquals(1, SonarDisplayLayout.advanceRevealProgress(
                0, 1, 30, 0.05f), 1.0e-6);
        assertEquals(0.25f, SonarDisplayLayout.advanceRevealProgress(
                0, 0.25f, 30, 0.05f), 1.0e-6);
    }

    @Test
    void fullRefreshCanImmediatelyHideOnlyOlderEpochs() {
        assertTrue(SonarDisplayLayout.hideOldFrameAfterFullRefresh(true, 4, 5));
        assertFalse(SonarDisplayLayout.hideOldFrameAfterFullRefresh(true, 5, 5));
        assertFalse(SonarDisplayLayout.hideOldFrameAfterFullRefresh(false, 4, 5));
    }

    @Test
    void displayEchoRangeCellsStayOnBinCenters() {
        int range = 128;
        float first = SonarDisplayProjection.rangeBinCenter(10, range);
        float second = SonarDisplayProjection.rangeBinCenter(11, range);
        float gappedHalfWidth = SonarDisplayProjection.echoRangeHalfWidth(range, true);

        assertEquals(1f / range, second - first, 1.0e-6);
        assertTrue(first + gappedHalfWidth < second - gappedHalfWidth);
    }

    @Test
    void voxelDdaVisitsEveryCellInOrderWithoutSkipping() {
        List<SonarVoxelDda.Cell> cells =
                SonarVoxelDda.trace(0.5, 4.5, 0.5, 1, 0, 0, 0, 3.2);
        assertEquals(List.of(0, 1, 2, 3),
                cells.stream().map(SonarVoxelDda.Cell::x).toList());
        assertTrue(cells.get(1).distance() < cells.get(2).distance());
        assertEquals(1, cells.get(1).incidence(), 1.0e-6);
    }

    @Test
    void voxelDdaHandlesDiagonalAndNegativeDirections() {
        List<SonarVoxelDda.Cell> cells =
                SonarVoxelDda.trace(3.5, 1.5, 3.5, -Math.sqrt(0.5), 0,
                        -Math.sqrt(0.5), 0, 4);
        assertEquals(3, cells.getFirst().x());
        assertEquals(3, cells.getFirst().z());
        assertTrue(cells.getLast().x() <= 1);
        assertTrue(cells.getLast().z() <= 1);
    }

    @Test
    void sectionCollectionCoversEveryCrossedSection() {
        Set<Section> sections = traceSections(15.5, 15.5, 0.5,
                Math.sqrt(0.5), Math.sqrt(0.5), 0, 0, 3);

        assertTrue(sections.contains(new Section(0, 0, 0)));
        assertTrue(sections.contains(new Section(1, 0, 0)));
        assertTrue(sections.contains(new Section(1, 0, 1)));
        assertEquals(voxelSections(15.5, 15.5, 0.5,
                Math.sqrt(0.5), Math.sqrt(0.5), 0, 0, 3), sections);
    }

    @Test
    void sectionDdaMatchesVoxelDdaAcrossRandomRays() {
        Random random = new Random(0x5E_C710L);
        for (int sample = 0; sample < 500; sample++) {
            double originX = random.nextDouble(-96, 96);
            double originY = random.nextDouble(-96, 96);
            double originZ = random.nextDouble(-96, 96);
            double directionX = random.nextDouble(-1, 1);
            double directionY = random.nextDouble(-1, 1);
            double directionZ = random.nextDouble(-1, 1);
            double length = Math.sqrt(directionX * directionX
                    + directionY * directionY + directionZ * directionZ);
            if (length < 1.0e-6) {
                sample--;
                continue;
            }
            directionX /= length;
            directionY /= length;
            directionZ /= length;
            double start = random.nextDouble(0, 24);
            double end = start + random.nextDouble(0.01, 128);

            assertEquals(voxelSections(originX, originY, originZ,
                            directionX, directionY, directionZ, start, end),
                    traceSections(originX, originY, originZ,
                            directionX, directionY, directionZ, start, end),
                    "section mismatch for random sample " + sample);
        }
    }

    @Test
    void sectionDdaMatchesVoxelDdaAtSectionBoundaries() {
        assertSectionTraversalMatches(16, 16, 16, -1, 0, 0, 0, 48);
        assertSectionTraversalMatches(-16, -16, -16, 0, -1, 0, 0, 48);
        double diagonal = Math.sqrt(1.0 / 3.0);
        assertSectionTraversalMatches(15.5, 15.5, 15.5,
                diagonal, diagonal, diagonal, 0, 64);
        assertSectionTraversalMatches(-15.5, -15.5, -15.5,
                -diagonal, -diagonal, -diagonal, 8, 72);
    }

    @Test
    void traceRangesCoverEveryRayOnce() {
        List<SonarTracePlan.Range> ranges = SonarTracePlan.splitRanges(10, 3);
        boolean[] covered = new boolean[10];
        for (SonarTracePlan.Range range : ranges) {
            for (int i = range.startInclusive(); i < range.endExclusive(); i++) {
                assertFalse(covered[i], "ray index covered twice: " + i);
                covered[i] = true;
            }
        }
        for (boolean value : covered) assertTrue(value);
        assertEquals(3, ranges.size());
    }

    @Test
    void staleBatchResultsAreRejected() {
        assertTrue(SonarTracePlan.isCurrentBatch(3, 7, 3, 7));
        assertFalse(SonarTracePlan.isCurrentBatch(4, 7, 3, 7));
        assertFalse(SonarTracePlan.isCurrentBatch(3, 8, 3, 7));
    }

    @Test
    void hitRefinementOffsetShrinksWithDistance() {
        assertTrue(SonarAdaptiveTracePlan.refinementOffsetDegrees(20)
                > SonarAdaptiveTracePlan.refinementOffsetDegrees(80));
    }

    @Test
    void hitRefinementCreatesConfiguredRaysAndBacktracks() {
        SonarAdaptiveTracePlan.Settings settings = SonarAdaptiveTracePlan.settings(128, 120, 5, 5, 4, 5);
        SonarAdaptiveTracePlan.Leaf root = new SonarAdaptiveTracePlan.Leaf(2, 2, 0, 0);

        List<SonarAdaptiveTracePlan.Leaf> refinements =
                SonarAdaptiveTracePlan.refinementsForHit(root, 70, settings);
        assertEquals(4, refinements.size());
        assertTrue(refinements.stream().allMatch(SonarAdaptiveTracePlan.Leaf::refinement));
        assertTrue(refinements.stream().allMatch(leaf -> leaf.refinementStartDistance() == 65
                && leaf.refinementEndDistance() == 128));
        assertTrue(refinements.stream().anyMatch(leaf -> leaf.bearingOffset() != 0));
        assertTrue(refinements.stream().anyMatch(leaf -> leaf.pitchOffset() != 0));
    }

    @Test
    void hitRefinementCanContinuePastWideAngleForwardDepth() {
        SonarAdaptiveTracePlan.Settings settings = SonarAdaptiveTracePlan.settings(128, 120, 5, 5, 4, 5);
        SonarAdaptiveTracePlan.Leaf nearEdge = new SonarAdaptiveTracePlan.Leaf(3, 2, 0, 0);

        List<SonarAdaptiveTracePlan.Leaf> horizontal = SonarAdaptiveTracePlan.refinementsForHit(
                nearEdge, 70, settings).stream()
                .filter(leaf -> leaf.bearingOffset() != 0)
                .toList();

        SonarAdaptiveTracePlan.Leaf outward = horizontal.stream()
                .filter(leaf -> SonarAdaptiveTracePlan.bearing(leaf, settings)
                        > SonarAdaptiveTracePlan.bearing(nearEdge, settings))
                .findFirst()
                .orElseThrow();
        assertTrue(outward.refinementEndDistance() > 70);
        assertEquals(128, outward.refinementEndDistance(), 1.0e-6);
    }

    @Test
    void hitRefinementClampsToSectorAndPitchEdges() {
        SonarAdaptiveTracePlan.Settings settings = SonarAdaptiveTracePlan.settings(128, 120, 5, 5, 16, 5);

        List<SonarAdaptiveTracePlan.Leaf> leftEdge = SonarAdaptiveTracePlan.refinementsForHit(
                new SonarAdaptiveTracePlan.Leaf(0, 2, 0, 0), 1, settings);
        assertEquals(16, leftEdge.size());
        assertTrue(leftEdge.stream().allMatch(leaf ->
                SonarAdaptiveTracePlan.bearing(leaf, settings) >= -60
                        && SonarAdaptiveTracePlan.bearing(leaf, settings) <= 60));

        List<SonarAdaptiveTracePlan.Leaf> bottomEdge = SonarAdaptiveTracePlan.refinementsForHit(
                new SonarAdaptiveTracePlan.Leaf(2, 0, 0, 0), 1, settings);
        assertEquals(16, bottomEdge.size());
        assertTrue(bottomEdge.stream().allMatch(leaf ->
                SonarAdaptiveTracePlan.pitch(leaf, settings) >= SonarKinematics.MIN_PITCH
                        && SonarAdaptiveTracePlan.pitch(leaf, settings) <= SonarKinematics.MAX_PITCH));
    }

    @Test
    void sideScanRefinementStaysInsideItsSideAndVerticalFan() {
        SonarAdaptiveTracePlan.Settings settings = SonarAdaptiveTracePlan.settings(
                128, 3, 90, 5, 9, 16, 5);
        SonarAdaptiveTracePlan.Leaf rightBottom =
                new SonarAdaptiveTracePlan.Leaf(0, 0, 90,
                        SideScanGeometry.CENTER_PITCH_DEGREES);

        List<SonarAdaptiveTracePlan.Leaf> refinements =
                SonarAdaptiveTracePlan.sideScanRefinementsForHit(rightBottom, 20, settings);
        assertEquals(16, refinements.size());
        assertTrue(refinements.stream().allMatch(leaf -> {
            double bearing = SonarAdaptiveTracePlan.bearing(leaf, settings);
            double pitch = SonarAdaptiveTracePlan.pitch(leaf, settings);
            return bearing >= 88.5 && bearing <= 91.5 && pitch >= -90 && pitch <= 0;
        }));
    }

    @Test
    void hitRefinementCanBeDisabledAndDoesNotRefineAgain() {
        SonarAdaptiveTracePlan.Leaf root = new SonarAdaptiveTracePlan.Leaf(2, 2, 0, 0);
        SonarAdaptiveTracePlan.Settings disabled = SonarAdaptiveTracePlan.settings(128, 120, 5, 5, 4, 0);
        assertTrue(SonarAdaptiveTracePlan.refinementsForHit(root, 70, disabled).isEmpty());

        SonarAdaptiveTracePlan.Settings enabled = SonarAdaptiveTracePlan.settings(128, 120, 5, 5, 4, 5);
        SonarAdaptiveTracePlan.Leaf child =
                SonarAdaptiveTracePlan.refinementsForHit(root, 70, enabled).getFirst();
        assertTrue(SonarAdaptiveTracePlan.refinementsForHit(child, 70, enabled).isEmpty());
    }

    @Test
    void hitRefinementSupportsFourEightAndSixteenAdditionalRays() {
        SonarAdaptiveTracePlan.Leaf root = new SonarAdaptiveTracePlan.Leaf(2, 2, 0, 0);
        for (int count : new int[] {4, 8, 16}) {
            SonarAdaptiveTracePlan.Settings settings =
                    SonarAdaptiveTracePlan.settings(128, 120, 5, 5, count, 5);
            assertEquals(count, SonarAdaptiveTracePlan.refinementsForHit(root, 70, settings).size());
        }
    }

    @Test
    void hitRefinementTraceStartsNearHitAndCanRunForward() {
        SonarAdaptiveTracePlan.Settings settings = SonarAdaptiveTracePlan.settings(128, 120, 5, 5, 4, 5);
        SonarAdaptiveTracePlan.Leaf root = new SonarAdaptiveTracePlan.Leaf(2, 2, 0, 0);
        SonarAdaptiveTracePlan.Leaf child =
                SonarAdaptiveTracePlan.refinementsForHit(root, 70, settings).getFirst();

        assertEquals(10, SonarAdaptiveTracePlan.nextTraceEnd(0, settings, root, 10), 1.0e-6);
        assertEquals(75, SonarAdaptiveTracePlan.nextTraceEnd(65, settings, child, 10), 1.0e-6);
        assertEquals(128, child.refinementEndDistance(), 1.0e-6);
    }

    @Test
    void refinedEchoFootprintCanBeSmallerThanBasePixel() {
        float base = SonarDisplayProjection.echoAngularHalfWidth(120, 51, true);
        float refined = SonarDisplayProjection.echoAngularHalfWidth(
                (float) SonarAdaptiveTracePlan.refinementOffsetDegrees(70), true);
        assertTrue(refined < base);
    }

    @Test
    void displayEchoAngularCellsCanShrinkToNeighbourSpacing() {
        float spacing = 0.5f;
        float halfWidth = SonarDisplayProjection.echoAngularHalfWidth(spacing, true);
        assertTrue(halfWidth * 2 < spacing);
    }

    @Test
    void echoKeyKeepsDifferentBearingsInSameBeamAndBinSeparate() {
        SonarScanManager.EchoKey center = new SonarScanManager.EchoKey(2, 70, 0);
        SonarScanManager.EchoKey refined = new SonarScanManager.EchoKey(2, 70, 650);
        assertNotEquals(center, refined);
    }

    @Test
    void sableConeCullRejectsBoxesThatCannotIntersectScan() {
        assertFalse(boxMayIntersectCone(0, 0, 130, 1, 1, 131, 60, 32));
        assertFalse(boxMayIntersectCone(-1, -1, -20, 1, 1, -18, 60, 128));
        assertFalse(boxMayIntersectCone(30, -1, 20, 31, 1, 21, 30, 128));
        assertFalse(boxMayIntersectCone(-1, 20, 40, 1, 21, 41, 60, 128));
    }

    @Test
    void sableConeCullKeepsBoxesInsideScan() {
        assertTrue(boxMayIntersectCone(-1, -1, 20, 1, 1, 22, 60, 128));
    }

    @Test
    void nearestHitSelectionPrefersCloserSableHit() {
        assertTrue(SonarTraceSupport.firstDistanceBeatsSecond(true, 8, true, 12));
        assertFalse(SonarTraceSupport.firstDistanceBeatsSecond(true, 12, true, 8));
        assertTrue(SonarTraceSupport.firstDistanceBeatsSecond(true, 8, false, 128));
        assertFalse(SonarTraceSupport.firstDistanceBeatsSecond(false, 8, false, 128));
    }

    private record Section(int chunkX, int chunkZ, int sectionY) {
        static Section containing(int x, int y, int z) {
            return new Section(Math.floorDiv(x, 16), Math.floorDiv(z, 16), Math.floorDiv(y, 16));
        }
    }

    private static boolean[] quadraticVisible(List<SonarDisplayLayout.AngularPoint> points,
                                              float[] resolutions, boolean pointGaps) {
        boolean[] visible = new boolean[points.size()];
        float halfFill = pointGaps ? 0.42f : 0.50f;
        java.util.Map<Integer, List<Integer>> rows = new java.util.HashMap<>();
        for (int index = 0; index < points.size(); index++) {
            rows.computeIfAbsent(points.get(index).rangeBin(), ignored -> new java.util.ArrayList<>()).add(index);
        }
        for (List<Integer> row : rows.values()) {
            row.sort(java.util.Comparator.<Integer>comparingDouble(
                            index -> points.get(index).strength()).reversed()
                    .thenComparingInt(Integer::intValue));
            List<Integer> accepted = new java.util.ArrayList<>();
            for (int candidate : row) {
                boolean overlaps = false;
                for (int other : accepted) {
                    float gap = Math.abs(points.get(candidate).bearingDegrees()
                            - points.get(other).bearingDegrees());
                    float required = halfFill * (resolutions[candidate] + resolutions[other]);
                    if (gap + 1.0e-4f < required) {
                        overlaps = true;
                        break;
                    }
                }
                if (!overlaps) {
                    visible[candidate] = true;
                    accepted.add(candidate);
                }
            }
        }
        return visible;
    }

    private static Set<Section> traceSections(double originX, double originY, double originZ,
                                              double directionX, double directionY, double directionZ,
                                              double startDistance, double endDistance) {
        Set<Section> sections = new HashSet<>();
        SonarSectionDda.traceSections(originX, originY, originZ,
                directionX, directionY, directionZ, startDistance, endDistance,
                (x, y, z) -> sections.add(new Section(x, z, y)));
        return sections;
    }

    private static Set<Section> voxelSections(double originX, double originY, double originZ,
                                              double directionX, double directionY, double directionZ,
                                              double startDistance, double endDistance) {
        Set<Section> sections = new HashSet<>();
        SonarVoxelDda.traceCells(originX, originY, originZ,
                directionX, directionY, directionZ, startDistance, endDistance,
                (x, y, z, distance, incidence) -> {
                    sections.add(Section.containing(x, y, z));
                    return true;
                });
        return sections;
    }

    private static void assertSectionTraversalMatches(double originX, double originY, double originZ,
                                                      double directionX, double directionY, double directionZ,
                                                      double startDistance, double endDistance) {
        assertEquals(voxelSections(originX, originY, originZ,
                        directionX, directionY, directionZ, startDistance, endDistance),
                traceSections(originX, originY, originZ,
                        directionX, directionY, directionZ, startDistance, endDistance));
    }

    private static double bearing(SonarAdaptiveTracePlan.Leaf leaf, SonarAdaptiveTracePlan.Settings settings) {
        return SonarAdaptiveTracePlan.baseBearing(leaf.beam(), settings) + leaf.bearingOffset();
    }

    private static double pitch(SonarAdaptiveTracePlan.Leaf leaf, SonarAdaptiveTracePlan.Settings settings) {
        return SonarAdaptiveTracePlan.basePitch(leaf.vertical(), settings) + leaf.pitchOffset();
    }

    private static boolean boxMayIntersectCone(double minX, double minY, double minZ,
                                               double maxX, double maxY, double maxZ,
                                               int sector, int range) {
        return SonarTraceSupport.boxMayIntersectCone(minX, minY, minZ, maxX, maxY, maxZ,
                0, 0, 0,
                0, 0, 1,
                1, 0, 0,
                0, 1, 0,
                sector, range);
    }
}
