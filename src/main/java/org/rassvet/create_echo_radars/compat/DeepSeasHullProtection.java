package org.rassvet.create_echo_radars.compat;

import org.rassvet.create_echo_radars.CreateEchoRadars;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Creates an immutable Deep Seas hull property without linking against Deep Seas at runtime.
 * The sample value supplied by its getFor method tells us the nested record's actual class.
 */
public final class DeepSeasHullProtection {
    public static final int SONAR_MAX_WATER_DEPTH = 512;
    private static volatile Object immuneProperty;
    private static volatile boolean creationFailed;

    private DeepSeasHullProtection() {}

    public static Optional<?> makePressureImmune(Optional<?> original) {
        Object cached = immuneProperty;
        if (cached != null) return Optional.of(cached);
        if (creationFailed || original.isEmpty()) return original;

        synchronized (DeepSeasHullProtection.class) {
            if (immuneProperty != null) return Optional.of(immuneProperty);
            if (creationFailed) return original;
            try {
                Class<?> propertyClass = original.orElseThrow().getClass();
                Constructor<?> constructor =
                        propertyClass.getConstructor(int.class, float.class);
                immuneProperty = constructor.newInstance(Integer.MAX_VALUE, 0.0f);
                return Optional.of(immuneProperty);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                creationFailed = true;
                CreateEchoRadars.LOGGER.error(
                        "Could not make sonar glass pressure-immune in Create Deep Seas",
                        exception);
                return original;
            }
        }
    }

    public static Optional<?> increaseSonarDepth(Optional<?> original) {
        if (original.isEmpty()) return original;
        try {
            Object property = original.orElseThrow();
            Class<?> propertyClass = property.getClass();
            Method depthMethod = propertyClass.getMethod("maxWaterDepth");
            int depth = (int) depthMethod.invoke(property);
            if (depth >= SONAR_MAX_WATER_DEPTH) return original;
            Method chanceMethod = propertyClass.getMethod("implosionChance");
            float chance = (float) chanceMethod.invoke(property);
            Constructor<?> constructor = propertyClass.getConstructor(int.class, float.class);
            return Optional.of(constructor.newInstance(SONAR_MAX_WATER_DEPTH, chance));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            CreateEchoRadars.LOGGER.error("Could not increase sonar depth in Create Deep Seas", exception);
            return original;
        }
    }

    static void resetForTests() {
        immuneProperty = null;
        creationFailed = false;
    }
}
