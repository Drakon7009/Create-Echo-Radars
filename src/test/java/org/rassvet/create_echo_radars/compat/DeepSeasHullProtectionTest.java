package org.rassvet.create_echo_radars.compat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class DeepSeasHullProtectionTest {
    @AfterEach
    void reset() {
        DeepSeasHullProtection.resetForTests();
    }

    @Test
    void replacesHullPropertyWithPressureImmuneValues() {
        Optional<?> result = DeepSeasHullProtection.makePressureImmune(
                Optional.of(new TestHullProperty(12, 0.75f)));

        TestHullProperty property = (TestHullProperty) result.orElseThrow();
        assertEquals(Integer.MAX_VALUE, property.maxWaterDepth());
        assertEquals(0.0f, property.implosionChance());
    }

    @Test
    void reusesTheImmutablePressureImmuneProperty() {
        Optional<?> first = DeepSeasHullProtection.makePressureImmune(
                Optional.of(new TestHullProperty(12, 0.75f)));
        Optional<?> second = DeepSeasHullProtection.makePressureImmune(
                Optional.of(new TestHullProperty(30, 0.25f)));

        assertSame(first.orElseThrow(), second.orElseThrow());
    }

    @Test
    void leavesAnEmptyPropertyUnchanged() {
        Optional<?> empty = Optional.empty();
        assertSame(empty, DeepSeasHullProtection.makePressureImmune(empty));
    }

    @Test
    void increasesSonarDepthAndPreservesImplosionChance() {
        Optional<?> result = DeepSeasHullProtection.increaseSonarDepth(
                Optional.of(new TestHullProperty(46, 0.35f)));
        TestHullProperty property = (TestHullProperty) result.orElseThrow();
        assertEquals(512, property.maxWaterDepth());
        assertEquals(0.35f, property.implosionChance());
    }

    @Test
    void preservesHigherConfiguredDepth() {
        Optional<?> original = Optional.of(new TestHullProperty(800, 0.2f));
        assertSame(original, DeepSeasHullProtection.increaseSonarDepth(original));
    }

    public record TestHullProperty(int maxWaterDepth, float implosionChance) {}
}
