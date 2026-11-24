package dev.comfyfluffy.caustica.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import dev.comfyfluffy.caustica.rt.terrain.VoxyTerrain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.VoxyClient", remap = false)
abstract class VoxyClientMixin {
    @Inject(method = "initVoxyClient", at = @At("HEAD"), cancellable = true)
    private static void caustica$initVulkanStorage(CallbackInfo ci) {
        if (((GpuDeviceAccessor) (Object) RenderSystem.getDevice()).caustica$getBackend() instanceof VulkanDevice) {
            VoxyTerrain.initializeStorage();
            ci.cancel();
        }
    }
}
