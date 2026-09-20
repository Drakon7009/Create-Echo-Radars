package org.rassvet.create_echo_radars.performance;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.rassvet.create_echo_radars.CreateEchoRadars;

public final class PerformanceNetworking {
    private PerformanceNetworking() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(ControlPayload.TYPE, ControlPayload.STREAM_CODEC, ControlPayload::handle)
                .playToServer(ClientMetricsPayload.TYPE, ClientMetricsPayload.STREAM_CODEC,
                        ClientMetricsPayload::handle);
    }

    public static void sendControl(ServerPlayer player, boolean active, int stageId, String label) {
        PacketDistributor.sendToPlayer(player, new ControlPayload(active, stageId, label));
    }

    public record ControlPayload(boolean active, int stageId, String label)
            implements CustomPacketPayload {
        public static final Type<ControlPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CreateEchoRadars.MOD_ID, "performance_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ControlPayload> STREAM_CODEC =
                StreamCodec.ofMember(ControlPayload::encode, ControlPayload::decode);

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeBoolean(active);
            buffer.writeVarInt(stageId);
            buffer.writeUtf(label, 256);
        }

        private static ControlPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ControlPayload(buffer.readBoolean(), buffer.readVarInt(), buffer.readUtf(256));
        }

        private static void handle(ControlPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> PerformanceClientTelemetry.control(
                    payload.active, payload.stageId, payload.label));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ClientMetricsPayload(int stageId, int frames, long elapsedNanos,
                                       long p95FrameNanos, long maxFrameNanos)
            implements CustomPacketPayload {
        public static final Type<ClientMetricsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CreateEchoRadars.MOD_ID, "performance_client_metrics"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientMetricsPayload> STREAM_CODEC =
                StreamCodec.ofMember(ClientMetricsPayload::encode, ClientMetricsPayload::decode);

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(stageId);
            buffer.writeVarInt(frames);
            buffer.writeVarLong(elapsedNanos);
            buffer.writeVarLong(p95FrameNanos);
            buffer.writeVarLong(maxFrameNanos);
        }

        private static ClientMetricsPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ClientMetricsPayload(buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong());
        }

        private static void handle(ClientMetricsPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    PerformanceTestManager.acceptClientMetrics(player, payload);
                }
            });
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
