package org.rassvet.create_echo_radars;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.rassvet.create_echo_radars.config.ServerConfig;
import org.rassvet.create_echo_radars.config.SyncedServerConfig;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarType;
import org.rassvet.create_echo_radars.performance.PerformanceNetworking;
import org.rassvet.create_echo_radars.performance.PerformanceTestBuild;

public final class ModNetworking {
    private ModNetworking() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetworking::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("17");
        registrar.playToServer(UpdateSonarSettingsPayload.TYPE,
                        UpdateSonarSettingsPayload.STREAM_CODEC, UpdateSonarSettingsPayload::handle)
                .playToServer(UpdateServerConfigPayload.TYPE,
                        UpdateServerConfigPayload.STREAM_CODEC, UpdateServerConfigPayload::handle)
                .playToClient(ServerConfigSnapshotPayload.TYPE,
                        ServerConfigSnapshotPayload.STREAM_CODEC, ServerConfigSnapshotPayload::handle)
                .playToClient(SonarGlassPulsePayload.TYPE,
                        SonarGlassPulsePayload.STREAM_CODEC, SonarGlassPulsePayload::handle);
        if (PerformanceTestBuild.enabled()) PerformanceNetworking.register(registrar);
    }

    public static void sendSettings(BlockPos pos, int range, int horizontalSector,
                                    int verticalSector, int tiltAngle, boolean autoHeight) {
        PacketDistributor.sendToServer(new UpdateSonarSettingsPayload(
                pos, range, horizontalSector, verticalSector, tiltAngle, autoHeight));
    }

    public static void sendServerConfig(int[] horizontalBeams, int[] verticalBeams,
                                        int additionalRays, boolean refineOnlyUndetectedNeighbors,
                                        int hitRefinementBacktrackBlocks,
                                        int blocksPerTick, int pingPauseTicks,
                                        int sideScanPingPauseTicks,
                                        boolean sideScanMovementOnly, int maxConcurrentChunkReads,
                                        int traceWorkerThreads, int maximumSonarRange,
                                        boolean angleRangeReduction, boolean entityOcclusionCheck,
                                        boolean traceTimeProfiling) {
        PacketDistributor.sendToServer(new UpdateServerConfigPayload(horizontalBeams, verticalBeams,
                additionalRays, refineOnlyUndetectedNeighbors, hitRefinementBacktrackBlocks,
                blocksPerTick, pingPauseTicks, sideScanPingPauseTicks, sideScanMovementOnly,
                maxConcurrentChunkReads, traceWorkerThreads,
                maximumSonarRange, angleRangeReduction,
                entityOcclusionCheck, traceTimeProfiling));
    }

    public static void syncServerConfig(net.minecraft.server.level.ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, ServerConfigSnapshotPayload.fromServerConfig());
    }

    public static void syncServerConfigToAllPlayers() {
        PacketDistributor.sendToAllPlayers(ServerConfigSnapshotPayload.fromServerConfig());
    }

    public static void sendGlassPulse(net.minecraft.server.level.ServerLevel level,
                                      BlockPos anchor, long serverTick) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new net.minecraft.world.level.ChunkPos(anchor),
                new SonarGlassPulsePayload(anchor, serverTick));
    }

    public record SonarGlassPulsePayload(BlockPos anchor, long serverTick) implements CustomPacketPayload {
        public static final Type<SonarGlassPulsePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID, "sonar_glass_pulse"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SonarGlassPulsePayload> STREAM_CODEC =
                StreamCodec.ofMember(SonarGlassPulsePayload::encode, SonarGlassPulsePayload::decode);

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(anchor);
            buffer.writeLong(serverTick);
        }

        private static SonarGlassPulsePayload decode(RegistryFriendlyByteBuf buffer) {
            return new SonarGlassPulsePayload(buffer.readBlockPos(), buffer.readLong());
        }

        private static void handle(SonarGlassPulsePayload payload, IPayloadContext context) {
            context.enqueueWork(() -> org.rassvet.create_echo_radars.client.SonarGlassOverlay
                    .pulse(payload.anchor, payload.serverTick));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateSonarSettingsPayload(BlockPos pos, int range, int horizontalSector,
                                             int verticalSector, int tiltAngle, boolean autoHeight)
            implements CustomPacketPayload {
        public static final Type<UpdateSonarSettingsPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID, "update_sonar_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateSonarSettingsPayload> STREAM_CODEC =
                StreamCodec.ofMember(UpdateSonarSettingsPayload::encode, UpdateSonarSettingsPayload::decode);

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(range);
            buffer.writeVarInt(horizontalSector);
            buffer.writeVarInt(verticalSector);
            buffer.writeVarInt(tiltAngle);
            buffer.writeBoolean(autoHeight);
        }

        private static UpdateSonarSettingsPayload decode(RegistryFriendlyByteBuf buffer) {
            return new UpdateSonarSettingsPayload(buffer.readBlockPos(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
        }

        private static void handle(UpdateSonarSettingsPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player().level().getBlockEntity(payload.pos) instanceof SonarBlockEntity sonar) {
                    sonar.applySettings(context.player(), payload.range, payload.horizontalSector,
                            payload.verticalSector, payload.tiltAngle, payload.autoHeight);
                }
            });
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateServerConfigPayload(int[] horizontalBeams, int[] verticalBeams,
                                            int additionalRays, boolean refineOnlyUndetectedNeighbors,
                                            int hitRefinementBacktrackBlocks,
                                            int blocksPerTick, int pingPauseTicks,
                                            int sideScanPingPauseTicks,
                                            boolean sideScanMovementOnly,
                                            int maxConcurrentChunkReads, int traceWorkerThreads,
                                            int maximumSonarRange, boolean angleRangeReduction,
                                            boolean entityOcclusionCheck, boolean traceTimeProfiling)
            implements CustomPacketPayload {
        public static final Type<UpdateServerConfigPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID, "update_server_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateServerConfigPayload> STREAM_CODEC =
                StreamCodec.ofMember(UpdateServerConfigPayload::encode, UpdateServerConfigPayload::decode);

        private void encode(RegistryFriendlyByteBuf buffer) {
            writeBeamValues(buffer, horizontalBeams);
            writeBeamValues(buffer, verticalBeams);
            buffer.writeVarInt(additionalRays);
            buffer.writeBoolean(refineOnlyUndetectedNeighbors);
            buffer.writeVarInt(hitRefinementBacktrackBlocks);
            buffer.writeVarInt(blocksPerTick);
            buffer.writeVarInt(pingPauseTicks);
            buffer.writeVarInt(sideScanPingPauseTicks);
            buffer.writeBoolean(sideScanMovementOnly);
            buffer.writeVarInt(maxConcurrentChunkReads);
            buffer.writeVarInt(traceWorkerThreads);
            buffer.writeVarInt(maximumSonarRange);
            buffer.writeBoolean(angleRangeReduction);
            buffer.writeBoolean(entityOcclusionCheck);
            buffer.writeBoolean(traceTimeProfiling);
        }

        private static UpdateServerConfigPayload decode(RegistryFriendlyByteBuf buffer) {
            return new UpdateServerConfigPayload(readBeamValues(buffer), readBeamValues(buffer),
                    buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean());
        }

        private static void handle(UpdateServerConfigPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!context.player().hasPermissions(2)) {
                    CreateEchoRadars.LOGGER.warn("Player {} tried to update sonar server config without permission",
                            context.player().getScoreboardName());
                    return;
                }
                ServerConfig.save(payload.horizontalBeams, payload.verticalBeams,
                        payload.additionalRays, payload.refineOnlyUndetectedNeighbors,
                        payload.hitRefinementBacktrackBlocks,
                        payload.blocksPerTick, payload.pingPauseTicks,
                        payload.sideScanPingPauseTicks, payload.sideScanMovementOnly,
                        payload.maxConcurrentChunkReads, payload.traceWorkerThreads,
                        payload.maximumSonarRange, payload.angleRangeReduction,
                        payload.entityOcclusionCheck, payload.traceTimeProfiling);
                syncServerConfigToAllPlayers();
                CreateEchoRadars.LOGGER.info(
                        "Player {} updated sonar server config: beams={}, additionalRays={}, refineOnlyUndetectedNeighbors={}, hitRefinementBacktrackBlocks={}, blocksPerTick={}, pingPauseTicks={}, sideScanPingPauseTicks={}, sideScanMovementOnly={}, maxConcurrentChunkReads={}, traceWorkerThreads={}, maximumSonarRange={}, angleRangeReduction={}, entityOcclusionCheck={}, traceTimeProfiling={}",
                        context.player().getScoreboardName(), beamSettingsLog(), ServerConfig.additionalRays(),
                        ServerConfig.refineOnlyUndetectedNeighbors(), ServerConfig.hitRefinementBacktrackBlocks(),
                        ServerConfig.blocksPerTick(), ServerConfig.pingPauseTicks(),
                        ServerConfig.sideScanPingPauseTicks(),
                        ServerConfig.sideScanMovementOnly(),
                        ServerConfig.maxConcurrentChunkReads(), ServerConfig.traceWorkerThreads(),
                        ServerConfig.maximumSonarRange(), ServerConfig.angleRangeReduction(),
                        ServerConfig.entityOcclusionCheck(), ServerConfig.traceTimeProfiling());
            });
        }

        private static void writeBeamValues(RegistryFriendlyByteBuf buffer, int[] values) {
            int expected = SonarType.values().length;
            if (values.length != expected) {
                throw new IllegalArgumentException("Expected beam settings for " + expected + " sonar types");
            }
            for (int value : values) buffer.writeVarInt(value);
        }

        private static int[] readBeamValues(RegistryFriendlyByteBuf buffer) {
            int[] values = new int[SonarType.values().length];
            for (int i = 0; i < values.length; i++) values[i] = buffer.readVarInt();
            return values;
        }

        private static String beamSettingsLog() {
            StringBuilder result = new StringBuilder("{");
            for (SonarType type : SonarType.values()) {
                if (result.length() > 1) result.append(", ");
                ServerConfig.BeamSettings beams = ServerConfig.beamSettings(type);
                result.append(type.registryName()).append('=')
                        .append(beams.horizontal()).append('x').append(beams.vertical());
            }
            return result.append('}').toString();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ServerConfigSnapshotPayload(int[] horizontalBeams, int[] verticalBeams,
                                              int additionalRays, boolean refineOnlyUndetectedNeighbors,
                                              int hitRefinementBacktrackBlocks,
                                              int blocksPerTick, int pingPauseTicks,
                                              int sideScanPingPauseTicks,
                                              boolean sideScanMovementOnly,
                                              int maxConcurrentChunkReads, int traceWorkerThreads,
                                              int maximumSonarRange, boolean angleRangeReduction,
                                              boolean entityOcclusionCheck, boolean traceTimeProfiling)
            implements CustomPacketPayload {
        public static final Type<ServerConfigSnapshotPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID, "server_config_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigSnapshotPayload> STREAM_CODEC =
                StreamCodec.ofMember(ServerConfigSnapshotPayload::encode, ServerConfigSnapshotPayload::decode);

        private void encode(RegistryFriendlyByteBuf buffer) {
            UpdateServerConfigPayload.writeBeamValues(buffer, horizontalBeams);
            UpdateServerConfigPayload.writeBeamValues(buffer, verticalBeams);
            buffer.writeVarInt(additionalRays);
            buffer.writeBoolean(refineOnlyUndetectedNeighbors);
            buffer.writeVarInt(hitRefinementBacktrackBlocks);
            buffer.writeVarInt(blocksPerTick);
            buffer.writeVarInt(pingPauseTicks);
            buffer.writeVarInt(sideScanPingPauseTicks);
            buffer.writeBoolean(sideScanMovementOnly);
            buffer.writeVarInt(maxConcurrentChunkReads);
            buffer.writeVarInt(traceWorkerThreads);
            buffer.writeVarInt(maximumSonarRange);
            buffer.writeBoolean(angleRangeReduction);
            buffer.writeBoolean(entityOcclusionCheck);
            buffer.writeBoolean(traceTimeProfiling);
        }

        private static ServerConfigSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ServerConfigSnapshotPayload(UpdateServerConfigPayload.readBeamValues(buffer),
                    UpdateServerConfigPayload.readBeamValues(buffer), buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean());
        }

        private static ServerConfigSnapshotPayload fromServerConfig() {
            SyncedServerConfig.Snapshot snapshot = SyncedServerConfig.snapshotFromServer();
            return new ServerConfigSnapshotPayload(snapshot.horizontalBeams(), snapshot.verticalBeams(),
                    snapshot.additionalRays(), snapshot.refineOnlyUndetectedNeighbors(),
                    snapshot.hitRefinementBacktrackBlocks(), snapshot.blocksPerTick(), snapshot.pingPauseTicks(),
                    snapshot.sideScanPingPauseTicks(),
                    snapshot.sideScanMovementOnly(),
                    snapshot.maxConcurrentChunkReads(), snapshot.traceWorkerThreads(),
                    snapshot.maximumSonarRange(), snapshot.angleRangeReduction(),
                    snapshot.entityOcclusionCheck(), snapshot.traceTimeProfiling());
        }

        private static void handle(ServerConfigSnapshotPayload payload, IPayloadContext context) {
            SyncedServerConfig.apply(payload.horizontalBeams, payload.verticalBeams, payload.additionalRays,
                    payload.refineOnlyUndetectedNeighbors, payload.hitRefinementBacktrackBlocks,
                    payload.blocksPerTick, payload.pingPauseTicks,
                    payload.sideScanPingPauseTicks, payload.sideScanMovementOnly,
                    payload.maxConcurrentChunkReads,
                    payload.traceWorkerThreads, payload.maximumSonarRange,
                    payload.angleRangeReduction, payload.entityOcclusionCheck,
                    payload.traceTimeProfiling);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
