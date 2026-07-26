package org.rassvet.create_echo_radars.content.glass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.rassvet.create_echo_radars.CreateEchoRadars;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public interface SonarGlass {
    Map<Class<?>, Optional<Method>> COPYCAT_MATERIAL_ACCESSORS =
            new ConcurrentHashMap<>();

    static boolean isGlass(BlockState state) {
        return state.getBlock() instanceof SonarGlass;
    }

    /**
     * Copycats stores the applied block state in its block entity. Keeping this
     * lookup reflective makes Copycats optional while still allowing every
     * server/client glass query to see the wrapped sonar-glass material.
     */
    static boolean isGlass(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isGlass(state)) return true;
        if (!state.hasBlockEntity()) return false;
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace()
                .equals("copycats")) return false;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return false;
        Optional<Method> accessor = COPYCAT_MATERIAL_ACCESSORS.computeIfAbsent(
                blockEntity.getClass(), SonarGlass::findMaterialAccessor);
        if (accessor.isEmpty()) return false;
        try {
            Object material = accessor.get().invoke(blockEntity);
            return material instanceof BlockState materialState
                    && isGlass(materialState);
        } catch (ReflectiveOperationException exception) {
            CreateEchoRadars.LOGGER.debug(
                    "Could not read Copycats material at {}", pos, exception);
            return false;
        }
    }

    private static Optional<Method> findMaterialAccessor(Class<?> type) {
        try {
            return Optional.of(type.getMethod("getMaterial"));
        } catch (NoSuchMethodException ignored) {
            return Optional.empty();
        }
    }
}
