package org.rassvet.create_echo_radars.mixin;

import com.happysg.radar.item.GuidedFuzeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.rassvet.create_echo_radars.compat.cbcmoreshells.CbcmsTorpedoGuidance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

@Mixin(value = GuidedFuzeItem.class, remap = false)
public abstract class GuidedFuzeTorpedoMixin {
    @Inject(method = "onProjectileTick", at = @At("HEAD"), cancellable = true)
    private void createEchoRadars$guideCbcmsTorpedo(ItemStack fuze,
                                                    AbstractCannonProjectile projectile,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (!ModList.get().isLoaded("cbcmoreshells")
                || !CbcmsTorpedoGuidance.isSupportedTorpedo(projectile)) {
            return;
        }

        CbcmsTorpedoGuidance.guide(fuze, projectile);
        // FuzeItem's base tick result is false. Cancelling here prevents the
        // ballistic apex and validity gates from running for CBCMS torpedoes.
        cir.setReturnValue(false);
    }
}
