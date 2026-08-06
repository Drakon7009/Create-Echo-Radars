package org.rassvet.create_echo_radars.client;

import com.happysg.radar.block.datalink.DataLinkBlockItem;
import com.simibubi.create.AllSpecialTextures;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlock;
import org.rassvet.create_echo_radars.content.summator.SonarSignalSummatorBlockEntity;

/** Highlights the summator antenna contact in the same style as Power Grid terminals. */
public final class SonarSignalSummatorOutline {
    private static final Object OUTLINE_SLOT = new Object();
    private static final double OUTLINE_HALF_SIZE = 0.125;
    private static final int OUTLINE_COLOR = 0xAAAAAA;

    private SonarSignalSummatorOutline() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;

        ItemStack main = minecraft.player.getMainHandItem();
        ItemStack off = minecraft.player.getOffhandItem();
        if (!(main.getItem() instanceof DataLinkBlockItem)
                && !(off.getItem() instanceof DataLinkBlockItem)) return;
        if (!(minecraft.level.getBlockEntity(hit.getBlockPos())
                instanceof SonarSignalSummatorBlockEntity summator)) return;

        int slot = SonarSignalSummatorBlock.slotAt(summator.getBlockState(),
                summator.getBlockPos(), hit);
        if (slot < 0) return;

        Vec3 center = SonarSignalSummatorBlock.slotCenter(
                summator.getBlockState(), slot).add(Vec3.atLowerCornerOf(hit.getBlockPos()));
        AABB outline = new AABB(center.x - OUTLINE_HALF_SIZE,
                center.y - OUTLINE_HALF_SIZE,
                center.z - OUTLINE_HALF_SIZE,
                center.x + OUTLINE_HALF_SIZE,
                center.y + OUTLINE_HALF_SIZE,
                center.z + OUTLINE_HALF_SIZE);
        Outliner.getInstance().chaseAABB(OUTLINE_SLOT, outline)
                .colored(OUTLINE_COLOR)
                .withFaceTexture(AllSpecialTextures.CUTOUT_CHECKERED)
                .lineWidth(0.020f);
    }
}
