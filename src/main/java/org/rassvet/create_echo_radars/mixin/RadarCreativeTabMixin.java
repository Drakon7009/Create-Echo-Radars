package org.rassvet.create_echo_radars.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import org.rassvet.create_echo_radars.compat.RadarInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeTab.class)
public abstract class RadarCreativeTabMixin {
    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void createEchoRadars$combinedTitle(CallbackInfoReturnable<Component> cir) {
        if (RadarInventory.isRadarTab((CreativeModeTab) (Object) this)) {
            cir.setReturnValue(Component.translatable("itemGroup.create_echo_radars.combined"));
        }
    }
}
