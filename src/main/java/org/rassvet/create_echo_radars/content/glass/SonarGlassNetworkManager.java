package org.rassvet.create_echo_radars.content.glass;

import com.happysg.radar.block.behavior.networks.NetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class SonarGlassNetworkManager {
    private static final Map<ServerLevel, SonarGlassNetworkManager> INSTANCES = new WeakHashMap<>();
    private final Map<Long, State> states = new HashMap<>();

    private SonarGlassNetworkManager() {}

    public static SonarGlassNetworkManager get(ServerLevel level) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(level, ignored -> new SonarGlassNetworkManager());
        }
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        Set<Long> active = new HashSet<>();
        NetworkData network = NetworkData.get(level);
        for (NetworkData.Group group : network.getGroupsByFiltererView().values()) {
            if (group.radarPos == null || !(level.getBlockEntity(group.radarPos) instanceof SonarBlockEntity sonar)) {
                continue;
            }
            boolean emitterSubmerged = sonar.isEmitterSubmerged();
            for (BlockPos endpoint : group.monitorEndpoints) {
                if (!SonarGlass.isGlass(level.getBlockState(endpoint))
                        || !(level.getBlockEntity(endpoint) instanceof SonarGlassBlockEntity glass)) continue;
                long endpointKey = endpoint.asLong();
                if (!emitterSubmerged) {
                    states.remove(endpointKey);
                    glass.clearState();
                    continue;
                }
                active.add(endpointKey);
                State state = states.computeIfAbsent(endpointKey, ignored -> new State(now));
                if (state.disconnecting) {
                    state.disconnecting = false;
                    state.nextCycle = now;
                }
                if (now >= state.nextCycle) {
                    glass.acceptState(SonarGlassState.from(sonar), now);
                    state.nextCycle = now + SonarGlassAnimation.CYCLE_TICKS;
                }
            }
        }

        Iterator<Map.Entry<Long, State>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, State> entry = iterator.next();
            if (active.contains(entry.getKey())) continue;
            BlockPos pos = BlockPos.of(entry.getKey());
            if (!(level.getBlockEntity(pos) instanceof SonarGlassBlockEntity glass)) {
                iterator.remove();
                continue;
            }
            State state = entry.getValue();
            if (!state.disconnecting) {
                state.disconnecting = true;
                state.disconnectStart = now;
                glass.beginDisconnect(now);
            } else if (now - state.disconnectStart >= SonarGlassAnimation.DISCONNECT_FADE_TICKS) {
                glass.clearState();
                iterator.remove();
            }
        }
    }

    public void refreshNow(BlockPos endpoint, long now) {
        states.put(endpoint.asLong(), new State(now));
    }

    private static final class State {
        private long nextCycle;
        private long disconnectStart;
        private boolean disconnecting;

        private State(long nextCycle) { this.nextCycle = nextCycle; }
    }
}
