package org.rassvet.create_echo_radars.content.sonar;

import com.happysg.radar.block.behavior.networks.INetworkNode;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.behavior.RadarScanningBlockBehavior;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.rassvet.create_echo_radars.config.ServerConfig;
import org.rassvet.create_echo_radars.config.SyncedServerConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SonarBlockEntity extends KineticBlockEntity
        implements IRadar, SonarDataSource, MenuProvider, INetworkNode {
    public static final int MIN_RANGE = 16;
    public static final int MAX_RANGE = ServerConfig.DEFAULT_MAXIMUM_SONAR_RANGE;
    public static final int MIN_SECTOR = 15;
    public static final int MAX_SECTOR = 120;
    public static final int MIN_ANGLE = 1;
    public static final int MAX_ANGLE = 180;
    public static final int MIN_TILT = -90;
    public static final int MAX_TILT = 90;

    private final Map<String, RadarTrack> entityTracks = new HashMap<>();
    private static final int ENTITY_VISIBILITY_CACHE_TICKS = 100;
    private final SonarEntityVisibilityCache entityVisibilityCache =
            new SonarEntityVisibilityCache(ENTITY_VISIBILITY_CACHE_TICKS);
    private RadarScanningBlockBehavior scanningBehavior;
    private int range = MAX_RANGE;
    private int horizontalSector;
    private int verticalSector;
    private int tiltAngle;
    private boolean autoHeight = true;
    private float mechanicalAngle;
    private float previousMechanicalAngle;
    private BlockPos lastKnownNetworkPos;
    @Nullable
    private BlockPos dataLinkFiltererPos;
    @Nullable
    private BlockPos dataLinkPos;

    public SonarBlockEntity(BlockPos pos, BlockState state) {
        super(org.rassvet.create_echo_radars.CreateEchoRadars.SONAR_BLOCK_ENTITY.get(), pos, state);
        lastKnownNetworkPos = pos.immutable();
        SonarType type = sonarType(state);
        horizontalSector = type.defaultHorizontalAngle();
        verticalSector = type.defaultVerticalAngle();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        scanningBehavior = new RadarScanningBlockBehavior(this);
        scanningBehavior.setTrackExpiration(40);
        scanningBehavior.setYRange(MAX_RANGE);
        scanningBehavior.setScanFlags(false, true, false, false, false, false, false);
        behaviours.add(scanningBehavior);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;

        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && level.getGameTime() % 20 == 0) {
            updateNetworkPosition(serverLevel);
        }

        previousMechanicalAngle = mechanicalAngle;
        float mechanicalSpeed = 0;
        if (getSonarType() == SonarType.MECHANICAL_IMAGING_C) {
            mechanicalSpeed = mechanicalAngularSpeed();
            mechanicalAngle = SonarRotation.advance(mechanicalAngle, mechanicalSpeed);
            if (!level.isClientSide && level.getGameTime() % 20 == 0) setChanged();
        }

        updateScanner();
        if (level.isClientSide) return;
        if (getSonarType() == SonarType.MECHANICAL_IMAGING_C && isRunning()) {
            for (float scanAngle : SonarRotation.crossedScanSteps(
                    previousMechanicalAngle, mechanicalAngle, mechanicalSpeed,
                    SonarRotation.MECHANICAL_SCAN_STEP_DEGREES)) {
                SonarOrientation unTilted = unTiltedOrientation(scanAngle);
                SonarOrientation scanOrientation = applyTilt(unTilted, tiltAngle);
                Vec3 localCenter = Vec3.atCenterOf(worldPosition);
                Vec3 worldCenter = PhysicsHandler.getWorldVec(level, localCenter);
                Vec3 scanOrigin = emitterPosition(worldCenter, getSonarType(), unTilted, scanAngle);
                scanEntitiesAtScanStart(scanOrigin, scanOrientation, getSonarRange(),
                        Math.round(SonarRotation.MECHANICAL_SCAN_STEP_DEGREES),
                        verticalSector, autoHeight);
            }
        }
        SonarScanManager.get((net.minecraft.server.level.ServerLevel) level).touch(this);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level instanceof ServerLevel serverLevel) {
            reconcileDataLinkPosition();
            updateNetworkPosition(serverLevel);
        }
    }

    private void updateNetworkPosition(ServerLevel serverLevel) {
        if (lastKnownNetworkPos.equals(worldPosition)) return;
        if (NetworkData.get(serverLevel).updateRadarPosition(serverLevel.dimension(),
                lastKnownNetworkPos, worldPosition)) {
            lastKnownNetworkPos = worldPosition.immutable();
            setChanged();
        }
    }

    void scanEntitiesAtScanStart(Vec3 origin, SonarOrientation orientation,
                                 int scanRange, int horizontalAngle, int verticalAngle,
                                 boolean scanAutoHeight) {
        if (level == null || level.isClientSide) return;
        boolean mechanical = getSonarType() == SonarType.MECHANICAL_IMAGING_C;
        if (!mechanical) {
            entityTracks.entrySet().removeIf(entry ->
                    level.getGameTime() - entry.getValue().scannedTime() > 20);
        }
        if (!isRunning()) {
            entityVisibilityCache.clear();
            return;
        }
        if (!ServerConfig.entityOcclusionCheck()) entityVisibilityCache.clear();

        long gameTime = level.getGameTime();
        Set<String> seenInSector = mechanical ? new HashSet<>() : Set.of();
        int scanYRange = displayYRange(scanRange, scanAutoHeight, verticalAngle, tiltAngle);
        net.minecraft.world.phys.AABB bounds = new net.minecraft.world.phys.AABB(origin, origin).inflate(scanRange);
        for (Entity entity : level.getEntities((Entity) null, bounds, Entity::isAlive)) {
            Vec3 target = entity.getBoundingBox().getCenter();
            Vec3 relative = target.subtract(origin);
            if (!insideScanVolume(relative, orientation, horizontalAngle, verticalAngle, scanRange)) continue;
            if (scanAutoHeight && Math.abs(SonarMath.project(relative, orientation).up()) > scanYRange) continue;
            String entityId = entity.getUUID().toString();
            if (!isEntityVisible(entityId, origin, target)) {
                entityTracks.remove(entityId);
                continue;
            }
            if (mechanical) seenInSector.add(entityId);
            entityTracks.compute(entityId, (id, track) -> {
                if (track == null) {
                    RadarTrack created = new RadarTrack(entity);
                    created.setPosition(target);
                    return created;
                }
                track.updateRadarTrack(entity);
                track.setPosition(target);
                return track;
            });
        }
        if (mechanical) {
            MechanicalTrackLatch.clearMissingSweptTracks(entityTracks, seenInSector,
                    track -> insideScanVolume(track.position().subtract(origin), orientation,
                            horizontalAngle, verticalAngle, scanRange));
        }
        entityVisibilityCache.prune(gameTime);
    }

    private void updateScanner() {
        if (scanningBehavior == null) return;
        Vec3 forward = worldForward();
        scanningBehavior.setRange(getSonarRange());
        scanningBehavior.setFov(horizontalSector);
        scanningBehavior.setYRange(displayYRange());
        scanningBehavior.setScanPos(emitterPosition());
        scanningBehavior.setAngle((Math.toDegrees(Math.atan2(forward.x, forward.z)) + 360) % 360);
        scanningBehavior.setRunning(isRunning());
    }

    public int displayYRange() {
        return displayYRange(getSonarRange(), autoHeight, verticalSector, tiltAngle);
    }

    private static int displayYRange(int range, boolean autoHeight) {
        return displayYRange(range, autoHeight, 20, 0);
    }

    private static int displayYRange(int range, boolean autoHeight, int verticalAngle, int tiltAngle) {
        if (!autoHeight) return range;
        double halfAngle = Math.min(89, Math.abs(tiltAngle) + verticalAngle / 2.0);
        int coneHeight = (int) Math.ceil(Math.tan(Math.toRadians(halfAngle)) * range) + 2;
        return Mth.clamp(coneHeight, 2, range);
    }

    public Vec3 emitterPosition() {
        Vec3 localCenter = Vec3.atCenterOf(worldPosition);
        Vec3 worldCenter = level == null ? localCenter : PhysicsHandler.getWorldVec(level, localCenter);
        return emitterPosition(worldCenter, getSonarType(), unTiltedOrientation(), mechanicalAngle);
    }

    public Vec3 displayOrigin() {
        if (getSonarType() != SonarType.MECHANICAL_IMAGING_C) return emitterPosition();
        Vec3 localCenter = Vec3.atCenterOf(worldPosition);
        Vec3 worldCenter = level == null ? localCenter : PhysicsHandler.getWorldVec(level, localCenter);
        return worldCenter.add(unTiltedOrientation().up().scale(0.25));
    }

    public SonarOrientation orientation() {
        return orientation(tiltAngle);
    }

    public SonarOrientation orientation(int previewTiltAngle) {
        SonarOrientation unTilted = unTiltedOrientation();
        return getSonarType() == SonarType.SIDE_SCAN_D ? unTilted
                : applyTilt(unTilted, clampTilt(getSonarType(), previewTiltAngle));
    }

    public SonarOrientation displayOrientation() {
        return getSonarType() == SonarType.MECHANICAL_IMAGING_C
                ? baseOrientation() : orientation();
    }

    public SonarOrientation unTiltedOrientation() {
        return unTiltedOrientation(mechanicalAngle);
    }

    private SonarOrientation unTiltedOrientation(float scanAngle) {
        SonarOrientation base = baseOrientation();
        return switch (getSonarType()) {
            case ECHO_SOUNDER_A -> new SonarOrientation(base.up().scale(-1), base.right(), base.forward());
            case MECHANICAL_IMAGING_C -> new SonarOrientation(
                    base.direction(scanAngle, 0),
                    base.direction(scanAngle + 90, 0), base.up());
            case SIDE_SCAN_D, FORWARD_LOOKING_F -> base;
        };
    }

    private SonarOrientation baseOrientation() {
        SonarOrientation base = SonarOrientation.of(this);
        if (!isUpsideDown()) return base;
        return new SonarOrientation(base.forward().scale(-1), base.right(), base.up().scale(-1));
    }

    public static Vec3 emitterPosition(Vec3 blockCenter, SonarType type,
                                       SonarOrientation unTiltedOrientation,
                                       float mechanicalAngle) {
        return switch (type) {
            case ECHO_SOUNDER_A -> blockCenter.add(unTiltedOrientation.forward().scale(0.501));
            case MECHANICAL_IMAGING_C -> blockCenter
                    .add(unTiltedOrientation.forward().scale(
                            SonarRotation.mechanicalEmitterDistance(mechanicalAngle)))
                    .add(unTiltedOrientation.up().scale(0.25));
            case SIDE_SCAN_D -> blockCenter.add(unTiltedOrientation.up().scale(-1.0 / 32.0));
            case FORWARD_LOOKING_F -> blockCenter.add(unTiltedOrientation.forward().scale(0.55));
        };
    }

    public static SonarOrientation applyTilt(SonarOrientation orientation, float tiltDegrees) {
        return new SonarOrientation(
                orientation.direction(0, tiltDegrees),
                orientation.right(),
                orientation.direction(0, tiltDegrees + 90));
    }

    public static int minimumTilt(SonarType type) {
        return type == SonarType.SIDE_SCAN_D ? SideScanGeometry.MIN_TILT_DEGREES : MIN_TILT;
    }

    public static int maximumTilt(SonarType type) {
        return type == SonarType.SIDE_SCAN_D ? SideScanGeometry.MAX_TILT_DEGREES : MAX_TILT;
    }

    public static int clampTilt(SonarType type, int tiltDegrees) {
        return Mth.clamp(tiltDegrees, minimumTilt(type), maximumTilt(type));
    }

    public Vec3 worldForward() {
        return orientation().forward();
    }

    public boolean isEmitterSubmerged() {
        if (level == null) return false;
        if (level.getFluidState(worldPosition).is(FluidTags.WATER)) return true;
        if (getSonarType() != SonarType.SIDE_SCAN_D) {
            return level.getFluidState(BlockPos.containing(emitterPosition())).is(FluidTags.WATER);
        }
        Vec3 center = emitterPosition();
        Vec3 forward = unTiltedOrientation().forward().scale(SideScanGeometry.EMITTER_FORWARD_OFFSET);
        return level.getFluidState(BlockPos.containing(center.add(forward))).is(FluidTags.WATER)
                || level.getFluidState(BlockPos.containing(center.subtract(forward))).is(FluidTags.WATER);
    }

    @Override
    public Collection<RadarTrack> getTracks() {
        if (scanningBehavior == null) return List.copyOf(entityTracks.values());
        Vec3 origin = emitterPosition();
        SonarOrientation orientation = orientation();
        java.util.stream.Stream<RadarTrack> sableTracks = (getSonarType() == SonarType.MECHANICAL_IMAGING_C
                ? java.util.stream.Stream.<RadarTrack>empty()
                : scanningBehavior.getRadarTracks().stream())
                .filter(track -> insideScanVolume(track.position().subtract(origin), orientation,
                        horizontalSector, verticalSector, getSonarRange()))
                .filter(track -> !autoHeight
                        || Math.abs(SonarMath.project(track.position().subtract(origin), orientation).up())
                        <= displayYRange())
                .filter(track -> isSableTrackVisible(track.id(), origin, track.position()));
        return java.util.stream.Stream.concat(entityTracks.values().stream(), sableTracks)
                .collect(java.util.stream.Collectors.toMap(RadarTrack::id, track -> track,
                        (first, second) -> first))
                .values();
    }

    private boolean insideScanVolume(Vec3 relative, SonarOrientation orientation,
                                     int horizontalAngle, int verticalAngle, int scanRange) {
        if (relative.length() > scanRange) return false;
        if (getSonarType() != SonarType.SIDE_SCAN_D) {
            return SonarMath.insideCone(relative, orientation,
                    horizontalAngle, verticalAngle, scanRange);
        }
        if (relative.lengthSqr() < 1.0e-8) return true;
        Vec3 direction = relative.normalize();
        Vec3 negativeCenter = SideScanGeometry.beamOrientation(orientation,
                -SideScanGeometry.CENTER_YAW_DEGREES, tiltAngle).forward();
        Vec3 positiveCenter = SideScanGeometry.beamOrientation(orientation,
                SideScanGeometry.CENTER_YAW_DEGREES, tiltAngle).forward();
        double halfDiagonal = Math.toRadians(Math.min(89,
                Math.hypot(horizontalAngle, verticalAngle) * 0.5));
        double threshold = Math.cos(halfDiagonal);
        return direction.dot(negativeCenter) >= threshold
                || direction.dot(positiveCenter) >= threshold;
    }

    private boolean isEntityVisible(String entityId, Vec3 origin, Vec3 target) {
        return isTrackVisible(entityId, origin, target, false);
    }

    private boolean isSableTrackVisible(String trackId, Vec3 origin, Vec3 target) {
        return isTrackVisible(trackId, origin, target, true);
    }

    private boolean isTrackVisible(String trackId, Vec3 origin, Vec3 target,
                                   boolean ignoreTrackedSableConstruction) {
        if (level == null) return false;
        if (level.isClientSide || !ServerConfig.entityOcclusionCheck()) return true;
        BlockPos sonarCell = BlockPos.containing(origin);
        BlockPos entityCell = BlockPos.containing(target);
        return entityVisibilityCache.resolve(trackId, visibilityCell(sonarCell), visibilityCell(entityCell),
                level.getGameTime(), () -> hasClearEntityRay(target, origin,
                        ignoreTrackedSableConstruction ? trackId : null));
    }

    private boolean hasClearEntityRay(Vec3 entityPosition, Vec3 sonarPosition,
                                      @Nullable String ignoredSableTrackId) {
        ClipContext context = new ClipContext(entityPosition, sonarPosition,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        if (ignoredSableTrackId != null) {
            SableSonarCompat.ignoreTrackedSubLevel(context, ignoredSableTrackId);
        }
        BlockHitResult hit = level.clip(context);
        return hit.getType() == HitResult.Type.MISS;
    }

    private static SonarEntityVisibilityCache.Cell visibilityCell(BlockPos pos) {
        return new SonarEntityVisibilityCache.Cell(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public int getSonarRange() {
        return Math.min(getConfiguredSonarRange(), SonarRangeLimit.effectiveMaximumRange(
                getSonarType(), horizontalSector, verticalSector,
                maximumConfiguredRange(), angleRangeReductionEnabled()));
    }

    public int getConfiguredSonarRange() {
        return Math.min(range, maximumConfiguredRange());
    }

    private int maximumConfiguredRange() {
        return level != null && level.isClientSide
                ? SyncedServerConfig.maximumSonarRange() : ServerConfig.maximumSonarRange();
    }

    private boolean angleRangeReductionEnabled() {
        return level != null && level.isClientSide
                ? SyncedServerConfig.angleRangeReduction() : ServerConfig.angleRangeReduction();
    }

    @Override
    public int getHorizontalSector() {
        return horizontalSector;
    }

    @Override
    public int getVerticalSector() {
        return verticalSector;
    }

    public int getTiltAngle() {
        return tiltAngle;
    }

    public boolean isUpsideDown() {
        return getBlockState().getValue(SonarBlock.UPSIDE_DOWN);
    }

    @Override
    public SonarType getSonarType() {
        return sonarType(getBlockState());
    }

    public boolean isAutoHeight() {
        return autoHeight;
    }

    public boolean applySettings(Player player, int newRange, int newHorizontalSector,
                                 int newVerticalSector, int newTiltAngle, boolean newAutoHeight) {
        SonarType type = getSonarType();
        if (player.distanceToSqr(Vec3.atCenterOf(worldPosition)) > 64
                || newRange < MIN_RANGE || newRange > ServerConfig.maximumSonarRange()
                || newHorizontalSector < MIN_ANGLE
                || newHorizontalSector > type.maximumHorizontalAngle()
                || newVerticalSector < MIN_ANGLE
                || newVerticalSector > type.maximumVerticalAngle()
                || newTiltAngle < minimumTilt(type)
                || newTiltAngle > maximumTilt(type)) {
            return false;
        }
        range = Math.min(newRange, SonarRangeLimit.effectiveMaximumRange(type,
                newHorizontalSector, newVerticalSector,
                ServerConfig.maximumSonarRange(), ServerConfig.angleRangeReduction()));
        horizontalSector = newHorizontalSector;
        verticalSector = newVerticalSector;
        tiltAngle = newTiltAngle;
        autoHeight = newAutoHeight;
        setChanged();
        sendData();
        return true;
    }

    public boolean applySettings(Player player, int newRange, int newSector) {
        return applySettings(player, newRange, newSector, verticalSector, tiltAngle, autoHeight);
    }

    @Override
    public float getRange() {
        return getSonarRange();
    }

    @Override
    public boolean isRunning() {
        return isEmitterSubmerged()
                && (getSonarType() != SonarType.MECHANICAL_IMAGING_C || Math.abs(getSpeed()) > 1.0e-4f);
    }

    @Override
    public BlockPos getWorldPos() {
        return worldPosition;
    }

    @Override
    public float getGlobalAngle() {
        Vec3 forward = worldForward();
        return (float) ((Math.toDegrees(Math.atan2(forward.x, forward.z)) + 360) % 360);
    }

    @Override
    public String getRadarType() {
        return "sonar";
    }

    @Override
    public Direction getradarDirection() {
        return getBlockState().getValue(SonarBlock.FACING);
    }

    @Override
    public void onNetworkDisconnected() {
        // The radar keeps scanning; only the Create: Radars network association is removed.
    }

    public boolean hasDataLink() {
        return dataLinkFiltererPos != null;
    }

    @Nullable
    public BlockPos getDataLinkFiltererPos() {
        return dataLinkFiltererPos;
    }

    @Nullable
    public BlockPos getDataLinkPos() {
        return dataLinkPos;
    }

    public boolean installDataLink(BlockPos filtererPos, BlockPos linkPos) {
        if (dataLinkFiltererPos != null) return false;
        dataLinkFiltererPos = filtererPos.immutable();
        dataLinkPos = linkPos.immutable();
        setChanged();
        sendData();
        return true;
    }

    public boolean removeDataLink() {
        if (dataLinkFiltererPos == null) return false;
        dataLinkFiltererPos = null;
        dataLinkPos = null;
        setChanged();
        sendData();
        return true;
    }

    private void reconcileDataLinkPosition() {
        if (level == null || dataLinkFiltererPos == null) return;
        if (dataLinkPos != null && level.getBlockState(dataLinkPos)
                .is(org.rassvet.create_echo_radars.CreateEchoRadars.SONAR_DATA_LINK.get())) return;
        for (Direction direction : Direction.values()) {
            BlockPos candidate = worldPosition.relative(direction);
            BlockState state = level.getBlockState(candidate);
            if (state.is(org.rassvet.create_echo_radars.CreateEchoRadars.SONAR_DATA_LINK.get())
                    && state.getValue(SonarDataLinkBlock.FACING) == direction) {
                dataLinkPos = candidate.immutable();
                setChanged();
                return;
            }
        }
    }

    public boolean ownsDataLink(BlockPos linkPos) {
        return dataLinkPos != null && dataLinkPos.equals(linkPos);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Range", range);
        tag.putInt("Sector", horizontalSector);
        tag.putInt("VerticalSector", verticalSector);
        tag.putInt("TiltAngle", tiltAngle);
        tag.putBoolean("AutoHeight", autoHeight);
        tag.putFloat("MechanicalAngle", mechanicalAngle);
        tag.putLong("LastKnownNetworkPos", lastKnownNetworkPos.asLong());
        if (dataLinkFiltererPos != null) {
            tag.putLong("DataLinkFiltererPos", dataLinkFiltererPos.asLong());
        }
        if (dataLinkPos != null) {
            tag.putLong("DataLinkPos", dataLinkPos.asLong());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        range = Mth.clamp(tag.contains("Range") ? tag.getInt("Range") : MAX_RANGE,
                MIN_RANGE, ServerConfig.MAXIMUM_SONAR_RANGE_LIMIT);
        SonarType type = getSonarType();
        horizontalSector = Mth.clamp(tag.contains("Sector") ? tag.getInt("Sector")
                : type.defaultHorizontalAngle(), MIN_ANGLE, type.maximumHorizontalAngle());
        verticalSector = Mth.clamp(tag.contains("VerticalSector") ? tag.getInt("VerticalSector")
                : type.defaultVerticalAngle(), MIN_ANGLE, type.maximumVerticalAngle());
        tiltAngle = clampTilt(type, tag.getInt("TiltAngle"));
        autoHeight = !tag.contains("AutoHeight") || tag.getBoolean("AutoHeight");
        mechanicalAngle = SonarRotation.wrap(tag.getFloat("MechanicalAngle"));
        previousMechanicalAngle = mechanicalAngle;
        if (tag.contains("LastKnownNetworkPos")) {
            lastKnownNetworkPos = BlockPos.of(tag.getLong("LastKnownNetworkPos"));
        }
        dataLinkFiltererPos = tag.contains("DataLinkFiltererPos", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("DataLinkFiltererPos")) : null;
        dataLinkPos = tag.contains("DataLinkPos", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("DataLinkPos")) : null;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.create_echo_radars." + getSonarType().registryName());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SonarMenu(containerId, inventory, this);
    }

    public float getMechanicalAngle(float partialTick) {
        return Mth.rotLerp(partialTick, previousMechanicalAngle, mechanicalAngle);
    }

    public float mechanicalAngle() {
        return mechanicalAngle;
    }

    public float mechanicalAngularSpeed() {
        // The inverted model's local Y axis points opposite Create's shaft axis.
        // Keep the scan and the rotating housing aligned with the visible shaft.
        return SonarRotation.angularSpeed(isUpsideDown() ? getSpeed() : -getSpeed(),
                AllConfigs.server().kinetics.maxRotationSpeed.get());
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        if (level != null && !level.isClientSide
                && getSonarType() == SonarType.MECHANICAL_IMAGING_C) {
            setChanged();
            sendData();
        }
    }

    private static SonarType sonarType(BlockState state) {
        return state.getBlock() instanceof SonarBlock sonar
                ? sonar.sonarType() : SonarType.FORWARD_LOOKING_F;
    }
}
