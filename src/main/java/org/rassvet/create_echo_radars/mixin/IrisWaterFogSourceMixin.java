package org.rassvet.create_echo_radars.mixin;

import org.rassvet.create_echo_radars.client.IrisNativeWaterFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.programs.ProgramSource", remap = false)
public abstract class IrisWaterFogSourceMixin {
    @Shadow(remap = false) public abstract String getName();
    @Inject(method = "getFragmentSource", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void createEchoRadars$nativeWater(CallbackInfoReturnable<Optional<String>> ci) {
        ci.setReturnValue(ci.getReturnValue().map(source -> IrisNativeWaterFog.patch(getName(), source)));
    }
}
