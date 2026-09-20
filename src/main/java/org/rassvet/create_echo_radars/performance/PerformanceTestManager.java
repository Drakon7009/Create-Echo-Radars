package org.rassvet.create_echo_radars.performance;

import com.happysg.radar.block.monitor.MonitorBlock;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.registry.ModBlocks;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.content.sonar.SonarFrame;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorExtension;
import org.rassvet.create_echo_radars.content.sonar.SonarMonitorSnapshot;
import org.rassvet.create_echo_radars.content.sonar.SonarReturn;
import org.rassvet.create_echo_radars.content.sonar.SonarType;
import org.rassvet.create_echo_radars.content.sonar.SonarVoxelDda;
import org.rassvet.create_echo_radars.content.sonar.SonarWaterTraceWorkload;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PerformanceTestManager {
    public static final ResourceKey<Level> TEST_DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID, "performance_test"));
    private static final int[] DISPLAY_COUNTS = {16, 64, 144, 256};
    private static final int WARMUP_TICKS = 60;
    private static final int SAMPLE_TICKS = 100;
    private static final int INITIAL_RAYS = 256;
    private static final int MAX_RAYS = 262_144;
    private static final double NOTICEABLE_TRACE_MS = 50.0;
    private static final double NOTICEABLE_FRAME_MS = 50.0;
    private static final Map<MinecraftServer, Session> SESSIONS = new WeakHashMap<>();
    private static volatile long traceBlackhole;

    private PerformanceTestManager() {}

    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("echo_radars_perf")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start").executes(context -> start(context.getSource(), false, false)))
                .then(Commands.literal("rays").executes(context -> start(context.getSource(), true, false)))
                .then(Commands.literal("water").executes(context -> start(context.getSource(), true, true)))
                .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource()))));
    }

    public static void tick(ServerLevel level) {
        Session session;
        synchronized (SESSIONS) {
            session = SESSIONS.get(level.getServer());
        }
        if (session != null && level.dimension().equals(TEST_DIMENSION)) session.tick(level);
    }

    public static void acceptClientMetrics(ServerPlayer player,
                                           PerformanceNetworking.ClientMetricsPayload payload) {
        Session session;
        synchronized (SESSIONS) {
            session = SESSIONS.get(player.getServer());
        }
        if (session != null && session.playerId.equals(player.getUUID())) {
            session.acceptClientMetrics(payload);
        }
    }

    private static int start(CommandSourceStack source, boolean raysOnly, boolean water) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception error) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
        MinecraftServer server = source.getServer();
        synchronized (SESSIONS) {
            if (SESSIONS.containsKey(server)) {
                source.sendFailure(Component.literal("A performance test is already running."));
                return 0;
            }
        }
        ServerLevel testLevel = server.getLevel(TEST_DIMENSION);
        if (testLevel == null) {
            source.sendFailure(Component.literal(
                    "Performance-test dimension is missing. Install the *-performance-test.jar build."));
            return 0;
        }
        try {
            Session session = new Session(player, raysOnly, water);
            synchronized (SESSIONS) {
                SESSIONS.put(server, session);
            }
            session.start(testLevel);
            return 1;
        } catch (IOException error) {
            CreateEchoRadars.LOGGER.error("Could not start performance test", error);
            source.sendFailure(Component.literal("Could not create the performance report: " + error.getMessage()));
            return 0;
        }
    }

    private static int stop(CommandSourceStack source) {
        Session session;
        synchronized (SESSIONS) {
            session = SESSIONS.get(source.getServer());
        }
        if (session == null) {
            source.sendFailure(Component.literal("No performance test is running."));
            return 0;
        }
        session.finish("stopped by command");
        return 1;
    }

    private static int status(CommandSourceStack source) {
        Session session;
        synchronized (SESSIONS) {
            session = SESSIONS.get(source.getServer());
        }
        if (session == null) {
            source.sendSuccess(() -> Component.literal("No performance test is running."), false);
        } else {
            source.sendSuccess(() -> Component.literal("Performance test: " + session.label
                    + ", tick " + session.stageTicks + "/" + session.stageDuration()), false);
        }
        return 1;
    }

    private enum Stage {
        WARMUP, BASELINE, DISPLAY, RAYS
    }

    private static final class Session {
        private final UUID playerId;
        private final MinecraftServer ownerServer;
        private final ResourceKey<Level> returnDimension;
        private final Vec3 returnPosition;
        private final float returnYaw;
        private final float returnPitch;
        private final GameType returnGameMode;
        private final BufferedWriter report;
        private final Path reportPath;
        private final Map<BlockPos, BlockState> replacedBlocks = new HashMap<>();
        private final List<BlockPos> monitorPositions = new ArrayList<>();
        private final List<Long> traceTimes = new ArrayList<>();
        private Stage stage = Stage.WARMUP;
        private int stageId;
        private int stageTicks;
        private int displayTypeIndex;
        private int displayCountIndex;
        private int rays = INITIAL_RAYS;
        private double[] rayDirections = new double[0];
        private String label = "starting";
        private long clientFrames;
        private long clientElapsedNanos;
        private long clientP95Nanos;
        private long clientMaxNanos;
        private double baselineFps;
        private double baselineP95Millis;
        private boolean closed;

        private final boolean raysOnly;
        private final SonarWaterTraceWorkload waterWorkload;
        private boolean optimizedWater;
        private boolean referenceLimitReached;
        private long referenceChecksum;
        private boolean referenceChecksumAvailable;

        private Session(ServerPlayer player, boolean raysOnly, boolean water) throws IOException {
            this.raysOnly = raysOnly;
            this.waterWorkload = water ? new SonarWaterTraceWorkload() : null;
            playerId = player.getUUID();
            ownerServer = player.getServer();
            returnDimension = player.level().dimension();
            returnPosition = player.position();
            returnYaw = player.getYRot();
            returnPitch = player.getXRot();
            returnGameMode = player.gameMode.getGameModeForPlayer();
            Path directory = player.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("create_echo_radars-performance");
            Files.createDirectories(directory);
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
            reportPath = directory.resolve("performance-" + stamp + ".csv").toAbsolutePath();
            report = Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            report.write("timestamp,event,phase,sonar_type,display_count,rays,avg_fps,"
                    + "p95_frame_ms,max_frame_ms,avg_trace_ms,p95_trace_ms,noticeable_lag,note\n");
            report.flush();
        }

        private void start(ServerLevel testLevel) {
            ServerPlayer player = player();
            if (player == null) {
                finish("player unavailable");
                return;
            }
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(testLevel, 0.5, 79, -38.5, 0, 0);
            announce("Started. Report: " + reportPath);
            beginStage(Stage.WARMUP, "warm-up after dimension load");
        }

        private void tick(ServerLevel level) {
            if (closed) return;
            ServerPlayer player = player();
            if (player == null) {
                finish("player disconnected");
                return;
            }
            if (!player.level().dimension().equals(TEST_DIMENSION)) {
                finish("player left the test dimension");
                return;
            }
            if (stage == Stage.RAYS) runTraceLoad();
            if (closed) return;
            stageTicks++;
            if (stageTicks < stageDuration()) return;
            switch (stage) {
                case WARMUP -> beginStage(Stage.BASELINE, "baseline without displays");
                case BASELINE -> {
                    writeClientSummary("baseline", null, 0, false);
                    baselineFps = currentFps();
                    baselineP95Millis = nanosToMillis(clientP95Nanos);
                    if (raysOnly) {
                        prepareRayDirections();
                        beginStage(Stage.RAYS, rayLabel());
                        break;
                    }
                    displayTypeIndex = 0;
                    displayCountIndex = 0;
                    startDisplayStage(level);
                }
                case DISPLAY -> advanceDisplay(level);
                case RAYS -> advanceRays(level);
            }
        }

        private int stageDuration() {
            return stage == Stage.WARMUP ? WARMUP_TICKS : SAMPLE_TICKS;
        }

        private void startDisplayStage(ServerLevel level) {
            SonarType type = SonarType.values()[displayTypeIndex];
            int count = DISPLAY_COUNTS[displayCountIndex];
            installMonitors(level, count, syntheticSnapshot(type, level.getGameTime()));
            beginStage(Stage.DISPLAY, "display " + type.registryName() + " x" + count);
        }

        private void advanceDisplay(ServerLevel level) {
            SonarType type = SonarType.values()[displayTypeIndex];
            int count = DISPLAY_COUNTS[displayCountIndex];
            boolean lag = isClientLag();
            writeClientSummary("display", type, count, lag);
            displayCountIndex++;
            if (displayCountIndex >= DISPLAY_COUNTS.length) {
                displayCountIndex = 0;
                displayTypeIndex++;
            }
            if (displayTypeIndex < SonarType.values().length) {
                startDisplayStage(level);
            } else {
                restoreMonitors(level);
                prepareRayDirections();
                beginStage(Stage.RAYS, rayLabel());
            }
        }

        private void advanceRays(ServerLevel level) {
            double average = averageMillis(traceTimes);
            double p95 = percentileMillis(traceTimes, 0.95);
            boolean clientLag = isClientLag();
            boolean lag = average >= NOTICEABLE_TRACE_MS
                    || p95 >= NOTICEABLE_TRACE_MS * 1.5 || clientLag;
            String phase = waterWorkload == null ? "ray_trace"
                    : optimizedWater ? "water_optimized" : "water_reference";
            writeRow("result", phase, null, 0, rays, currentFps(),
                    nanosToMillis(clientP95Nanos), nanosToMillis(clientMaxNanos),
                    average, p95, lag, waterWorkload == null ? "exact SonarVoxelDda workload"
                            : "Minecraft water/stone snapshot; no Sable; identical ray directions");
            announce(String.format(java.util.Locale.ROOT,
                    "%s x%d: average %.2f ms/tick, p95 %.2f ms, client %.1f FPS%s",
                    phase, rays, average, p95, currentFps(),
                    lag ? " — noticeable lag threshold reached" : ""));
            if (waterWorkload != null && !optimizedWater) {
                referenceLimitReached |= lag;
                optimizedWater = true;
                beginStage(Stage.RAYS, rayLabel());
                return;
            }
            if (lag || rays >= MAX_RAYS) {
                finish(lag ? "completed at noticeable ray-tracing lag" : "completed at maximum ray count");
                return;
            }
            rays = Math.min(MAX_RAYS, rays * 2);
            optimizedWater = waterWorkload != null && referenceLimitReached;
            referenceChecksumAvailable = false;
            prepareRayDirections();
            beginStage(Stage.RAYS, rayLabel());
        }

        private String rayLabel() {
            return (waterWorkload == null ? "raw DDA"
                    : optimizedWater ? "water optimized" : "water reference") + " x" + rays;
        }

        private void beginStage(Stage next, String nextLabel) {
            stage = next;
            label = nextLabel;
            stageTicks = 0;
            stageId++;
            resetMetrics();
            ServerPlayer player = player();
            if (player != null) PerformanceNetworking.sendControl(player, true, stageId, label);
            announce("Stage: " + label);
            writeRow("start", next.name().toLowerCase(java.util.Locale.ROOT),
                    next == Stage.DISPLAY ? SonarType.values()[displayTypeIndex] : null,
                    next == Stage.DISPLAY ? DISPLAY_COUNTS[displayCountIndex] : 0,
                    next == Stage.RAYS ? rays : 0, 0, 0, 0, 0, 0, false, label);
        }

        private void acceptClientMetrics(PerformanceNetworking.ClientMetricsPayload metrics) {
            if (closed || metrics.stageId() != stageId || metrics.frames() <= 0
                    || metrics.elapsedNanos() <= 0) return;
            clientFrames += metrics.frames();
            clientElapsedNanos += metrics.elapsedNanos();
            clientP95Nanos = Math.max(clientP95Nanos, metrics.p95FrameNanos());
            clientMaxNanos = Math.max(clientMaxNanos, metrics.maxFrameNanos());
        }

        private void resetMetrics() {
            clientFrames = 0;
            clientElapsedNanos = 0;
            clientP95Nanos = 0;
            clientMaxNanos = 0;
            traceTimes.clear();
        }

        private double currentFps() {
            return clientElapsedNanos == 0 ? 0 : clientFrames * 1_000_000_000.0 / clientElapsedNanos;
        }

        private boolean isClientLag() {
            if (clientElapsedNanos == 0) return false;
            double fpsThreshold = baselineFps > 0 ? baselineFps * 0.7 : 45.0;
            double p95Threshold = baselineP95Millis > 0
                    ? Math.max(NOTICEABLE_FRAME_MS, baselineP95Millis * 2) : NOTICEABLE_FRAME_MS;
            return currentFps() < fpsThreshold || nanosToMillis(clientP95Nanos) >= p95Threshold;
        }

        private void writeClientSummary(String phase, SonarType type, int count, boolean lag) {
            double fps = currentFps();
            String note = clientElapsedNanos == 0 ? "no client telemetry" : "synthetic data; no sonar tracing";
            writeRow("result", phase, type, count, 0, fps,
                    nanosToMillis(clientP95Nanos), nanosToMillis(clientMaxNanos),
                    0, 0, lag, note);
            announce(String.format(java.util.Locale.ROOT,
                    "%s%s: %.1f FPS, p95 %.2f ms, max %.2f ms%s",
                    type == null ? "Baseline" : type.registryName() + " x" + count,
                    clientElapsedNanos == 0 ? " (no telemetry)" : "", fps,
                    nanosToMillis(clientP95Nanos), nanosToMillis(clientMaxNanos),
                    lag ? " — noticeable display lag" : ""));
        }

        private void installMonitors(ServerLevel level, int count, SonarMonitorSnapshot snapshot) {
            restoreMonitors(level);
            int width = 16;
            for (int index = 0; index < count; index++) {
                int column = index % width;
                int row = index / width;
                BlockPos pos = new BlockPos((column - 8) * 2 + 1, 64 + row * 2, 0);
                replacedBlocks.putIfAbsent(pos, level.getBlockState(pos));
                level.getChunk(pos);
                BlockState state = ModBlocks.MONITOR.get().defaultBlockState()
                        .setValue(MonitorBlock.FACING, Direction.NORTH);
                level.setBlock(pos, state, 3);
                if (level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
                    SonarMonitorExtension extension = (SonarMonitorExtension) monitor;
                    extension.createEchoRadars$setMonitorDimensions(1, 1);
                    extension.createEchoRadars$setSyntheticSnapshot(snapshot);
                    monitor.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                }
                monitorPositions.add(pos);
            }
        }

        private void restoreMonitors(ServerLevel level) {
            for (BlockPos pos : monitorPositions) {
                BlockState original = replacedBlocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
                level.setBlock(pos, original, 3);
            }
            monitorPositions.clear();
        }

        private void prepareRayDirections() {
            rayDirections = new double[rays * 3];
            double goldenAngle = Math.PI * (3 - Math.sqrt(5));
            for (int index = 0; index < rays; index++) {
                double y = 1 - 2 * (index + 0.5) / rays;
                double radius = Math.sqrt(Math.max(0, 1 - y * y));
                double angle = index * goldenAngle;
                rayDirections[index * 3] = Math.cos(angle) * radius;
                rayDirections[index * 3 + 1] = y;
                rayDirections[index * 3 + 2] = Math.sin(angle) * radius;
            }
        }

        private void runTraceLoad() {
            long started = System.nanoTime();
            if (waterWorkload != null) {
                long checksum = waterWorkload.run(rayDirections, optimizedWater);
                traceTimes.add(System.nanoTime() - started);
                traceBlackhole = checksum;
                if (!optimizedWater) {
                    referenceChecksum = checksum;
                    referenceChecksumAvailable = true;
                } else if (referenceChecksumAvailable && referenceChecksum != checksum) {
                    finish("ERROR: optimized water hit checksum differs from reference");
                }
                return;
            }
            long[] accumulator = {traceBlackhole};
            for (int index = 0; index < rays; index++) {
                int offset = index * 3;
                SonarVoxelDda.traceCells(0.5, 80.5, 0.5,
                        rayDirections[offset], rayDirections[offset + 1], rayDirections[offset + 2],
                        0, 128, (x, y, z, distance, incidence) -> {
                            accumulator[0] += (x * 31L + y * 17L + z) ^ Double.doubleToRawLongBits(incidence);
                            return true;
                        });
            }
            traceBlackhole = accumulator[0];
            traceTimes.add(System.nanoTime() - started);
        }

        private void finish(String reason) {
            if (closed) return;
            closed = true;
            MinecraftServer server = server();
            if (server != null) {
                ServerLevel testLevel = server.getLevel(TEST_DIMENSION);
                if (testLevel != null) restoreMonitors(testLevel);
            }
            announce("Finished: " + reason + ". Report: " + reportPath);
            ServerPlayer player = player();
            if (player != null) {
                PerformanceNetworking.sendControl(player, false, stageId, "finished");
                if (server != null) {
                    ServerLevel destination = server.getLevel(returnDimension);
                    if (destination != null) player.teleportTo(destination, returnPosition.x,
                            returnPosition.y, returnPosition.z, returnYaw, returnPitch);
                }
                player.setGameMode(returnGameMode);
            }
            writeRow("finish", stage.name().toLowerCase(java.util.Locale.ROOT), null,
                    0, stage == Stage.RAYS ? rays : 0, 0, 0, 0, 0, 0, false, reason);
            try {
                report.close();
            } catch (IOException error) {
                CreateEchoRadars.LOGGER.error("Could not close performance report {}", reportPath, error);
            }
            if (server != null) {
                synchronized (SESSIONS) {
                    SESSIONS.remove(server, this);
                }
            }
        }

        private ServerPlayer player() {
            MinecraftServer server = server();
            return server == null ? null : server.getPlayerList().getPlayer(playerId);
        }

        private MinecraftServer server() {
            return ownerServer;
        }

        private void announce(String message) {
            String full = "[Echo Radars Perf] " + message;
            ServerPlayer player = player();
            if (player != null) player.sendSystemMessage(Component.literal(full));
            CreateEchoRadars.LOGGER.info(full);
        }

        private void writeRow(String event, String phase, SonarType type, int displays, int rayCount,
                              double fps, double p95Frame, double maxFrame, double avgTrace,
                              double p95Trace, boolean lag, String note) {
            if (closed && !"finish".equals(event)) return;
            try {
                report.write(String.join(",",
                        LocalDateTime.now().toString(), event, phase,
                        type == null ? "" : type.registryName(), Integer.toString(displays),
                        Integer.toString(rayCount), format(fps), format(p95Frame), format(maxFrame),
                        format(avgTrace), format(p95Trace), Boolean.toString(lag), csv(note)));
                report.write('\n');
                report.flush();
            } catch (IOException error) {
                CreateEchoRadars.LOGGER.error("Could not write performance report {}", reportPath, error);
            }
        }
    }

    private static SonarMonitorSnapshot syntheticSnapshot(SonarType type, long tick) {
        List<SonarFrame> frames = new ArrayList<>();
        int frameCount = type == SonarType.SIDE_SCAN_D ? 10 : 4;
        int horizontalBeams = 64;
        int verticalBeams = 8;
        float horizontalResolution = type.maximumHorizontalAngle() / (float) (horizontalBeams - 1);
        float verticalResolution = type.maximumVerticalAngle() / (float) (verticalBeams - 1);
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            List<SonarReturn> returns = new ArrayList<>(horizontalBeams * verticalBeams);
            for (int vertical = 0; vertical < verticalBeams; vertical++) {
                for (int beam = 0; beam < horizontalBeams; beam++) {
                    float bearing = -type.maximumHorizontalAngle() / 2f
                            + type.maximumHorizontalAngle() * beam / (horizontalBeams - 1f);
                    float elevation = -type.maximumVerticalAngle() / 2f
                            + type.maximumVerticalAngle() * vertical / (verticalBeams - 1f);
                    float wave = (float) ((Math.sin(beam * 0.37 + vertical * 0.71 + frameIndex) + 1) * 0.5);
                    float distance = 0.12f + 0.82f * wave;
                    float intensity = 0.3f + 0.7f * (1 - distance);
                    returns.add(new SonarReturn(beam, Math.round(distance * 127), bearing, elevation,
                            distance, intensity, horizontalResolution, verticalResolution));
                }
            }
            List<Float> scanAngles = type == SonarType.MECHANICAL_IMAGING_C
                    ? returns.stream().map(SonarReturn::bearingDegrees).toList() : List.of();
            frames.add(new SonarFrame(tick - frameIndex, tick - frameIndex * 5L,
                    tick - frameIndex * 5L + 4, true, 1, returns, scanAngles));
        }
        return new SonarMonitorSnapshot(frames, 128, 128,
                type.maximumHorizontalAngle(), type.maximumVerticalAngle(), horizontalBeams,
                type, 0, type == SonarType.MECHANICAL_IMAGING_C ? 12 : 0,
                tick, false, new Vec3(0.5, 80.5, 0.5),
                new Vec3(0, 0, -1), new Vec3(1, 0, 0), new Vec3(0, 1, 0));
    }

    private static double averageMillis(List<Long> values) {
        if (values.isEmpty()) return 0;
        double total = 0;
        for (long value : values) total += value;
        return total / values.size() / 1_000_000.0;
    }

    private static double percentileMillis(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        long[] sorted = values.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(sorted);
        int index = Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * percentile) - 1);
        return nanosToMillis(sorted[index]);
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"").replace('\n', ' ') + '"';
    }
}
