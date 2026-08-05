package org.rassvet.create_echo_radars.client;

import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockItem;
import com.happysg.radar.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlock;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlockEntity;

public final class SonarSignalSummatorRenderer
        extends SafeBlockEntityRenderer<SonarSignalSummatorBlockEntity> {
    private static final double ANTENNA_SCALE = 0.22;

    public SonarSignalSummatorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SonarSignalSummatorBlockEntity summator, float partialTick,
                              PoseStack poseStack, MultiBufferSource buffers,
                              int light, int overlay) {
        BlockState summatorState = summator.getBlockState();
        Direction facing = summatorState.getValue(SonarSignalSummatorBlock.FACING);
        BlockState antennaState = ModBlocks.RADAR_LINK.getDefaultState()
                .setValue(DataLinkBlock.FACING, facing)
                .setValue(DataLinkBlock.LINK_STYLE, DataLinkBlock.LinkStyle.RADAR);
        BakedModel antennaModel = Minecraft.getInstance().getBlockRenderer()
                .getBlockModel(antennaState);

        for (int slot = 0; slot < SonarSignalSummatorBlock.SLOT_COUNT; slot++) {
            if (!summator.hasAntenna(slot)) continue;
            Vec3 center = SonarSignalSummatorBlock.slotCenter(summatorState, slot);
            poseStack.pushPose();
            poseStack.translate(center.x - ANTENNA_SCALE * 0.5,
                    center.y - ANTENNA_SCALE * 0.5,
                    center.z - ANTENNA_SCALE * 0.5);
            poseStack.scale((float) ANTENNA_SCALE, (float) ANTENNA_SCALE,
                    (float) ANTENNA_SCALE);
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                    poseStack.last(), buffers.getBuffer(RenderType.cutout()),
                    antennaState, antennaModel, 1.0f, 1.0f, 1.0f,
                    light, overlay, ModelData.EMPTY, RenderType.cutout());
            poseStack.popPose();
        }

        renderHoveredSlot(summator, poseStack, buffers);
    }

    private static void renderHoveredSlot(SonarSignalSummatorBlockEntity summator,
                                          PoseStack poseStack, MultiBufferSource buffers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.hitResult == null
                || minecraft.hitResult.getType() != HitResult.Type.BLOCK) return;
        ItemStack main = minecraft.player.getMainHandItem();
        ItemStack off = minecraft.player.getOffhandItem();
        if (!(main.getItem() instanceof DataLinkBlockItem)
                && !(off.getItem() instanceof DataLinkBlockItem)) return;
        BlockHitResult hit = (BlockHitResult) minecraft.hitResult;
        if (!hit.getBlockPos().equals(summator.getBlockPos())) return;
        int slot = SonarSignalSummatorBlock.slotAt(summator.getBlockState(),
                summator.getBlockPos(), hit);
        if (slot < 0 || summator.hasAntenna(slot)) return;

        Vec3 center = SonarSignalSummatorBlock.slotCenter(
                summator.getBlockState(), slot);
        double half = 0.125;
        AABB outline = new AABB(center.x - half, center.y - half, center.z - half,
                center.x + half, center.y + half, center.z + half);
        LevelRenderer.renderLineBox(poseStack,
                buffers.getBuffer(RenderType.lines()), outline,
                1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(SonarSignalSummatorBlockEntity blockEntity) {
        return false;
    }
}
