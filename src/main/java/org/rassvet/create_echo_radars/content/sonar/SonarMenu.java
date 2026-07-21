package org.rassvet.create_echo_radars.content.sonar;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.rassvet.create_echo_radars.CreateEchoRadars;

public class SonarMenu extends AbstractContainerMenu {
    private final SonarBlockEntity sonar;
    private final ContainerLevelAccess access;
    private final DataSlot range = DataSlot.standalone();
    private final DataSlot sector = DataSlot.standalone();
    private final DataSlot verticalSector = DataSlot.standalone();
    private final DataSlot tiltAngle = DataSlot.standalone();
    private final DataSlot sonarType = DataSlot.standalone();
    private final DataSlot autoHeight = DataSlot.standalone();

    public SonarMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, (SonarBlockEntity) inventory.player.level().getBlockEntity(buffer.readBlockPos()));
    }

    public SonarMenu(int containerId, Inventory inventory, SonarBlockEntity sonar) {
        super(CreateEchoRadars.SONAR_MENU.get(), containerId);
        this.sonar = sonar;
        this.access = sonar == null ? ContainerLevelAccess.NULL :
                ContainerLevelAccess.create(sonar.getLevel(), sonar.getBlockPos());
        addDataSlot(range);
        addDataSlot(sector);
        addDataSlot(verticalSector);
        addDataSlot(tiltAngle);
        addDataSlot(sonarType);
        addDataSlot(autoHeight);
        if (sonar != null) {
            range.set(sonar.getSonarRange());
            sector.set(sonar.getHorizontalSector());
            verticalSector.set(sonar.getVerticalSector());
            tiltAngle.set(sonar.getTiltAngle());
            sonarType.set(sonar.getSonarType().ordinal());
            autoHeight.set(sonar.isAutoHeight() ? 1 : 0);
        }
    }

    public SonarBlockEntity getSonar() {
        return sonar;
    }

    public int getRange() {
        return range.get();
    }

    public int getSector() {
        return sector.get();
    }

    public int getVerticalSector() {
        return verticalSector.get();
    }

    public int getTiltAngle() {
        return tiltAngle.get();
    }

    public SonarType getSonarType() {
        int ordinal = Math.max(0, Math.min(SonarType.values().length - 1, sonarType.get()));
        return SonarType.values()[ordinal];
    }

    public boolean isAutoHeight() {
        return autoHeight.get() != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return sonar != null && !sonar.isRemoved()
                && player.distanceToSqr(Vec3.atCenterOf(sonar.getBlockPos())) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
