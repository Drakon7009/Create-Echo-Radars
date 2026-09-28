package org.rassvet.create_echo_radars.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
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
    public static final ModelResourceLocation SHAFT_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID,
                    "block/mechanical_scanning_sonar_shaft"));
    public static final ModelResourceLocation ROTATING_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(CreateEchoRadars.MOD_ID,
                    "block/mechanical_scanning_sonar_rotating"));

    public MechanicalSonarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SonarBlockEntity sonar, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffers, int light, int overlay) {
        if (sonar.getSonarType() != SonarType.MECHANICAL_IMAGING_C) return;
        BakedModel shaftModel = Minecraft.getInstance().getModelManager()
                .getModel(SHAFT_MODEL);
        BakedModel rotatingModel = Minecraft.getInstance().getModelManager()
                .getModel(ROTATING_MODEL);
        Direction inputFace = sonar.isUpsideDown() ? Direction.UP : Direction.DOWN;
        int shaftLight = LevelRenderer.getLightColor(sonar.getLevel(),
                sonar.getBlockPos().relative(inputFace));
        float shaftAngle = (float) Math.toDegrees(KineticBlockEntityRenderer.getAngleForBe(
                sonar, sonar.getBlockPos(), Direction.Axis.Y));
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(shaftAngle));
        poseStack.mulPose(Axis.XP.rotationDegrees(inputFace == Direction.UP ? -90 : 90));
        poseStack.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), buffers.getBuffer(RenderType.solid()),
                sonar.getBlockState(), shaftModel, 1.0f, 1.0f, 1.0f,
                shaftLight, overlay, ModelData.EMPTY, RenderType.solid());
        poseStack.popPose();
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
        float visualAngle;
        if (sonar.getLevel() instanceof PonderLevel) {
            // Ponder can change kinetic speed long after its scene clock starts.
            // The inverted model's local Y axis points opposite Create's shaft axis.
            visualAngle = sonar.isUpsideDown() ? -shaftAngle : shaftAngle;
        } else {
            float shaftOffset = KineticBlockEntityRenderer.getRotationOffsetForPosition(
                    sonar, sonar.getBlockPos(), Direction.Axis.Y);
            visualAngle = -sonar.getMechanicalAngle(partialTick)
                    + (sonar.isUpsideDown() ? -shaftOffset : shaftOffset);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(visualAngle));
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
