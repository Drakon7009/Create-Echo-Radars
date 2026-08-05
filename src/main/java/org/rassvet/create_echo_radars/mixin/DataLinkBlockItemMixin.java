package org.rassvet.create_echo_radars.mixin;

import com.happysg.radar.block.datalink.DataLinkBlockItem;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.rassvet.create_echo_radars.content.sonar.SonarBlock;
import org.rassvet.create_echo_radars.ModNetworking;
import org.rassvet.create_echo_radars.content.glass.SonarGlass;
import org.rassvet.create_echo_radars.content.glass.SonarGlassNetwork;
import org.rassvet.create_echo_radars.content.glass.SonarGlassNetworkManager;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlock;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataLinkBlockItem.class, remap = false)
public abstract class DataLinkBlockItemMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void createEchoRadars$attachSonarGlass(UseOnContext ctx,
                                                   CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos clickedPos = ctx.getClickedPos();
        if (ctx.getLevel().getBlockEntity(clickedPos)
                instanceof SonarSignalSummatorBlockEntity summator) {
            createEchoRadars$useSummator(ctx, summator, cir);
            return;
        }
        if (!SonarGlass.isGlass(ctx.getLevel(), clickedPos)) return;
        if (ctx.getPlayer() == null) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        if (ctx.getPlayer().isShiftKeyDown()) return;
        if (ctx.getLevel().isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        ServerLevel level = (ServerLevel) ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        BlockPos filtererPos = NbtUtils.readBlockPos(tag, "SelectedFiltererPos").orElse(null);
        if (filtererPos == null || !(level.getBlockEntity(filtererPos) instanceof NetworkFiltererBlockEntity)) {
            error(ctx, "message.create_echo_radars.sonar_glass.select_controller");
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        SonarGlassNetwork.Component component = SonarGlassNetwork.find(level, clickedPos);
        if (component.overflow()) {
            error(ctx, "message.create_echo_radars.sonar_glass.too_large");
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        NetworkData data = NetworkData.get(level);
        for (BlockPos pos : component.blocks()) {
            if (data.getFiltererForEndpoint(level.dimension(), pos) != null) {
                error(ctx, "message.create_echo_radars.sonar_glass.already_linked");
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }
        }
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockPos placedPos = clickedPos.relative(ctx.getClickedFace(), clickedState.canBeReplaced() ? 0 : 1);
        double range = RadarConfig.server().radarLinkRange.get();
        if (!PhysicsHandler.getWorldPos(level, placedPos).getCenter().closerThan(
                PhysicsHandler.getWorldPos(level, filtererPos).getCenter(), range)
                || !PhysicsHandler.getWorldPos(level, placedPos).getCenter().closerThan(
                PhysicsHandler.getWorldPos(level, clickedPos).getCenter(), range)) {
            error(ctx, "display_link.too_far");
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        NetworkData.Group group = data.getOrCreateGroup(level.dimension(), filtererPos);
        if (!data.canAttachMonitor(group, clickedPos)) {
            error(ctx, "create_radar.data_link.filter_attach_denied");
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        InteractionResult placed = ((BlockItem) (Object) this).place(new BlockPlaceContext(ctx));
        if (placed == InteractionResult.FAIL || !(level.getBlockState(placedPos).getBlock() instanceof DataLinkBlock)) {
            error(ctx, "create_radar.data_link.place_failed");
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        BlockState linkState = level.getBlockState(placedPos);
        if (linkState.hasProperty(DataLinkBlock.LINK_STYLE)) {
            level.setBlock(placedPos, linkState.setValue(
                    DataLinkBlock.LINK_STYLE, DataLinkBlock.LinkStyle.RADAR), 3);
        }
        data.attachMonitor(level, group, clickedPos);
        data.addDataLinkToGroup(group, placedPos, clickedPos);
        stack.remove(DataComponents.CUSTOM_DATA);
        SonarGlassNetworkManager.get(level).refreshNow(clickedPos, level.getGameTime());
        ModNetworking.sendGlassPulse(level, clickedPos, level.getGameTime());
        ctx.getPlayer().displayClientMessage(Component.translatable("display_link.success")
                .withStyle(ChatFormatting.GREEN), true);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    private static void createEchoRadars$useSummator(
            UseOnContext ctx, SonarSignalSummatorBlockEntity summator,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (ctx.getPlayer() == null) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        int slot = SonarSignalSummatorBlock.slotAt(summator.getBlockState(),
                summator.getBlockPos(), new net.minecraft.world.phys.BlockHitResult(
                        ctx.getClickLocation(), ctx.getClickedFace(),
                        ctx.getClickedPos(), false));
        if (slot < 0) {
            error(ctx, "message.create_echo_radars.signal_summator.aim_at_slot");
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        if (ctx.getLevel().isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (ctx.getPlayer().isShiftKeyDown()) {
            if (summator.removeAntenna(slot)) {
                SonarSignalSummatorBlock.returnDataLink(ctx.getLevel(),
                        ctx.getClickedPos(), ctx.getPlayer());
                ctx.getPlayer().displayClientMessage(Component.translatable(
                        "message.create_echo_radars.signal_summator.antenna_removed")
                        .withStyle(ChatFormatting.YELLOW), true);
            } else {
                error(ctx, "message.create_echo_radars.signal_summator.slot_empty");
            }
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (summator.hasAntenna(slot)) {
            error(ctx, "message.create_echo_radars.signal_summator.slot_occupied");
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        ItemStack stack = ctx.getItemInHand();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA,
                CustomData.EMPTY).copyTag();
        BlockPos filtererPos = NbtUtils.readBlockPos(tag,
                "SelectedFiltererPos").orElse(null);
        if (filtererPos == null || !(ctx.getLevel().getBlockEntity(filtererPos)
                instanceof NetworkFiltererBlockEntity)) {
            error(ctx, "message.create_echo_radars.sonar_glass.select_controller");
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        ServerLevel level = (ServerLevel) ctx.getLevel();
        NetworkData.Group group = NetworkData.get(level).getGroup(
                level.dimension(), filtererPos);
        if (group == null || group.radarPos == null
                || !(level.getBlockEntity(group.radarPos) instanceof SonarBlockEntity)) {
            error(ctx, "message.create_echo_radars.signal_summator.controller_has_no_sonar");
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        if (summator.hasFilterer(filtererPos)) {
            error(ctx, "message.create_echo_radars.signal_summator.already_installed");
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        double range = RadarConfig.server().radarLinkRange.get();
        if (!PhysicsHandler.getWorldPos(level, ctx.getClickedPos()).getCenter().closerThan(
                PhysicsHandler.getWorldPos(level, filtererPos).getCenter(), range)) {
            error(ctx, "display_link.too_far");
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        if (!summator.installAntenna(slot, filtererPos)) {
            error(ctx, "message.create_echo_radars.signal_summator.slot_occupied");
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        ctx.getPlayer().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        level.gameEvent(ctx.getPlayer(), GameEvent.BLOCK_PLACE, ctx.getClickedPos());
        if (!ctx.getPlayer().getAbilities().instabuild) stack.shrink(1);
        stack.remove(DataComponents.CUSTOM_DATA);
        ctx.getPlayer().displayClientMessage(Component.translatable(
                "message.create_echo_radars.signal_summator.antenna_installed",
                slot + 1).withStyle(ChatFormatting.GREEN), true);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    private static void error(UseOnContext ctx, String key) {
        ctx.getPlayer().displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    @Redirect(
            method = "getFilterTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;",
                    ordinal = 1
            )
    )
    private static Block createEchoRadars$acceptSonarAsStationaryRadar(BlockState state) {
        Block block = state.getBlock();
        return block instanceof SonarBlock ? ModBlocks.STATIONARY_RADAR.get() : block;
    }
}
