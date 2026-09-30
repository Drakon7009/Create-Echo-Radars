package org.rassvet.create_echo_radars.client;

import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlock;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlockEntity;

public final class SonarSignalSummatorRenderer
        extends SafeBlockEntityRenderer<SonarSignalSummatorBlockEntity> {
    private static final double ANTENNA_SCALE = 0.22;
    private static final double ANTENNA_PANEL_UP = 0.25 / 16.0;
    private static final float PANEL_TILT_DEGREES = 22.5f;

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
            poseStack.translate(center.x, center.y, center.z);
            switch (facing) {
                case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(PANEL_TILT_DEGREES));
                case EAST -> poseStack.mulPose(Axis.ZP.rotationDegrees(PANEL_TILT_DEGREES));
                case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-PANEL_TILT_DEGREES));
                case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(-PANEL_TILT_DEGREES));
                default -> { }
            }
            poseStack.translate(0, ANTENNA_PANEL_UP, 0);
            poseStack.scale((float) ANTENNA_SCALE, (float) ANTENNA_SCALE,
                    (float) ANTENNA_SCALE);
            poseStack.translate(-0.5, -0.5, -0.5);
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                    poseStack.last(), buffers.getBuffer(RenderType.cutout()),
                    antennaState, antennaModel, 1.0f, 1.0f, 1.0f,
                    light, overlay, ModelData.EMPTY, RenderType.cutout());
            poseStack.popPose();
        }

    }

    @Override
    public boolean shouldRenderOffScreen(SonarSignalSummatorBlockEntity blockEntity) {
        return false;
    }
}
