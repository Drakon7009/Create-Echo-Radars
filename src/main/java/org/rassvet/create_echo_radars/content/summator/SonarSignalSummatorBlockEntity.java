package org.rassvet.create_echo_radars.content.summator;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.content.glass.SonarGlass;
import org.rassvet.create_echo_radars.content.glass.SonarGlassNetworkManager;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class SonarSignalSummatorBlockEntity extends BlockEntity {
    private final BlockPos[] filterers = new BlockPos[SonarSignalSummatorBlock.SLOT_COUNT];
    private BlockPos glassTarget;

    public SonarSignalSummatorBlockEntity(BlockPos pos, BlockState state) {
        super(CreateEchoRadars.SIGNAL_SUMMATOR_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, SonarSignalSummatorBlockEntity summator) {
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % 20 != 0) return;
        SonarGlassNetworkManager.get(serverLevel).registerSummator(summator);
    }

    public void setGlassTarget(BlockPos target) {
        if (level instanceof ServerLevel serverLevel) {
            BlockPos canonical = SonarGlassNetworkManager.canonicalDisplayPosition(
                    serverLevel, target);
            if (canonical != null) target = canonical;
        }
        glassTarget = target.immutable();
        sync();
    }

    public @Nullable BlockPos glassTarget() {
        return glassTarget;
    }

    public boolean hasAntenna(int slot) {
        return slot >= 0 && slot < filterers.length && filterers[slot] != null;
    }

    public boolean hasFilterer(BlockPos filterer) {
        return Arrays.stream(filterers).anyMatch(filterer::equals);
    }

    public boolean installAntenna(int slot, BlockPos filterer) {
        if (slot < 0 || slot >= filterers.length || filterers[slot] != null
                || hasFilterer(filterer)) return false;
        filterers[slot] = filterer.immutable();
        sync();
        refreshOutput();
        return true;
    }

    public boolean removeAntenna(int slot) {
        if (slot < 0 || slot >= filterers.length || filterers[slot] == null) return false;
        filterers[slot] = null;
        sync();
        refreshOutput();
        return true;
    }

    public boolean hasValidGlassTarget(ServerLevel level) {
        return glassTarget != null && SonarGlass.isGlass(level, glassTarget)
                && SonarSignalSummatorItem.withinPlacementRange(level,
                worldPosition, glassTarget);
    }

    public List<SonarBlockEntity> linkedSonars(ServerLevel level) {
        NetworkData data = NetworkData.get(level);
        List<SonarBlockEntity> sonars = new ArrayList<>();
        for (BlockPos filterer : filterers) {
            if (filterer == null || !(level.getBlockEntity(filterer)
                    instanceof NetworkFiltererBlockEntity)) continue;
            NetworkData.Group group = data.getGroup(level.dimension(), filterer);
            if (group != null && group.radarPos != null
                    && level.getBlockEntity(group.radarPos) instanceof SonarBlockEntity sonar) {
                sonars.add(sonar);
            }
        }
        return List.copyOf(sonars);
    }

    public void applyPositionRemaps(
            Map<Long, SummatorLinkRemap.Move> glassMoves,
            Map<Long, SummatorLinkRemap.Move> filtererMoves, long now) {
        boolean changed = false;
        if (glassTarget != null) {
            long resolved = SummatorLinkRemap.resolve(
                    glassTarget.asLong(), glassMoves, now);
            if (resolved != glassTarget.asLong()) {
                glassTarget = BlockPos.of(resolved);
                changed = true;
            }
        }
        for (int slot = 0; slot < filterers.length; slot++) {
            BlockPos filterer = filterers[slot];
            if (filterer == null) continue;
            long resolved = SummatorLinkRemap.resolve(
                    filterer.asLong(), filtererMoves, now);
            if (resolved != filterer.asLong()) {
                filterers[slot] = BlockPos.of(resolved);
                changed = true;
            }
        }
        if (changed) {
            sync();
            refreshOutput();
        }
    }

    private void refreshOutput() {
        if (level instanceof ServerLevel serverLevel && glassTarget != null) {
            SonarGlassNetworkManager.get(serverLevel).refreshNow(
                    glassTarget, level.getGameTime());
        }
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
        if (glassTarget != null) tag.putLong("GlassTarget", glassTarget.asLong());
        ListTag slots = new ListTag();
        for (int i = 0; i < filterers.length; i++) {
            if (filterers[i] == null) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.putLong("Filterer", filterers[i].asLong());
            slots.add(entry);
        }
        tag.put("Antennas", slots);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        glassTarget = tag.contains("GlassTarget")
                ? BlockPos.of(tag.getLong("GlassTarget")) : null;
        Arrays.fill(filterers, null);
        ListTag slots = tag.getList("Antennas", Tag.TAG_COMPOUND);
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag entry = slots.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < filterers.length) {
                filterers[slot] = BlockPos.of(entry.getLong("Filterer"));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
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
        if (level instanceof ServerLevel serverLevel) {
            SonarGlassNetworkManager manager = SonarGlassNetworkManager.get(serverLevel);
            manager.registerSummator(this);
            if (glassTarget != null) {
                BlockPos canonical = SonarGlassNetworkManager.canonicalDisplayPosition(
                        serverLevel, glassTarget);
                if (canonical != null && !canonical.equals(glassTarget)) {
                    glassTarget = canonical;
                    sync();
                }
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            SonarGlassNetworkManager.get(serverLevel).unregisterSummator(this);
        }
        super.setRemoved();
    }
}
