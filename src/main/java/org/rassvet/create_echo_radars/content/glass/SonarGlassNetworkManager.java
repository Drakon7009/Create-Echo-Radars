package org.rassvet.create_echo_radars.content.glass;

import com.happysg.radar.block.behavior.networks.NetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarBlock;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlockEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Collections;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;
import org.rassvet.create_echo_radars.content.summator.SummatorLinkRemap;

public final class SonarGlassNetworkManager {
    private static final long REMAP_RETENTION_TICKS = 200;
    private static final Map<ServerLevel, SonarGlassNetworkManager> INSTANCES = new WeakHashMap<>();
    private final Map<Long, State> states = new HashMap<>();
    private final Set<SonarSignalSummatorBlockEntity> summators =
            Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Long, SummatorLinkRemap.Move> glassMoves = new HashMap<>();
    private final Map<Long, SummatorLinkRemap.Move> filtererMoves = new HashMap<>();

    private SonarGlassNetworkManager() {}

    public static SonarGlassNetworkManager get(ServerLevel level) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(level, ignored -> new SonarGlassNetworkManager());
        }
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        pruneMoves(glassMoves, now);
        pruneMoves(filtererMoves, now);
        Set<Long> active = new HashSet<>();
        Map<Long, TreeMap<Long, SonarBlockEntity>> sourcesByDisplay = new HashMap<>();
        Map<Long, SonarGlassBlockEntity> displays = new HashMap<>();
        NetworkData network = NetworkData.get(level);
        for (NetworkData.Group group : network.getGroupsByFiltererView().values()) {
            if (group.radarPos == null || !(level.getBlockEntity(group.radarPos) instanceof SonarBlockEntity sonar)
                    || !isLiveSource(level, sonar)) {
                continue;
            }
            if (!sonar.isEmitterSubmerged()) continue;
            for (BlockPos endpoint : group.monitorEndpoints) {
                SonarGlassBlockEntity display = displayEntity(level, endpoint);
                if (display == null) continue;
                addSource(sourcesByDisplay, displays, display, sonar);
            }
        }

        summators.removeIf(summator -> summator.isRemoved()
                || summator.getLevel() != level);
        applyMoves(now);
        for (SonarSignalSummatorBlockEntity summator : List.copyOf(summators)) {
            if (!summator.hasValidGlassTarget(level)) continue;
            SonarGlassBlockEntity display = displayEntity(
                    level, summator.glassTarget());
            if (display == null) continue;
            for (SonarBlockEntity sonar : summator.linkedSonars(level)) {
                if (isLiveSource(level, sonar) && sonar.isEmitterSubmerged()) {
                    addSource(sourcesByDisplay, displays, display, sonar);
                }
            }
        }

        for (Map.Entry<Long, TreeMap<Long, SonarBlockEntity>> entry
                : sourcesByDisplay.entrySet()) {
            long endpointKey = entry.getKey();
            SonarGlassBlockEntity display = displays.get(endpointKey);
            active.add(endpointKey);
            State state = states.computeIfAbsent(endpointKey,
                    ignored -> new State(now));
            state.displayPos = display.getBlockPos().immutable();
            if (state.disconnecting) {
                state.disconnecting = false;
                state.nextCycle = now;
            }
            int signature = entry.getValue().keySet().hashCode();
            state.sourcePositions = Set.copyOf(entry.getValue().keySet());
            if (signature != state.sourceSignature) {
                state.sourceSignature = signature;
                state.nextCycle = now;
            }
            if (now >= state.nextCycle) {
                List<SonarGlassState> displayStates = entry.getValue().values()
                        .stream().map(SonarGlassState::from).toList();
                display.acceptStates(displayStates, now);
                state.nextCycle = now + SonarGlassAnimation.CYCLE_TICKS;
            }
        }

        Iterator<Map.Entry<Long, State>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, State> entry = iterator.next();
            if (active.contains(entry.getKey())) continue;
            State state = entry.getValue();
            BlockPos displayPos = state.displayPos == null
                    ? BlockPos.of(entry.getKey()) : state.displayPos;
            if (!(level.getBlockEntity(displayPos)
                    instanceof SonarGlassBlockEntity glass)) {
                iterator.remove();
                continue;
            }
            if (!state.disconnecting) {
                state.disconnecting = true;
                state.disconnectStart = now;
                glass.beginDisconnect(now);
            } else if (now - state.disconnectStart
                    >= SonarGlassAnimation.DISCONNECT_FADE_TICKS) {
                glass.clearState();
                iterator.remove();
            }
        }
    }

    public void refreshNow(BlockPos endpoint, long now) {
        states.computeIfAbsent(endpoint.asLong(), ignored -> new State(now));
        for (State state : states.values()) state.nextCycle = now;
    }

    public void onSonarRemoved(ServerLevel level, BlockPos sonarPos) {
        summators.removeIf(summator -> summator.isRemoved()
                || summator.getLevel() != level);
        for (SonarSignalSummatorBlockEntity summator : List.copyOf(summators)) {
            summator.onSonarRemoved(level, sonarPos);
        }
        long source = sonarPos.asLong();
        Iterator<Map.Entry<Long, State>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, State> entry = iterator.next();
            State state = entry.getValue();
            if (!state.sourcePositions.contains(source)) continue;
            if (state.sourcePositions.size() > 1) {
                state.sourcePositions = state.sourcePositions.stream()
                        .filter(position -> position != source)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                state.nextCycle = level.getGameTime();
                continue;
            }
            BlockPos displayPos = state.displayPos == null
                    ? BlockPos.of(entry.getKey()) : state.displayPos;
            if (level.getBlockEntity(displayPos) instanceof SonarGlassBlockEntity glass) {
                glass.clearState();
            }
            iterator.remove();
        }
    }

    public void registerSummator(SonarSignalSummatorBlockEntity summator) {
        summators.add(summator);
    }

    public void unregisterSummator(SonarSignalSummatorBlockEntity summator) {
        summators.remove(summator);
    }

    public void remapGlassTarget(BlockPos oldPos, BlockPos newPos, long now) {
        recordMove(glassMoves, oldPos, newPos, now);
    }

    public void remapFilterer(BlockPos oldPos, BlockPos newPos, long now) {
        recordMove(filtererMoves, oldPos, newPos, now);
    }

    private void applyMoves(long now) {
        for (SonarSignalSummatorBlockEntity summator : List.copyOf(summators)) {
            summator.applyPositionRemaps(glassMoves, filtererMoves, now);
        }
    }

    private static void recordMove(Map<Long, SummatorLinkRemap.Move> moves,
                                   BlockPos oldPos, BlockPos newPos, long now) {
        if (oldPos.equals(newPos)) return;
        moves.put(oldPos.asLong(), new SummatorLinkRemap.Move(
                newPos.asLong(), now, now + REMAP_RETENTION_TICKS));
    }

    private static void pruneMoves(Map<Long, SummatorLinkRemap.Move> moves,
                                   long now) {
        moves.values().removeIf(move -> move.expiresAt() < now);
    }

    public static @Nullable BlockPos canonicalDisplayPosition(
            ServerLevel level, BlockPos target) {
        if (!SonarGlass.isGlass(level, target)) return null;
        if (level.getBlockEntity(target) instanceof SonarGlassBlockEntity) {
            return target.immutable();
        }
        BlockPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (BlockPos pos : SonarGlassNetwork.find(level, target).blocks()) {
            if (!(level.getBlockEntity(pos) instanceof SonarGlassBlockEntity)) continue;
            long dx = (long) pos.getX() - target.getX();
            long dy = (long) pos.getY() - target.getY();
            long dz = (long) pos.getZ() - target.getZ();
            long distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance
                    || distance == bestDistance && (best == null
                    || pos.asLong() < best.asLong())) {
                best = pos.immutable();
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void addSource(
            Map<Long, TreeMap<Long, SonarBlockEntity>> sourcesByDisplay,
            Map<Long, SonarGlassBlockEntity> displays,
            SonarGlassBlockEntity display, SonarBlockEntity sonar) {
        long displayKey = display.getBlockPos().asLong();
        displays.put(displayKey, display);
        sourcesByDisplay.computeIfAbsent(displayKey, ignored -> new TreeMap<>())
                .put(sonar.getBlockPos().asLong(), sonar);
    }

    private static boolean isLiveSource(ServerLevel level, SonarBlockEntity sonar) {
        return !sonar.isRemoved()
                && level.getBlockState(sonar.getBlockPos()).getBlock() instanceof SonarBlock;
    }

    private static @Nullable SonarGlassBlockEntity displayEntity(
            ServerLevel level, BlockPos endpoint) {
        if (!SonarGlass.isGlass(level, endpoint)) return null;
        if (level.getBlockEntity(endpoint) instanceof SonarGlassBlockEntity glass) {
            return glass;
        }
        SonarGlassNetwork.Component component = SonarGlassNetwork.find(level, endpoint);
        for (BlockPos pos : component.blocks()) {
            if (level.getBlockEntity(pos) instanceof SonarGlassBlockEntity glass) {
                return glass;
            }
        }
        return null;
    }

    private static final class State {
        private long nextCycle;
        private long disconnectStart;
        private boolean disconnecting;
        private BlockPos displayPos;
        private int sourceSignature;
        private Set<Long> sourcePositions = Set.of();

        private State(long nextCycle) { this.nextCycle = nextCycle; }
    }
}
