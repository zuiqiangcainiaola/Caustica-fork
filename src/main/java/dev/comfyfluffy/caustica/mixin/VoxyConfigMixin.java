package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.rt.terrain.VoxyTerrain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.config.VoxyConfig", remap = false)
abstract class VoxyConfigMixin {
    @Inject(method = "isRenderingEnabled", at = @At("HEAD"), cancellable = true)
    private void caustica$useVulkanTerrain(CallbackInfoReturnable<Boolean> cir) {
        if (VoxyTerrain.dataOnly()) cir.setReturnValue(false);
    }
}
