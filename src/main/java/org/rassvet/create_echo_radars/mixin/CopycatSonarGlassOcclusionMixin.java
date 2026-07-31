package org.rassvet.create_echo_radars.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.neoforge.CopycatRenderContextNeoForge;
import com.copycatsplus.copycats.foundation.copycat.model.neoforge.CopycatModelNeoForge;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.content.glass.SonarGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Copycats normally removes every quad on a face hidden by an adjacent block.
 * Sonar glass needs more precise handling because its model contains two
 * independent layers: the connected frame and the translucent spot filter.
 */
@Pseudo
@Mixin(targets =
        "com.copycatsplus.copycats.foundation.copycat.model.neoforge.CopycatModelNeoForge",
        remap = false)
public abstract class CopycatSonarGlassOcclusionMixin {
    private static final ResourceLocation SONAR_GLASS_FILTER =
            ResourceLocation.fromNamespaceAndPath(
                    CreateEchoRadars.MOD_ID, "block/sonar_glass_filter");
    private static final Map<CopycatModelNeoForge.OcclusionData,
            EnumSet<Direction>> FILTER_ONLY_FACES =
            Collections.synchronizedMap(new WeakHashMap<>());

    @WrapOperation(
            method = "gatherOcclusionData(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lcom/copycatsplus/copycats/foundation/copycat/model/neoforge/CopycatModelNeoForge$OcclusionData;Lcom/copycatsplus/copycats/foundation/copycat/ICopycatBlock;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;hidesNeighborFace(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"))
    private boolean createEchoRadars$markNativeGlassOcclusion(
            BlockState neighbourState,
            BlockGetter world,
            BlockPos neighbourPos,
            BlockState material,
            Direction oppositeFace,
            Operation<Boolean> original,
            @Local(argsOnly = true)
            CopycatModelNeoForge.OcclusionData occlusionData) {
        boolean hidden = original.call(neighbourState, world, neighbourPos,
                material, oppositeFace);
        if (hidden && SonarGlass.isGlass(neighbourState)
                && neighbourState.getBlock() == material.getBlock()) {
            synchronized (FILTER_ONLY_FACES) {
                FILTER_ONLY_FACES.computeIfAbsent(occlusionData,
                                ignored -> EnumSet.noneOf(Direction.class))
                        .add(oppositeFace.getOpposite());
            }
        }
        return hidden;
    }

    @WrapOperation(
            method = "getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/copycatsplus/copycats/foundation/copycat/model/neoforge/CopycatModelNeoForge$OcclusionData;isOccluded(Lnet/minecraft/core/Direction;)Z"))
    private boolean createEchoRadars$hideOnlyCoveredFilter(
            CopycatModelNeoForge.OcclusionData occlusionData,
            Direction face,
            Operation<Boolean> original,
            @Local(name = "material") BlockState material,
            @Local(name = "croppedQuad")
            CopycatRenderContextNeoForge.CopycatBakedQuad croppedQuad) {
        boolean occluded = original.call(occlusionData, face);
        if (!occluded || !SonarGlass.isGlass(material)
                || !isFilterOnlyFace(occlusionData, face)) return occluded;

        /*
         * Keep the copper/iron frame visible through the adjacent full glass.
         * Only the white spot/filter texture belongs to the internal surface
         * and must disappear where that surface is physically covered.
         */
        BakedQuad quad = croppedQuad;
        return SONAR_GLASS_FILTER.equals(
                quad.getSprite().contents().name());
    }

    private static boolean isFilterOnlyFace(
            CopycatModelNeoForge.OcclusionData occlusionData,
            Direction face) {
        if (face == null) return false;
        synchronized (FILTER_ONLY_FACES) {
            EnumSet<Direction> faces = FILTER_ONLY_FACES.get(occlusionData);
            return faces != null && faces.contains(face);
        }
    }
}
