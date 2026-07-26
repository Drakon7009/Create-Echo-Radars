package org.rassvet.create_echo_radars.mixin;

import net.minecraft.world.level.block.state.BlockState;
import org.rassvet.create_echo_radars.compat.DeepSeasHullProtection;
import org.rassvet.create_echo_radars.content.glass.SonarGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Pseudo
@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig",
        remap = false)
public abstract class DeepSeasHullStrengthConfigMixin {
    @Inject(
            method = "getFor(Lnet/minecraft/world/level/block/state/BlockState;)Ljava/util/Optional;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private static void createEchoRadars$protectSonarGlass(
            BlockState state, CallbackInfoReturnable<Optional<?>> cir) {
        if (SonarGlass.isGlass(state)) {
            cir.setReturnValue(DeepSeasHullProtection.makePressureImmune(
                    cir.getReturnValue()));
        }
    }
}
