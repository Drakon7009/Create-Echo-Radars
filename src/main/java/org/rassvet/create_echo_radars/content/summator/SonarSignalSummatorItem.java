package org.rassvet.create_echo_radars.content.summator;

import com.happysg.radar.compat.vs2.PhysicsHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import org.rassvet.create_echo_radars.ModNetworking;
import org.jetbrains.annotations.Nullable;
import org.rassvet.create_echo_radars.content.glass.SonarGlass;

/** Selects a sonar-glass aperture before placing its signal summator. */
public final class SonarSignalSummatorItem extends BlockItem {
    private static final String TARGET_POS = "SummatorGlassPos";
    private static final String TARGET_DIMENSION = "SummatorGlassDimension";

    public SonarSignalSummatorItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            clearSelection(level, player, context.getItemInHand());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos clicked = context.getClickedPos();
        if (SonarGlass.isGlass(level, clicked)) {
            if (!level.isClientSide && player != null) {
                setTarget(context.getItemInHand(), level, clicked);
                ModNetworking.sendGlassPulse((ServerLevel) level, clicked, level.getGameTime());
                player.displayClientMessage(Component.translatable(
                        "message.create_echo_radars.signal_summator.glass_selected",
                        clicked.getX(), clicked.getY(), clicked.getZ())
                        .withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                   InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return super.use(level, player, hand);
        clearSelection(level, player, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void clearSelection(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) return;
        clearTarget(stack);
        player.displayClientMessage(Component.translatable(
                "message.create_echo_radars.signal_summator.selection_cleared")
                .withStyle(ChatFormatting.YELLOW), true);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        BlockPos target = target(stack, level);
        if (target == null || !SonarGlass.isGlass(level, target)) {
            error(context, "message.create_echo_radars.signal_summator.select_glass_first");
            return InteractionResult.FAIL;
        }
        if (!withinPlacementRange(level, context.getClickedPos(), target)) {
            error(context, "message.create_echo_radars.signal_summator.glass_too_far");
            return InteractionResult.FAIL;
        }
        InteractionResult result = super.place(context);
        if (!level.isClientSide && result.consumesAction()) clearTarget(stack);
        return result;
    }

    private static void error(BlockPlaceContext context, String key) {
        if (!context.getLevel().isClientSide && context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(Component.translatable(key)
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    public static boolean withinPlacementRange(Level level, BlockPos summator,
                                               BlockPos glass) {
        return PhysicsHandler.getWorldPos(level, summator).getCenter().distanceToSqr(
                PhysicsHandler.getWorldPos(level, glass).getCenter())
                <= SonarSignalSummatorBlock.MAX_GLASS_DISTANCE
                * SonarSignalSummatorBlock.MAX_GLASS_DISTANCE;
    }

    public static void setTarget(ItemStack stack, Level level, BlockPos pos) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA,
                CustomData.EMPTY).copyTag();
        tag.putLong(TARGET_POS, pos.asLong());
        tag.putString(TARGET_DIMENSION, level.dimension().location().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void clearTarget(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA,
                CustomData.EMPTY).copyTag();
        tag.remove(TARGET_POS);
        tag.remove(TARGET_DIMENSION);
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static @Nullable BlockPos target(ItemStack stack, Level level) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA,
                CustomData.EMPTY).copyTag();
        if (!tag.contains(TARGET_POS) || !tag.contains(TARGET_DIMENSION)) return null;
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TARGET_DIMENSION));
        if (dimension == null || !dimension.equals(level.dimension().location())) return null;
        return BlockPos.of(tag.getLong(TARGET_POS));
    }
}
