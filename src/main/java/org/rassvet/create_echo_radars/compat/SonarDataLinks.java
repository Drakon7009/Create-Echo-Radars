package org.rassvet.create_echo_radars.compat;

import com.happysg.radar.block.datalink.DataLinkBlockItem;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import org.rassvet.create_echo_radars.ModNetworking;
import org.rassvet.create_echo_radars.content.glass.SonarGlass;
import org.rassvet.create_echo_radars.content.glass.SonarGlassNetwork;
import org.rassvet.create_echo_radars.content.glass.SonarGlassNetworkManager;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlock;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarDataLinkBlock;
import org.rassvet.create_echo_radars.content.sonar.SonarVoxelShapes;

public final class SonarDataLinks {
    private SonarDataLinks() {}

    public static void register() {
        com.happysg.radar.api.datalink.RadarDataLinkRegistry.register((level, pos) ->
                level.getBlockEntity(pos) instanceof SonarBlockEntity
                        || level.getBlockEntity(pos) instanceof SonarSignalSummatorBlockEntity
                        || SonarGlass.isGlass(level, pos)
                ? new com.happysg.radar.api.datalink.RadarDataLinkEndpoint(pos,
                        level.getBlockEntity(pos) instanceof SonarBlockEntity
                                ? com.happysg.radar.api.datalink.RadarDataLinkEndpointType.RADAR
                                : com.happysg.radar.api.datalink.RadarDataLinkEndpointType.CUSTOM) : null);
        com.happysg.radar.api.datalink.RadarDataLinkInteractionRegistry.register(context -> {
            var hand = context.player().getMainHandItem() == context.dataLinkStack()
                    ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND;
            var hit = new net.minecraft.world.phys.BlockHitResult(context.clickLocation(),
                    context.clickedFace(), context.clickedPos(), false);
            InteractionResult result = handle(new UseOnContext(context.player(), hand, hit));
            return result == InteractionResult.PASS
                    ? com.happysg.radar.api.datalink.RadarDataLinkInteraction.Result.PASS
                    : result == InteractionResult.FAIL
                    ? com.happysg.radar.api.datalink.RadarDataLinkInteraction.Result.FAIL
                    : com.happysg.radar.api.datalink.RadarDataLinkInteraction.Result.SUCCESS;
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SonarDataLinks::onShiftClick);
    }

    private static void onShiftClick(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof DataLinkBlockItem)) return;
        var be = event.getLevel().getBlockEntity(event.getPos());
        if (!event.getEntity().isShiftKeyDown()) {
            if (be instanceof SonarBlockEntity) {
                InteractionResult result = handle(new UseOnContext(event.getEntity(), event.getHand(), event.getHitVec()));
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
            return;
        }
        if (!(be instanceof SonarBlockEntity sonar && sonar.hasDataLink())
                && !(be instanceof SonarSignalSummatorBlockEntity)) return;
        InteractionResult result = handle(new UseOnContext(event.getEntity(), event.getHand(), event.getHitVec()));
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    private static InteractionResult handle(UseOnContext ctx) {
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() != null && !ctx.getPlayer().isShiftKeyDown()) {
            CompoundTag selection = ctx.getItemInHand().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (selection.contains("SelectedDimension") && !ctx.getLevel().dimension().location().toString()
                    .equals(selection.getString("SelectedDimension"))) {
                error(ctx, "create_radar.data_link.invalid_selection");
                return InteractionResult.FAIL;
            }
        }
        var result = new java.util.concurrent.atomic.AtomicReference<>(InteractionResult.PASS);
        attach(ctx, result::set);
        return result.get();
    }
    private static void attach(UseOnContext ctx,
                               java.util.function.Consumer<InteractionResult> result) {
        BlockPos clickedPos = ctx.getClickedPos();
        if (ctx.getLevel().getBlockEntity(clickedPos) instanceof SonarBlockEntity sonar) {
            if (ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown()
                    && !sonar.hasDataLink()) return;
            useSonar(ctx, sonar, result);
            return;
        }
        if (ctx.getLevel().getBlockEntity(clickedPos) instanceof SonarSignalSummatorBlockEntity summator) {
            useSummator(ctx, summator, result);
            return;
        }
        if (!SonarGlass.isGlass(ctx.getLevel(), clickedPos)) return;
        if (ctx.getPlayer() == null) {
            result.accept(InteractionResult.FAIL);
            return;
        }
        if (ctx.getPlayer().isShiftKeyDown()) return;
        if (ctx.getLevel().isClientSide) {
            result.accept(InteractionResult.SUCCESS);
            return;
        }
        ServerLevel level = (ServerLevel) ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        BlockPos filtererPos = NbtUtils.readBlockPos(tag, "SelectedFiltererPos").orElse(null);
        if (filtererPos == null || !(level.getBlockEntity(filtererPos) instanceof NetworkFiltererBlockEntity)) {
            error(ctx, "message.create_echo_radars.sonar_glass.select_controller");
            result.accept(InteractionResult.FAIL);
            return;
        }
        SonarGlassNetwork.Component component = SonarGlassNetwork.find(level, clickedPos);
        if (component.overflow()) {
            error(ctx, "message.create_echo_radars.sonar_glass.too_large");
            result.accept(InteractionResult.FAIL);
            return;
        }
        NetworkData data = NetworkData.get(level);
        for (BlockPos pos : component.blocks()) {
            if (data.getFiltererForEndpoint(level.dimension(), pos) != null) {
                error(ctx, "message.create_echo_radars.sonar_glass.already_linked");
                result.accept(InteractionResult.FAIL);
                return;
            }
        }
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockPos placedPos = clickedPos.relative(ctx.getClickedFace(), clickedState.canBeReplaced() ? 0 : 1);
        NetworkData.Group group = data.getOrCreateGroup(level.dimension(), filtererPos);
        if (!data.canAttachMonitor(group, clickedPos)) {
            error(ctx, "create_radar.data_link.filter_attach_denied");
            result.accept(InteractionResult.FAIL);
            return;
        }
        InteractionResult placed = ((BlockItem) ctx.getItemInHand().getItem()).place(new BlockPlaceContext(ctx));
        if (placed == InteractionResult.FAIL || !(level.getBlockState(placedPos).getBlock() instanceof DataLinkBlock)) {
            error(ctx, "create_radar.data_link.place_failed");
            result.accept(InteractionResult.FAIL);
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
        result.accept(InteractionResult.SUCCESS);
    }

    private static void useSonar(
            UseOnContext ctx, SonarBlockEntity sonar,
            java.util.function.Consumer<InteractionResult> result) {
        if (ctx.getPlayer() == null) {
            result.accept(InteractionResult.FAIL);
            return;
        }
        if (ctx.getLevel().isClientSide) {
            result.accept(InteractionResult.SUCCESS);
            return;
        }

        ServerLevel level = (ServerLevel) ctx.getLevel();
        if (ctx.getPlayer().isShiftKeyDown()) {
            NetworkData.get(level).onEndpointRemoved(level, sonar.getBlockPos());
            BlockPos linkPos = sonar.getDataLinkPos();
            if (sonar.removeDataLink()) {
                if (linkPos != null && level.getBlockState(linkPos)
                        .is(org.rassvet.create_echo_radars.CreateEchoRadars.SONAR_DATA_LINK.get())) {
                    level.removeBlock(linkPos, false);
                }
                SonarSignalSummatorBlock.returnDataLink(level, sonar.getBlockPos(), ctx.getPlayer());
                ctx.getPlayer().displayClientMessage(Component.translatable(
                        "message.create_echo_radars.sonar.data_link_removed")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
            result.accept(InteractionResult.SUCCESS);
            return;
        }
        if (sonar.hasDataLink()) {
            error(ctx, "message.create_echo_radars.sonar.data_link_already_installed");
            result.accept(InteractionResult.FAIL);
            return;
        }

        ItemStack stack = ctx.getItemInHand();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA,
                CustomData.EMPTY).copyTag();
        BlockPos filtererPos = NbtUtils.readBlockPos(tag,
                "SelectedFiltererPos").orElse(null);
        if (filtererPos == null || !(level.getBlockEntity(filtererPos)
                instanceof NetworkFiltererBlockEntity filterer)) {
            error(ctx, "message.create_echo_radars.sonar_glass.select_controller");
            result.accept(InteractionResult.FAIL);
            return;
        }


        NetworkData data = NetworkData.get(level);
        if (data.getFiltererForEndpoint(level.dimension(), sonar.getBlockPos()) != null) {
            error(ctx, "message.create_echo_radars.sonar.data_link_already_installed");
            result.accept(InteractionResult.FAIL);
            return;
        }
        NetworkData.Group group = data.getOrCreateGroup(level.dimension(), filtererPos);
        if (!data.canAttachRadar(group, sonar.getBlockPos())) {
            error(ctx, "create_radar.data_link.filter_attach_denied");
            result.accept(InteractionResult.FAIL);
            return;
        }

        BlockState clickedState = level.getBlockState(sonar.getBlockPos());
        BlockPos placedPos = sonar.getBlockPos().relative(ctx.getClickedFace(),
                clickedState.canBeReplaced() ? 0 : 1);
        boolean waterlogged = level.getFluidState(placedPos).is(net.minecraft.tags.FluidTags.WATER);
        InteractionResult placed = ((BlockItem) ctx.getItemInHand().getItem()).place(new BlockPlaceContext(ctx));
        if (placed == InteractionResult.FAIL
                || !(level.getBlockState(placedPos).getBlock() instanceof DataLinkBlock)) {
            error(ctx, "create_radar.data_link.place_failed");
            result.accept(InteractionResult.FAIL);
            return;
        }
        BlockState miniState = org.rassvet.create_echo_radars.CreateEchoRadars.SONAR_DATA_LINK.get()
                .defaultBlockState()
                .setValue(SonarDataLinkBlock.FACING, ctx.getClickedFace())
                .setValue(SonarDataLinkBlock.UPSIDE_DOWN,
                        SonarVoxelShapes.hasUpperMount(sonar.getSonarType(), sonar.isUpsideDown()))
                .setValue(SonarDataLinkBlock.WATERLOGGED, waterlogged);
        level.setBlock(placedPos, miniState, 3);

        if (!sonar.installDataLink(filtererPos, placedPos)) {
            level.removeBlock(placedPos, false);
            if (!ctx.getPlayer().getAbilities().instabuild) {
                SonarSignalSummatorBlock.returnDataLink(level, sonar.getBlockPos(), ctx.getPlayer());
            }
            error(ctx, "message.create_echo_radars.sonar.data_link_already_installed");
            result.accept(InteractionResult.FAIL);
            return;
        }

        data.attachRadar(level, group, sonar.getBlockPos());
        data.addDataLinkToGroup(group, placedPos, sonar.getBlockPos());
        filterer.applyFiltersToNetwork();
        stack.remove(DataComponents.CUSTOM_DATA);
        ctx.getPlayer().displayClientMessage(Component.translatable(
                "message.create_echo_radars.sonar.data_link_installed")
                .withStyle(ChatFormatting.GREEN), true);
        result.accept(InteractionResult.SUCCESS);
    }

    private static void useSummator(
            UseOnContext ctx, SonarSignalSummatorBlockEntity summator,
            java.util.function.Consumer<InteractionResult> result) {
        if (ctx.getPlayer() == null) {
            result.accept(InteractionResult.FAIL);
            return;
        }
        int slot = SonarSignalSummatorBlock.slotAt(summator.getBlockState(),
                summator.getBlockPos(), new net.minecraft.world.phys.BlockHitResult(
                        ctx.getClickLocation(), ctx.getClickedFace(),
                        ctx.getClickedPos(), false));
        if (slot < 0) {
            error(ctx, "message.create_echo_radars.signal_summator.aim_at_slot");
            result.accept(InteractionResult.FAIL);
            return;
        }
        if (ctx.getLevel().isClientSide) {
            result.accept(InteractionResult.SUCCESS);
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
            result.accept(InteractionResult.SUCCESS);
            return;
        }
        if (summator.hasAntenna(slot)) {
            error(ctx, "message.create_echo_radars.signal_summator.slot_occupied");
            result.accept(InteractionResult.FAIL);
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
            result.accept(InteractionResult.FAIL);
            return;
        }
        ServerLevel level = (ServerLevel) ctx.getLevel();
        NetworkData.Group group = NetworkData.get(level).getGroup(
                level.dimension(), filtererPos);
        if (group == null || RadarsApi.sonars(level, filtererPos).isEmpty()) {
            error(ctx, "message.create_echo_radars.signal_summator.controller_has_no_sonar");
            result.accept(InteractionResult.FAIL);
            return;
        }
        if (summator.hasFilterer(filtererPos)) {
            error(ctx, "message.create_echo_radars.signal_summator.already_installed");
            result.accept(InteractionResult.FAIL);
            return;
        }
        if (!summator.installAntenna(slot, filtererPos)) {
            error(ctx, "message.create_echo_radars.signal_summator.slot_occupied");
            result.accept(InteractionResult.FAIL);
            return;
        }
        ctx.getPlayer().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        level.gameEvent(ctx.getPlayer(), GameEvent.BLOCK_PLACE, ctx.getClickedPos());
        if (!ctx.getPlayer().getAbilities().instabuild) stack.shrink(1);
        stack.remove(DataComponents.CUSTOM_DATA);
        ctx.getPlayer().displayClientMessage(Component.translatable(
                "message.create_echo_radars.signal_summator.antenna_installed",
                slot + 1).withStyle(ChatFormatting.GREEN), true);
        result.accept(InteractionResult.SUCCESS);
    }

    private static void error(UseOnContext ctx, String key) {
        ctx.getPlayer().displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

}
