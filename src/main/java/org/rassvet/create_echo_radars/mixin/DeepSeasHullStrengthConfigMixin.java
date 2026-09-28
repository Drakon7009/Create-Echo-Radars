package org.rassvet.create_echo_radars.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.rassvet.create_echo_radars.compat.DeepSeasHullProtection;
import org.rassvet.create_echo_radars.content.glass.SonarGlass;
import org.rassvet.create_echo_radars.content.sonar.SonarBlock;
import org.rassvet.create_echo_radars.content.sonar.SonarDataLinkBlock;
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
    private static void createEchoRadars$protectSonarEquipment(
            BlockState state, CallbackInfoReturnable<Optional<?>> cir) {
        if (SonarGlass.isGlass(state)) {
            cir.setReturnValue(DeepSeasHullProtection.makePressureImmune(
                    cir.getReturnValue()));
        } else if (state.getBlock() instanceof SonarBlock
                || state.getBlock() instanceof SonarDataLinkBlock
                || BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(
                        ResourceLocation.fromNamespaceAndPath("create", "transmitter"))) {
            cir.setReturnValue(DeepSeasHullProtection.increaseSonarDepth(cir.getReturnValue()));
        }
    }
}
