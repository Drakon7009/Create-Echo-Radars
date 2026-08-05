package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.content.sonar.SonarBlock;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarType;

public final class MechanicalSonarRenderer extends SafeBlockEntityRenderer<SonarBlockEntity> {
    public static final ModelResourceLocation ROTATING_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID,
                    "block/mechanical_scanning_sonar_rotating"));

    public MechanicalSonarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SonarBlockEntity sonar, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffers, int light, int overlay) {
        if (sonar.getSonarType() != SonarType.MECHANICAL_IMAGING_C) return;
        BakedModel rotatingModel = Minecraft.getInstance().getModelManager()
                .getModel(ROTATING_MODEL);
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
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(-sonar.getMechanicalAngle(partialTick)));
        poseStack.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), buffers.getBuffer(RenderType.cutout()),
                sonar.getBlockState(), rotatingModel, 1.0f, 1.0f, 1.0f,
                light, overlay, ModelData.EMPTY, RenderType.cutout());
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SonarBlockEntity blockEntity) {
        return false;
    }
}
