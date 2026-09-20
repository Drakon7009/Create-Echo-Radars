package org.rassvet.create_echo_radars.content.glass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.client.SonarGlassOverlay;
import com.happysg.radar.block.behavior.networks.NetworkData;

public final class SonarGlassBlockEntity extends BlockEntity {
    private static final String SYNC_MARKER = "SonarGlassSync";
    private java.util.List<SonarGlassState> currentStates = java.util.List.of();
    private java.util.List<SonarGlassState> previousStates = java.util.List.of();
    private long cycleStartTick;
    private long disconnectStartTick = -1;
    private BlockPos lastKnownNetworkPos;

    public SonarGlassBlockEntity(BlockPos pos, BlockState state) {
        super(CreateEchoRadars.SONAR_GLASS_BLOCK_ENTITY.get(), pos, state);
        lastKnownNetworkPos = pos.immutable();
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
                            SonarGlassBlockEntity blockEntity) {
        if (level.isClientSide || level.getGameTime() % 20 != 0
                || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        blockEntity.updateNetworkPosition(serverLevel);
    }

    private void updateNetworkPosition(net.minecraft.server.level.ServerLevel serverLevel) {
        if (lastKnownNetworkPos.equals(worldPosition)) return;
        BlockPos previousPosition = lastKnownNetworkPos;
        SonarGlassNetworkManager.get(serverLevel).remapGlassTarget(
                previousPosition, worldPosition, serverLevel.getGameTime());
        NetworkData data = NetworkData.get(serverLevel);
        data.updateMonitorPosition(serverLevel.dimension(),
                previousPosition, worldPosition);
        lastKnownNetworkPos = worldPosition.immutable();
        setChanged();
    }

    public @Nullable SonarGlassState currentState() {
        return currentStates.isEmpty() ? null : currentStates.getFirst();
    }
    public @Nullable SonarGlassState previousState() {
        return previousStates.isEmpty() ? null : previousStates.getFirst();
    }
    public java.util.List<SonarGlassState> currentStates() { return currentStates; }
    public java.util.List<SonarGlassState> previousStates() { return previousStates; }
    public long cycleStartTick() { return cycleStartTick; }
    public long disconnectStartTick() { return disconnectStartTick; }

    public void acceptState(SonarGlassState state, long startTick) {
        acceptStates(java.util.List.of(state), startTick);
    }

    public void acceptStates(java.util.List<SonarGlassState> states, long startTick) {
        previousStates = currentStates;
        currentStates = java.util.List.copyOf(states);
        cycleStartTick = startTick;
        disconnectStartTick = -1;
        sync();
    }

    public void beginDisconnect(long tick) {
        if (disconnectStartTick >= 0) return;
        disconnectStartTick = tick;
        sync();
    }

    public void clearState() {
        if (currentStates.isEmpty() && previousStates.isEmpty()
                && disconnectStartTick < 0) return;
        currentStates = java.util.List.of();
        previousStates = java.util.List.of();
        disconnectStartTick = -1;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("LastKnownNetworkPos", lastKnownNetworkPos.asLong());
        // Display state intentionally is not persisted; the controller refreshes it after load.
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("LastKnownNetworkPos")) {
            lastKnownNetworkPos = BlockPos.of(tag.getLong("LastKnownNetworkPos"));
        }
        if (!tag.getBoolean(SYNC_MARKER)) return;
        currentStates = loadStates(tag, "CurrentStates", "CurrentState");
        previousStates = loadStates(tag, "PreviousStates", "PreviousState");
        cycleStartTick = tag.getLong("CycleStart");
        disconnectStartTick = tag.contains("DisconnectStart") ? tag.getLong("DisconnectStart") : -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SYNC_MARKER, true);
        tag.put("CurrentStates", saveStates(currentStates));
        tag.put("PreviousStates", saveStates(previousStates));
        tag.putLong("CycleStart", cycleStartTick);
        if (disconnectStartTick >= 0) tag.putLong("DisconnectStart", disconnectStartTick);
        return tag;
    }

    private static ListTag saveStates(java.util.List<SonarGlassState> states) {
        ListTag list = new ListTag();
        for (SonarGlassState state : states) list.add(state.save());
        return list;
    }

    private static java.util.List<SonarGlassState> loadStates(
            CompoundTag tag, String listKey, String legacyKey) {
        if (tag.contains(listKey, Tag.TAG_LIST)) {
            ListTag list = tag.getList(listKey, Tag.TAG_COMPOUND);
            java.util.ArrayList<SonarGlassState> states = new java.util.ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                states.add(SonarGlassState.load(list.getCompound(i)));
            }
            return java.util.List.copyOf(states);
        }
        return tag.contains(legacyKey, Tag.TAG_COMPOUND)
                ? java.util.List.of(SonarGlassState.load(tag.getCompound(legacyKey)))
                : java.util.List.of();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) loadAdditional(tag, registries);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            updateNetworkPosition(serverLevel);
        } else if (level != null && level.isClientSide) {
            SonarGlassOverlay.track(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) SonarGlassOverlay.untrack(this);
        super.setRemoved();
    }
}
