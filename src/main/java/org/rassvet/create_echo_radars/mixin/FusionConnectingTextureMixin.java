package org.rassvet.create_echo_radars.mixin;

import com.supermartijn642.fusion.api.texture.types.connecting.predicates.ConnectionDirection;
import com.supermartijn642.fusion.api.texture.types.connecting.predicates.ConnectionPredicate;
import com.supermartijn642.fusion.texture.types.connecting.SurroundingBlockCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.rassvet.create_echo_radars.compat.fusion.SlopeFrameConnectivity;
import org.rassvet.create_echo_radars.compat.fusion.SonarSlopeFrameConnectionPredicate;
import org.rassvet.create_echo_radars.content.glass.SonarGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Extends Fusion's normal connection check without adding a custom predicate
 * identifier to model JSON. A failed/absent optional integration therefore
 * cannot invalidate the sonar-glass model.
 */
@Pseudo
@Mixin(targets =
        "com.supermartijn642.fusion.texture.types.connecting.ConnectingTextureType",
        remap = false)
public abstract class FusionConnectingTextureMixin {
    private static final String COPYCATS_NAMESPACE = "copycats";

    @Inject(method = "shouldConnect", at = @At("HEAD"), cancellable = true)
    private static void createEchoRadars$connectCopycatMaterial(
            ConnectionPredicate predicate,
            SurroundingBlockCache blocks,
            Direction face,
            ConnectionDirection direction,
            int neighborX,
            int neighborY,
            int neighborZ,
            BlockPos.MutableBlockPos mutablePos,
            CallbackInfoReturnable<Boolean> cir) {
        BlockAndTintGetter modelLevel = blocks.getLevel();
        BlockPos ownPos = blocks.getRealPos();

        /*
         * Copycats' model world is already filtered according to the shape.
         * A visible neighbor means that CT is allowed in this direction; AIR
         * means that the direction is a blocked edge such as a triangular
         * slope end. Do not unwrap this view for the topology decision.
         *
         * This also works for virtual/contraption rendering, where the real
         * block entity (and therefore getMaterial()) may be unavailable.
         */
        if (isCopycatsView(modelLevel)
                && SonarGlass.isGlass(blocks.getCenter())) {
            BlockAndTintGetter physicalLevel =
                    unwrapCopycatsView(modelLevel);
            BlockState physicalState =
                    physicalLevel.getBlockState(ownPos);

            /*
             * The frame texture has a dedicated predicate so its end-border
             * rule cannot affect the translucent glass texture or unrelated
             * Fusion materials. The baked face normal identifies the two
             * triangular caps directly, without changing UV coordinates.
             */
            if (predicate == SonarSlopeFrameConnectionPredicate.INSTANCE) {
                SlopeFrameConnectivity.Slope ownSlope = slope(
                        physicalState);
                if (ownSlope != null
                        && SlopeFrameConnectivity.isTriangularEndFace(
                                ownSlope,
                                SlopeFrameConnectivity.Face.valueOf(
                                        face.name()))) {
                    /*
                     * Never let surrounding blocks alter the frame painted on
                     * a triangular end cap. Copycats culls caps between
                     * adjacent aligned slopes, so this preserves only the two
                     * exposed outer borders and cannot create an inner grid.
                     */
                    cir.setReturnValue(false);
                    return;
                }
            }

            /*
             * A vertical slope's shape filter hides an adjacent full block
             * from the copied material model. Native sonar glass still sees
             * the Copycat, which leaves an asymmetric frame rendered only on
             * the slope. Resolve that direct native-glass neighbor against the
             * unfiltered world before consulting Copycats' shape filter.
             *
             * Air remains disconnected, so the exposed end and side textures
             * keep their triangular border.
             */
            if (isVerticalSlope(physicalState)) {
                BlockPos directNeighborPos = ownPos.offset(
                        neighborX, neighborY, neighborZ);
                BlockState directNeighbor =
                        physicalLevel.getBlockState(directNeighborPos);
                if (SonarGlass.isGlass(directNeighbor)
                        && sameMaterial(blocks.getCenter(), directNeighbor)) {
                    cir.setReturnValue(true);
                    return;
                }
            }

            BlockState visibleNeighbor = blocks.getState(
                    neighborX, neighborY, neighborZ);
            if (SonarGlass.isGlass(visibleNeighbor)) {
                cir.setReturnValue(sameMaterial(
                        blocks.getCenter(), visibleNeighbor));
                return;
            }
            if (isCopycat(visibleNeighbor)) {
                BlockPos neighborPos = ownPos.offset(
                        neighborX, neighborY, neighborZ);
                BlockState neighborMaterial = SonarGlass.material(
                        physicalLevel, neighborPos);
                if (neighborMaterial != null) {
                    cir.setReturnValue(sameMaterial(
                            blocks.getCenter(), neighborMaterial));
                    return;
                }

                /*
                 * A present Copycat block entity with no sonar material means
                 * that the Copycat contains another block (stone, gravel,
                 * etc.). It must never extend the sonar-glass texture. With
                 * virtual rendering there may be no block entity, so leave
                 * that case to Fusion's appearance-state predicate.
                 */
                if (physicalLevel.getBlockEntity(neighborPos) != null) {
                    cir.setReturnValue(false);
                }
                return;
            }
            cir.setReturnValue(false);
            return;
        }

        BlockAndTintGetter level = unwrapCopycatsView(modelLevel);
        BlockState ownPhysical = level.getBlockState(ownPos);

        boolean ownCopycat = isCopycat(ownPhysical);
        BlockPos otherPos = ownPos.offset(neighborX, neighborY, neighborZ);
        BlockState otherPhysical = level.getBlockState(otherPos);
        boolean otherCopycat = isCopycat(otherPhysical);
        // Native sonar glass is already handled correctly by Fusion. Copycats'
        // filtered model view, however, can hide both another Copycat's
        // material and an adjacent native block, so resolve every connection
        // involving at least one Copycat against the unwrapped world.
        if (!ownCopycat && !otherCopycat) return;

        BlockState ownMaterial = SonarGlass.material(level, ownPos);
        BlockState otherMaterial = SonarGlass.material(level, otherPos);
        cir.setReturnValue(sameMaterial(ownMaterial, otherMaterial));
    }

    private static boolean isVerticalSlope(BlockState state) {
        ResourceLocation id = state.getBlock().builtInRegistryHolder().key()
                .location();
        return COPYCATS_NAMESPACE.equals(id.getNamespace())
                && "copycat_vertical_slope".equals(id.getPath());
    }

    private static SlopeFrameConnectivity.Slope slope(BlockState state) {
        ResourceLocation id = state.getBlock().builtInRegistryHolder().key()
                .location();
        if (!COPYCATS_NAMESPACE.equals(id.getNamespace())) return null;

        SlopeFrameConnectivity.Kind kind;
        if ("copycat_slope".equals(id.getPath())) {
            kind = SlopeFrameConnectivity.Kind.REGULAR;
        } else if ("copycat_vertical_slope".equals(id.getPath())) {
            kind = SlopeFrameConnectivity.Kind.VERTICAL;
        } else {
            return null;
        }

        Object facingValue = propertyValue(state, "facing");
        if (!(facingValue instanceof Direction facing)
                || !facing.getAxis().isHorizontal()) return null;
        return new SlopeFrameConnectivity.Slope(
                kind,
                SlopeFrameConnectivity.HorizontalFacing.valueOf(
                        facing.name()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object propertyValue(BlockState state, String name) {
        Property property = state.getBlock().getStateDefinition()
                .getProperty(name);
        return property == null ? null : state.getValue(property);
    }

    private static boolean isCopycat(BlockState state) {
        ResourceLocation id = state.getBlock().builtInRegistryHolder().key()
                .location();
        return COPYCATS_NAMESPACE.equals(id.getNamespace());
    }

    private static boolean sameMaterial(BlockState first, BlockState second) {
        return first != null && second != null
                && first.getBlock() == second.getBlock();
    }

    private static boolean isCopycatsView(BlockAndTintGetter level) {
        return level.getClass().getName().startsWith(
                "com.copycatsplus.copycats.");
    }

    /**
     * Copycats gives copied models a filtered world view. Reading its backing
     * view is necessary here because the filter may hide the native full block
     * from a sloped copycat even though their visual frame should connect.
     */
    private static BlockAndTintGetter unwrapCopycatsView(
            BlockAndTintGetter level) {
        BlockAndTintGetter current = level;
        for (int depth = 0; depth < 3; depth++) {
            if (!isCopycatsView(current)) break;
            Object wrapped = wrapped(current);
            if (!(wrapped instanceof BlockAndTintGetter next)
                    || next == current) break;
            current = next;
        }
        return current;
    }

    private static Object wrapped(Object view) {
        try {
            Method method = view.getClass().getMethod("getWrapped");
            return method.invoke(view);
        } catch (ReflectiveOperationException ignored) {
            try {
                Field field = view.getClass().getField("wrapped");
                return field.get(view);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }
}
