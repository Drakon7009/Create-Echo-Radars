package org.rassvet.create_echo_radars.mixin;

import org.rassvet.create_echo_radars.client.IrisNativeWaterFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.Program", remap = false)
public abstract class IrisWaterFogProgramMixin {
    @Shadow(remap = false) public abstract int getProgramId();
    @Inject(method = "use", at = @At("RETURN"), require = 0, remap = false)
    private void createEchoRadars$waterUniforms(CallbackInfo ci) { IrisNativeWaterFog.bind(getProgramId()); }
    @Inject(method = "destroyInternal", at = @At("HEAD"), require = 0, remap = false)
    private void createEchoRadars$forgetWaterUniforms(CallbackInfo ci) { IrisNativeWaterFog.forget(getProgramId()); }
}
