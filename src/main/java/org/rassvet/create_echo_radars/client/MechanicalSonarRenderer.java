package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.content.sonar.SonarBlock;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarType;

public final class MechanicalSonarRenderer extends SafeBlockEntityRenderer<SonarBlockEntity> {
    public MechanicalSonarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SonarBlockEntity sonar, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffers, int light, int overlay) {
        if (sonar.getSonarType() != SonarType.MECHANICAL_IMAGING_C) return;
        BlockState headState = CreateEchoRadars.SONAR.get().defaultBlockState()
                .setValue(SonarBlock.FACING, Direction.NORTH);
        Direction facing = sonar.getBlockState().getValue(SonarBlock.FACING);
        float placementAngle = switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-placementAngle));
        if (sonar.isUpsideDown()) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(180));
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(-sonar.getMechanicalAngle(partialTick)));
        poseStack.translate(-0.5, -0.5, -0.5);
        poseStack.translate(0.18, 0.38, 0.18);
        poseStack.scale(0.64f, 0.64f, 0.64f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                headState, poseStack, buffers, light, overlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SonarBlockEntity blockEntity) {
        return false;
    }
}
