package org.rassvet.create_echo_radars.content.sonar;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarEntityVisibilityCacheTest {
    private static final SonarEntityVisibilityCache.Cell SONAR =
            new SonarEntityVisibilityCache.Cell(0, 10, 0);
    private static final SonarEntityVisibilityCache.Cell ENTITY =
            new SonarEntityVisibilityCache.Cell(20, 10, 0);

    @Test
    void unchangedEndpointsReuseExactlyOneRay() {
        SonarEntityVisibilityCache cache = new SonarEntityVisibilityCache(100);
        AtomicInteger rays = new AtomicInteger();

        assertTrue(cache.resolve("mob", SONAR, ENTITY, 10, () -> {
            rays.incrementAndGet();
            return true;
        }));
        assertTrue(cache.resolve("mob", SONAR, ENTITY, 20, () -> {
            rays.incrementAndGet();
            return false;
        }));
        assertEquals(1, rays.get());
    }

    @Test
    void movingEitherEndpointToAnotherBlockRetraces() {
        SonarEntityVisibilityCache cache = new SonarEntityVisibilityCache(100);
        AtomicInteger rays = new AtomicInteger();
        cache.resolve("mob", SONAR, ENTITY, 0, () -> {
            rays.incrementAndGet();
            return true;
        });

        SonarEntityVisibilityCache.Cell movedEntity = new SonarEntityVisibilityCache.Cell(21, 10, 0);
        assertFalse(cache.resolve("mob", SONAR, movedEntity, 10, () -> {
            rays.incrementAndGet();
            return false;
        }));
        SonarEntityVisibilityCache.Cell movedSonar = new SonarEntityVisibilityCache.Cell(1, 10, 0);
        assertTrue(cache.resolve("mob", movedSonar, movedEntity, 20, () -> {
            rays.incrementAndGet();
            return true;
        }));
        assertEquals(3, rays.get());
    }

    @Test
    void stationaryRayEventuallyRefreshesForWorldChanges() {
        SonarEntityVisibilityCache cache = new SonarEntityVisibilityCache(100);
        AtomicInteger rays = new AtomicInteger();
        assertTrue(cache.resolve("mob", SONAR, ENTITY, 0, () -> {
            rays.incrementAndGet();
            return true;
        }));
        assertFalse(cache.resolve("mob", SONAR, ENTITY, 100, () -> {
            rays.incrementAndGet();
            return false;
        }));
        assertEquals(2, rays.get());
    }

    @Test
    void unusedEntriesArePruned() {
        SonarEntityVisibilityCache cache = new SonarEntityVisibilityCache(100);
        cache.resolve("mob", SONAR, ENTITY, 0, () -> true);
        cache.prune(199);
        assertEquals(1, cache.size());
        cache.prune(200);
        assertEquals(0, cache.size());
    }
}
