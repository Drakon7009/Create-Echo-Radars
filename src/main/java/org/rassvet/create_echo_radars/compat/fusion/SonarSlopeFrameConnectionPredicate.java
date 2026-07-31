package org.rassvet.create_echo_radars.compat.fusion;

import com.google.gson.JsonObject;
import com.supermartijn642.fusion.api.texture.types.connecting.predicates.ConnectionDirection;
import com.supermartijn642.fusion.api.texture.types.connecting.predicates.ConnectionPredicate;
import com.supermartijn642.fusion.api.texture.types.connecting.predicates.FusionConnectionPredicateRegistry;
import com.supermartijn642.fusion.api.util.Serializer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.rassvet.create_echo_radars.CreateEchoRadars;

/**
 * Marker predicate used only by the sonar-glass frame texture. The Copycats
 * slope-specific topology is handled in {@code FusionConnectingTextureMixin},
 * where Fusion's exact world-space neighbor offset is available.
 */
public final class SonarSlopeFrameConnectionPredicate
        implements ConnectionPredicate {
    public static final SonarSlopeFrameConnectionPredicate INSTANCE =
            new SonarSlopeFrameConnectionPredicate();

    public static final Serializer<SonarSlopeFrameConnectionPredicate>
            SERIALIZER = new Serializer<>() {
        @Override
        public SonarSlopeFrameConnectionPredicate deserialize(
                JsonObject json) {
            return INSTANCE;
        }

        @Override
        public JsonObject serialize(
                SonarSlopeFrameConnectionPredicate predicate) {
            return new JsonObject();
        }
    };

    private SonarSlopeFrameConnectionPredicate() {}

    public static void register() {
        FusionConnectionPredicateRegistry.registerConnectionPredicate(
                ResourceLocation.fromNamespaceAndPath(
                        CreateEchoRadars.MOD_ID, "sonar_slope_frame"),
                SERIALIZER);
    }

    @Override
    public boolean shouldConnect(Direction face, BlockState state,
                                 BlockState otherState,
                                 BlockState blockingState,
                                 ConnectionDirection direction) {
        return otherState.getBlock() != Blocks.AIR
                && state.getBlock() == otherState.getBlock();
    }

    @Override
    public Serializer<? extends ConnectionPredicate> getSerializer() {
        return SERIALIZER;
    }
}
