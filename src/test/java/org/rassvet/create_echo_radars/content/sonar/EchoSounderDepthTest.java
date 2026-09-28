package org.rassvet.create_echo_radars.content.sonar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EchoSounderDepthTest {
    @Test
    void selectsTheMostVerticalReturnAndProjectsItsDepth() {
        SonarReturn angled = new SonarReturn(0, 20, 30, 0, 0.25f, 1, 0, 0);
        SonarReturn straight = new SonarReturn(1, 40, 0, 0, 0.5f, 1, 0, 0);
        assertEquals(50, EchoSounderDepth.fromReturns(List.of(angled, straight), 100), 0.001f);
        assertEquals(25 * Math.cos(Math.toRadians(30)),
                EchoSounderDepth.fromReturns(List.of(angled), 100), 0.001f);
    }

    @Test
    void keepsTheLatestReadingWhileTheNextPingHasNoReturn() {
        SonarFrame measured = new SonarFrame(1, 1, 2, true,
                List.of(new SonarReturn(0, 30, 0, 0, 0.3f, 1, 0, 0)));
        SonarFrame scanning = new SonarFrame(2, 3, 0, false, List.of());
        SonarFrame completedWithoutEcho = new SonarFrame(2, 3, 4, true, List.of());
        assertEquals(30, EchoSounderDepth.fromFrames(List.of(measured, scanning), 100), 0.001f);
        assertTrue(Float.isNaN(EchoSounderDepth.fromFrames(
                List.of(measured, completedWithoutEcho), 100)));
        assertTrue(Float.isNaN(EchoSounderDepth.fromFrames(List.of(scanning), 100)));
    }

    @Test
    void angularReturnsColorDifferentPartsOfTheSquare() {
        SonarReturn left = new SonarReturn(0, 20, -20, 0, 0.25f, 1, 10, 10);
        SonarReturn right = new SonarReturn(1, 60, 20, 0, 0.75f, 1, 10, 10);
        SonarFrame frame = new SonarFrame(1, 1, 2, true, List.of(left, right));
        EchoSounderDepth.Surface surface = EchoSounderDepth.surface(
                List.of(frame), 100, 60, 60, 12);
        assertTrue(surface.depthAt(2, 6) < surface.depthAt(10, 6));
        assertTrue(Float.isNaN(surface.depthAt(6, 2)));
        SonarDisplayLayout.Area map = EchoSounderDepth.mapArea(
                new SonarDisplayLayout.Area(0, 1, 0, 1));
        assertEquals(surface.depthAt(2, 6), surface.depthAtPosition(map,
                map.left() + map.width() * 2.5 / 12,
                map.bottom() + map.height() * 6.5 / 12), 0.001f);
        assertTrue(Float.isNaN(surface.depthAtPosition(map,
                map.right() + 0.01, map.centerZ())));
    }

    @Test
    void farRefinementCannotReplaceTheFirstGratePixel() {
        SonarReturn grate = new SonarReturn(0, 12, 0, 0, 0.15f, 0.8f, 12, 12);
        SonarReturn behindGrate = new SonarReturn(1, 72, 0, 0, 0.75f, 1, 2, 2);
        for (List<SonarReturn> returns : List.of(
                List.of(grate, behindGrate), List.of(behindGrate, grate))) {
            SonarFrame frame = new SonarFrame(1, 1, 2, true, returns);
            EchoSounderDepth.Surface surface = EchoSounderDepth.surface(
                    List.of(frame), 100, 60, 60, 12);
            assertEquals(15, surface.depthAt(6, 6), 0.001f);
        }
    }

    @Test
    void entityAnglesProjectIntoTheSameSquareAsEchoes() {
        SonarDisplayProjection.Point center = EchoSounderDepth.angularPoint(0, 0, 50, 50);
        assertEquals(0, center.x(), 0.0001);
        assertEquals(0, center.z(), 0.0001);
        SonarDisplayProjection.Point corner = EchoSounderDepth.angularPoint(25, -25, 50, 50);
        assertEquals(1, corner.x(), 0.0001);
        assertEquals(-1, corner.z(), 0.0001);
        assertTrue(EchoSounderDepth.angularPoint(26, 0, 50, 50) == null);
    }

}
