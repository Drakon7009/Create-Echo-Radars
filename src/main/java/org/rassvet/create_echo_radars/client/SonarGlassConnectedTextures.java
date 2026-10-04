package org.rassvet.create_echo_radars.client;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.model.ModelSwapper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.compat.ct.SlopeFrameConnectivity;
import org.rassvet.create_echo_radars.content.glass.SonarGlass;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Native Create CT for the frame; the translucent filter keeps its own UVs. */
public final class SonarGlassConnectedTextures {
    private static final CTSpriteShiftEntry IRON = shift("iron");
    private static final CTSpriteShiftEntry COPPER = shift("copper");

    private SonarGlassConnectedTextures() {}

    private static CTSpriteShiftEntry shift(String metal) {
        return CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
                ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID,
                        "block/" + metal + "_sonar_glass_frame"),
                ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID,
                        "block/" + metal + "_sonar_glass_frame_connected"));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(SonarGlassConnectedTextures::onModelBake);
    }

    private static void onModelBake(ModelEvent.ModifyBakingResult event) {
        ModelSwapper.swapModels(event.getModels(),
                ModelSwapper.getAllBlockStateModelLocations(CreateEchoRadars.IRON_SONAR_GLASS.get()),
                model -> new CTModel(model, new FrameBehaviour(IRON)));
        ModelSwapper.swapModels(event.getModels(),
                ModelSwapper.getAllBlockStateModelLocations(CreateEchoRadars.COPPER_SONAR_GLASS.get()),
                model -> new CTModel(model, new FrameBehaviour(COPPER)));
    }

    private static final class FrameBehaviour extends ConnectedTextureBehaviour.Base {
        private final CTSpriteShiftEntry shift;

        private FrameBehaviour(CTSpriteShiftEntry shift) {
            this.shift = shift;
        }

        @Override
        public CTSpriteShiftEntry getShift(BlockState state, Direction face, TextureAtlasSprite sprite) {
            return sprite == null || sprite == shift.getOriginal() ? shift : null;
        }

        @Override
        public boolean buildContextForOccludedDirections() {
            // Copycats can expose a face which the source full cube would hide.
            return true;
        }

        @Override
        public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter view,
                                  BlockPos pos, BlockPos otherPos, Direction face) {
            BlockAndTintGetter physical = unwrapCopycatsView(view);
            BlockState ownPhysical = physical.getBlockState(pos);

            if (isCopycatsView(view) && SonarGlass.isGlass(state)) {
                SlopeFrameConnectivity.Slope ownSlope = slope(ownPhysical);
                if (ownSlope != null && SlopeFrameConnectivity.isTriangularEndFace(
                        ownSlope, SlopeFrameConnectivity.Face.valueOf(face.name()))) {
                    return false;
                }
                // The vertical slope filter can hide an adjoining full glass block.
                if (isVerticalSlope(ownPhysical)) {
                    BlockState direct = physical.getBlockState(otherPos);
                    if (SonarGlass.isGlass(direct) && sameMaterial(state, direct)) return true;
                }
                BlockState visible = view.getBlockState(otherPos);
                if (SonarGlass.isGlass(visible)) return sameMaterial(state, visible);
                if (!isCopycat(visible)) return false;
                BlockState material = SonarGlass.material(physical, otherPos);
                if (material != null) return sameMaterial(state, material);
                // Virtual contraptions may only expose an appearance state.
                return physical.getBlockEntity(otherPos) == null && sameMaterial(state, other);
            }

            if (isCopycat(ownPhysical) || isCopycat(physical.getBlockState(otherPos))) {
                return sameMaterial(SonarGlass.material(physical, pos),
                        SonarGlass.material(physical, otherPos));
            }
            return super.connectsTo(state, other, view, pos, otherPos, face);
        }
    }

    private static boolean sameMaterial(BlockState first, BlockState second) {
        return first != null && second != null && first.getBlock() == second.getBlock();
    }

    private static boolean isCopycat(BlockState state) {
        return "copycats".equals(state.getBlock().builtInRegistryHolder().key().location().getNamespace());
    }

    private static boolean isVerticalSlope(BlockState state) {
        return isCopycat(state) && "copycat_vertical_slope".equals(
                state.getBlock().builtInRegistryHolder().key().location().getPath());
    }

    private static SlopeFrameConnectivity.Slope slope(BlockState state) {
        if (!isCopycat(state)) return null;
        String path = state.getBlock().builtInRegistryHolder().key().location().getPath();
        SlopeFrameConnectivity.Kind kind;
        if ("copycat_slope".equals(path)) kind = SlopeFrameConnectivity.Kind.REGULAR;
        else if ("copycat_vertical_slope".equals(path)) kind = SlopeFrameConnectivity.Kind.VERTICAL;
        else return null;
        Object value = propertyValue(state, "facing");
        if (!(value instanceof Direction facing) || !facing.getAxis().isHorizontal()) return null;
        return new SlopeFrameConnectivity.Slope(kind,
                SlopeFrameConnectivity.HorizontalFacing.valueOf(facing.name()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object propertyValue(BlockState state, String name) {
        Property property = state.getBlock().getStateDefinition().getProperty(name);
        return property == null ? null : state.getValue(property);
    }

    private static boolean isCopycatsView(BlockAndTintGetter level) {
        return level.getClass().getName().startsWith("com.copycatsplus.copycats.");
    }

    private static BlockAndTintGetter unwrapCopycatsView(BlockAndTintGetter level) {
        BlockAndTintGetter current = level;
        for (int depth = 0; depth < 3 && isCopycatsView(current); depth++) {
            Object wrapped = wrapped(current);
            if (!(wrapped instanceof BlockAndTintGetter next) || next == current) break;
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
