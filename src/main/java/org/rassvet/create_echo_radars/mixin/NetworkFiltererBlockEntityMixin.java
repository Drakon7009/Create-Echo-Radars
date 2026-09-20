package org.rassvet.create_echo_radars.mixin;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.rassvet.create_echo_radars.content.glass.SonarGlassNetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkFiltererBlockEntity.class, remap = false)
public abstract class NetworkFiltererBlockEntityMixin extends BlockEntity {
    @Shadow
    private BlockPos lastKnownPos;

    protected NetworkFiltererBlockEntityMixin(BlockEntityType<?> type,
                                               BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "onLoad", at = @At("HEAD"))
    private void createEchoRadars$moveSummatorFiltererLink(CallbackInfo ci) {
        createEchoRadars$updateMovedPosition();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private static void createEchoRadars$moveSummatorFiltererLinkOnTick(
            Level level, BlockPos pos, BlockState state,
            NetworkFiltererBlockEntity blockEntity, CallbackInfo ci) {
        ((NetworkFiltererBlockEntityMixin) (Object) blockEntity)
                .createEchoRadars$updateMovedPosition();
    }

    @Unique
    private void createEchoRadars$updateMovedPosition() {
        if (!(level instanceof ServerLevel serverLevel)
                || lastKnownPos == null || lastKnownPos.equals(worldPosition)) return;

        NetworkData data = NetworkData.get(serverLevel);
        boolean movedNetwork = data.updateFiltererPosition(
                serverLevel.dimension(), lastKnownPos, worldPosition);
        if (!movedNetwork) {
            NetworkData.Group oldGroup = data.getGroup(
                    serverLevel.dimension(), lastKnownPos);
            NetworkData.Group destinationGroup = data.getGroup(
                    serverLevel.dimension(), worldPosition);
            if (oldGroup != null && createEchoRadars$isEmpty(destinationGroup)) {
                // Sable may call onLoad before restoring LastKnownPos from NBT.
                // Create Radars then creates an empty group at the destination;
                // remove only that empty placeholder and retry the real move.
                data.dissolveNetworkForBrokenController(serverLevel, worldPosition);
                movedNetwork = data.updateFiltererPosition(
                        serverLevel.dimension(), lastKnownPos, worldPosition);
            }
        }
        if (movedNetwork) {
            SonarGlassNetworkManager.get(serverLevel).remapFilterer(
                    lastKnownPos, worldPosition, serverLevel.getGameTime());
        }
        lastKnownPos = worldPosition.immutable();
        setChanged();
    }

    @Unique
    private static boolean createEchoRadars$isEmpty(NetworkData.Group group) {
        return group != null && group.radarPos == null
                && group.monitorEndpoints.isEmpty()
                && group.weaponEndpoints.isEmpty()
                && group.usedWeaponMounts.isEmpty()
                && group.dataLinks.isEmpty();
    }
}
