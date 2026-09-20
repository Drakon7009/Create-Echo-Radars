package org.rassvet.create_echo_radars.ponder;

import com.happysg.radar.registry.ModBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.instruction.RotateSceneInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.rassvet.create_echo_radars.CreateEchoRadars;
import org.rassvet.create_echo_radars.content.sonar.SonarBlock;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlock;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlockEntity;

public final class EchoRadarsPonderScenes {
    private EchoRadarsPonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(
                        CreateEchoRadars.SONAR.getId(),
                        CreateEchoRadars.ECHO_SOUNDER.getId(),
                        CreateEchoRadars.SIDE_SCAN_SONAR.getId())
                .addStoryBoard("sonar_types", EchoRadarsPonderScenes::sonarTypes,
                        EchoRadarsPonderTags.ECHO_RADARS);

        helper.addStoryBoard(CreateEchoRadars.MECHANICAL_SCANNING_SONAR.getId(),
                "mechanical_sonar", EchoRadarsPonderScenes::mechanicalSonar,
                EchoRadarsPonderTags.ECHO_RADARS);

        helper.forComponents(
                        CreateEchoRadars.COPPER_SONAR_GLASS.getId(),
                        CreateEchoRadars.IRON_SONAR_GLASS.getId(),
                        CreateEchoRadars.SIGNAL_SUMMATOR.getId())
                .addStoryBoard("sonar_glass", EchoRadarsPonderScenes::sonarGlass,
                        EchoRadarsPonderTags.ECHO_RADARS);
    }

    public static void sonarTypes(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("sonar_types", "Installing and Configuring a Sonar");
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(0.58f);

        // Start from the intended side without spending the opening seconds
        // rotating there from Ponder's default camera angle.
        scene.addInstruction(ponderScene -> {
            ponderScene.getTransform().xRotation.startWithValue(-35);
            ponderScene.getTransform().yRotation.startWithValue(-35);
        });

        BlockPos sonar = util.grid().at(7, 1, 2);
        Selection nearWall = util.select().fromTo(0, 1, 4, 10, 3, 4);
        Selection bowCutaway = util.select().fromTo(8, 1, 0, 10, 3, 4);

        // Assemble the supplied ship the same way Create: Radars builds its
        // Ponder contraptions: one horizontal layer at a time. The sonar is
        // deliberately excluded from the hull layer so it can be installed
        // as the next, separate step.
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 6, 1, 4), Direction.UP);
        scene.idle(10);
        scene.world().showSection(util.select().layer(2), Direction.UP);
        scene.idle(10);
        scene.world().showSection(util.select().layer(3), Direction.UP);
        scene.idle(10);

        // Slide only the near wall away, then remove both the yaw offset and
        // Ponder's default downward pitch. This is a true orthographic side
        // view of the sonar, rather than another isometric angle.
        scene.world().hideSection(nearWall, Direction.SOUTH);
        scene.addInstruction(new RotateSceneInstruction(0, 0, false));
        scene.idle(20);

        scene.world().showSection(util.select().position(sonar), Direction.WEST);
        scene.world().modifyBlock(sonar,
                state -> state.setValue(SonarBlock.WATERLOGGED, true), false);
        scene.effects().indicateSuccess(sonar);
        scene.overlay().showText(75)
                .sharedText("sonar_types.water")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(sonar, Direction.DOWN));
        scene.idle(85);

        scene.world().showSection(nearWall, Direction.NORTH);
        scene.addInstruction(new RotateSceneInstruction(-35, 55, false));
        scene.idle(25);

        // The sonar points through the bow in this schematic. Fade only the
        // short section in front of it so the full range cone stays visible.
        scene.world().hideSection(bowCutaway, Direction.EAST);
        scene.idle(15);

        Vec3 previewOrigin = util.vector().centerOf(sonar).add(0.58, 0, 0);
        showSonarPreview(scene, previewOrigin, 3.2, 60, 30, 70);
        scene.overlay().showText(70)
                .sharedText("sonar_types.limits")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(previewOrigin.add(2.0, 0, 0));
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(sonar), Pointing.DOWN, 40)
                .rightClick();
        scene.overlay().showText(55)
                .sharedText("sonar_types.configure")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(sonar));
        scene.idle(65);

        scene.overlay().showText(140)
                .sharedText("sonar_types.angle_range")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(previewOrigin.add(2.0, 0, 0));
        showSonarPreview(scene, previewOrigin, 3.8, 30, 20, 36);
        scene.idle(40);
        showSonarPreview(scene, previewOrigin, 3.0, 75, 45, 36);
        scene.idle(40);
        showSonarPreview(scene, previewOrigin, 1.9, 120, 80, 55);
        scene.idle(70);
        scene.world().showSection(bowCutaway, Direction.WEST);
        scene.idle(15);
        scene.markAsFinished();
    }

    private static void showSonarPreview(CreateSceneBuilder scene, Vec3 origin, double range,
                                         double horizontalAngle, double verticalAngle, int duration) {
        int yawSegments = 12;
        int pitchSegments = 6;
        double halfHorizontal = horizontalAngle / 2.0;
        double halfVertical = verticalAngle / 2.0;
        Vec3 previousTop = null;
        Vec3 previousBottom = null;

        // The real preview is a 3D cone shell. Keep only its four corner rays,
        // central axis and closed outer rim so the Ponder version stays clean.
        for (int segment = 0; segment <= yawSegments; segment++) {
            double yaw = -halfHorizontal + horizontalAngle * segment / yawSegments;
            Vec3 top = previewPoint(origin, range, yaw, halfVertical);
            Vec3 bottom = previewPoint(origin, range, yaw, -halfVertical);

            if (previousTop != null) {
                scene.overlay().showLine(PonderPalette.BLUE, previousTop, top, duration);
                scene.overlay().showLine(PonderPalette.BLUE, previousBottom, bottom, duration);
            }
            if (segment == 0) {
                scene.overlay().showLine(PonderPalette.BLUE, origin, top, duration);
                scene.overlay().showLine(PonderPalette.BLUE, origin, bottom, duration);
            } else if (segment == yawSegments) {
                scene.overlay().showLine(PonderPalette.RED, origin, top, duration);
                scene.overlay().showLine(PonderPalette.RED, origin, bottom, duration);
            }

            previousTop = top;
            previousBottom = bottom;
        }

        Vec3 previousLeft = null;
        Vec3 previousRight = null;
        for (int segment = 0; segment <= pitchSegments; segment++) {
            double pitch = -halfVertical + verticalAngle * segment / pitchSegments;
            Vec3 left = previewPoint(origin, range, -halfHorizontal, pitch);
            Vec3 right = previewPoint(origin, range, halfHorizontal, pitch);
            if (previousLeft != null) {
                scene.overlay().showLine(PonderPalette.BLUE, previousLeft, left, duration);
                scene.overlay().showLine(PonderPalette.RED, previousRight, right, duration);
            }
            previousLeft = left;
            previousRight = right;
        }

        scene.overlay().showBigLine(PonderPalette.OUTPUT, origin,
                previewPoint(origin, range, 0, 0), duration);
    }

    private static Vec3 previewPoint(Vec3 origin, double range, double yawDegrees,
                                     double pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        return origin.add(
                range * Math.cos(yaw) * horizontal,
                range * Math.sin(pitch),
                -range * Math.sin(yaw) * horizontal);
    }

    public static void mechanicalSonar(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("mechanical_sonar", "Powering the Mechanical Imaging Sonar");
        scene.configureBasePlate(0, 0, 7);

        BlockPos sonar = util.grid().at(3, 2, 3);
        BlockPos shaft = sonar.below();
        BlockPos controller = util.grid().at(5, 1, 3);
        BlockPos compactLink = sonar.east();

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().position(sonar), Direction.DOWN);
        scene.overlay().showText(55)
                .sharedText("mechanical.intro")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(sonar));
        scene.idle(65);

        scene.world().showSection(util.select().position(shaft), Direction.UP);
        scene.overlay().showText(55)
                .sharedText("mechanical.power")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(shaft));
        scene.idle(30);
        Selection kinetics = util.select().fromTo(shaft, sonar);
        scene.world().setKineticSpeed(kinetics, 32);
        scene.effects().rotationSpeedIndicator(shaft);
        scene.effects().indicateSuccess(sonar);
        scene.idle(55);

        scene.overlay().showControls(util.vector().topOf(sonar), Pointing.DOWN, 35)
                .rightClick();
        scene.world().showSection(util.select().position(controller), Direction.WEST);
        scene.overlay().showControls(util.vector().topOf(controller), Pointing.DOWN, 30)
                .rightClick()
                .withItem(new ItemStack(ModBlocks.RADAR_LINK.asItem()));
        scene.idle(35);
        scene.overlay().showControls(util.vector().blockSurface(sonar, Direction.EAST),
                        Pointing.RIGHT, 35)
                .rightClick()
                .withItem(new ItemStack(ModBlocks.RADAR_LINK.asItem()));
        scene.world().showSection(util.select().position(compactLink), Direction.WEST);
        scene.overlay().showLine(PonderPalette.INPUT, util.vector().centerOf(controller),
                util.vector().centerOf(sonar), 65);
        scene.overlay().showText(60)
                .sharedText("mechanical.configure_network")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(sonar));
        scene.idle(70);
        scene.markAsFinished();
    }

    public static void sonarGlass(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("sonar_glass", "Displaying Sonar Signals on Glass");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.72f);
        scene.addInstruction(ponderScene -> {
            ponderScene.getTransform().xRotation.startWithValue(-25);
            ponderScene.getTransform().yRotation.startWithValue(155);
        });

        Selection glassWindow = util.select().fromTo(3, 1, 6, 5, 3, 6);
        BlockPos glassCenter = util.grid().at(4, 2, 6);
        BlockPos controller = util.grid().at(1, 1, 3);
        BlockPos directLink = glassCenter.north();
        BlockPos summator = util.grid().at(7, 1, 3);
        BlockState summatorState = CreateEchoRadars.SIGNAL_SUMMATOR.get().defaultBlockState()
                .setValue(SonarSignalSummatorBlock.FACING, Direction.NORTH);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(glassWindow, Direction.DOWN);
        scene.world().showSection(util.select().position(controller), Direction.DOWN);
        scene.idle(20);

        ItemStack dataLink = new ItemStack(ModBlocks.RADAR_LINK.asItem());
        scene.overlay().showControls(util.vector().topOf(controller), Pointing.DOWN, 30)
                .rightClick()
                .withItem(dataLink);
        scene.idle(35);
        scene.overlay().showControls(util.vector().blockSurface(glassCenter, Direction.NORTH),
                        Pointing.DOWN, 35)
                .rightClick()
                .withItem(dataLink);
        scene.world().showSection(util.select().position(directLink), Direction.SOUTH);
        scene.overlay().showLine(PonderPalette.INPUT, util.vector().centerOf(controller),
                util.vector().centerOf(glassCenter), 65);
        scene.overlay().showText(60)
                .sharedText("glass.single")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(directLink));
        scene.idle(70);

        scene.overlay().showControls(util.vector().centerOf(directLink), Pointing.RIGHT, 30)
                .leftClick();
        scene.world().destroyBlock(directLink);
        scene.overlay().showText(45)
                .sharedText("glass.remove_direct")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(directLink));
        scene.idle(50);

        scene.rotateCameraY(-20);
        scene.idle(15);
        scene.overlay().showControls(util.vector().centerOf(glassCenter), Pointing.DOWN, 35)
                .rightClick()
                .withItem(CreateEchoRadars.SIGNAL_SUMMATOR_ITEM.get().getDefaultInstance());
        scene.overlay().showOutline(PonderPalette.WHITE, "summator_glass_pulse",
                glassWindow, 12);
        scene.overlay().showText(50)
                .sharedText("glass.select")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(glassCenter));
        scene.idle(55);

        scene.overlay().showControls(util.vector().topOf(summator), Pointing.DOWN, 30)
                .rightClick()
                .withItem(CreateEchoRadars.SIGNAL_SUMMATOR_ITEM.get().getDefaultInstance());
        scene.world().showSection(util.select().position(summator), Direction.DOWN);
        scene.world().modifyBlockEntity(summator, SonarSignalSummatorBlockEntity.class,
                blockEntity -> blockEntity.setGlassTarget(glassCenter));
        scene.overlay().showLine(PonderPalette.OUTPUT, util.vector().centerOf(summator),
                util.vector().centerOf(glassCenter), 65);
        scene.overlay().showText(60)
                .sharedText("glass.place")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(summator));
        scene.idle(70);

        int[] demonstratedSlots = {0, 3};
        Pointing[] controlDirections = {Pointing.RIGHT, Pointing.LEFT};
        for (int index = 0; index < demonstratedSlots.length; index++) {
            int slot = demonstratedSlots[index];
            Vec3 slotPosition = SonarSignalSummatorBlock.slotCenter(summatorState, slot)
                    .add(summator.getX(), summator.getY(), summator.getZ());
            scene.overlay().showControls(slotPosition, controlDirections[index], 18)
                    .rightClick()
                    .withItem(dataLink);
            int currentSlot = slot;
            scene.world().modifyBlockEntity(summator, SonarSignalSummatorBlockEntity.class,
                    blockEntity -> blockEntity.installAntenna(currentSlot,
                            controller.offset(currentSlot, 0, 0)));
            scene.idle(22);
        }
        scene.overlay().showText(60)
                .sharedText("glass.inputs")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(summator));
        scene.idle(75);

        scene.markAsFinished();
    }
}
