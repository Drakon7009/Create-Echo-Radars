package org.rassvet.create_echo_radars.content.sonar;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual renderer history, including packet and animation resets. */
class SonarMonitorHistoryTest {
    @Test
    void movementAndEmptyUpdatesDoNotClearThePreviousSweep() throws Exception {
        Object history = history();
        SonarFrame completed = frame(1, 10, true);
        update(history, List.of(completed), Vec3.ZERO);
        SonarRevealAnimation state = reveal(history, completed, 0);
        reveal(history, completed, 2);
        call(history, "markFullyRefreshed", new Class<?>[]{long.class}, 1L);

        for (int update = 0; update < 30; update++) {
            update(history, List.of(frame(2, 20, false)), new Vec3(update * 0.01, 0, 0));
            assertEquals(1L, call(history, "fullyRefreshedEpoch", new Class<?>[]{}));
            assertTrue(frames(history).contains(completed));
            assertSame(state, reveal(history, completed, 3 + update));
            assertEquals(1, state.progress());
        }
        update(history, List.of(), new Vec3(0.3, 0, 0));
        assertTrue(frames(history).contains(completed));
        assertEquals(1L, call(history, "fullyRefreshedEpoch", new Class<?>[]{}));
    }

    @Test
    void reusedEpochStartsANewAnimationInsteadOfInstantlyClearingTheScreen() throws Exception {
        Object history = history();
        SonarFrame oldSession = frame(1, 10, true);
        update(history, List.of(oldSession), Vec3.ZERO);
        SonarRevealAnimation old = reveal(history, oldSession, 0);
        reveal(history, oldSession, 2);
        call(history, "markFullyRefreshed", new Class<?>[]{long.class}, 1L);

        SonarFrame newSession = frame(1, 1000, false);
        update(history, List.of(newSession), Vec3.ZERO);
        SonarRevealAnimation fresh = reveal(history, newSession, 1000);
        assertNotSame(old, fresh);
        assertFalse(fresh.fullyRevealed());
        assertEquals(0, fresh.progress());
        assertEquals(Long.MIN_VALUE, call(history, "fullyRefreshedEpoch", new Class<?>[]{}));
    }

    @Test
    void repeatedSweepsKeepTheirAnimationsAcrossLongPauses() throws Exception {
        Object history = history();
        for (long epoch = 1; epoch <= 12; epoch++) {
            double now = epoch * 200;
            SonarFrame current = frame(epoch, epoch * 10, false);
            update(history, List.of(current), new Vec3(epoch, 0, 0));
            SonarRevealAnimation state = reveal(history, current, now);
            reveal(history, current, now + 0.5);
            assertEquals(0.5f, state.progress());
            SonarFrame completed = frame(epoch, epoch * 10, true);
            update(history, List.of(completed), new Vec3(epoch, 0, 0));
            assertSame(state, reveal(history, completed, now + 1));
            assertTrue(state.fullyRevealed());
            call(history, "markFullyRefreshed", new Class<?>[]{long.class}, epoch);
            assertSame(state, reveal(history, completed, now + 150));
            assertEquals(1, state.progress());
            assertEquals(epoch, call(history, "fullyRefreshedEpoch", new Class<?>[]{}));
        }
    }

    private static SonarFrame frame(long epoch, long started, boolean completed) {
        return new SonarFrame(epoch, started, completed ? started + 5 : 0,
                completed, completed ? 1 : 0.5f, List.of());
    }

    private static Object history() throws Exception {
        Class<?> type = Class.forName("org.rassvet.create_echo_radars.client.SonarMonitorRenderer$ClientFrameHistory");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void update(Object history, List<SonarFrame> frames, Vec3 origin) throws Exception {
        var snapshot = new SonarMonitorSnapshot(frames, 128, 128, 90, 30, 64,
                SonarType.FORWARD_LOOKING_F, 0, 0, 0, false,
                origin, new Vec3(origin.x * 0.01, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 1, 0));
        call(history, "update", new Class<?>[]{SonarMonitorSnapshot.class}, snapshot);
    }

    private static SonarRevealAnimation reveal(Object history, SonarFrame frame, double now) throws Exception {
        return (SonarRevealAnimation) call(history, "revealState",
                new Class<?>[]{SonarFrame.class, double.class, float.class}, frame, now, 1f);
    }

    @SuppressWarnings("unchecked")
    private static List<SonarFrame> frames(Object history) throws Exception {
        return (List<SonarFrame>) call(history, "frames", new Class<?>[]{});
    }

    private static Object call(Object target, String name, Class<?>[] parameters, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
