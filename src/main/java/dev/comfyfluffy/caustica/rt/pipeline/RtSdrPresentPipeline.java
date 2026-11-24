package dev.comfyfluffy.caustica.rt.pipeline;

import dev.comfyfluffy.caustica.rt.RtContext;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Compute pass that converts Minecraft's SDR main target (rgba8, sRGB-encoded, sampled) to a PQ-encoded
 * rgba16f image at paper white. Used by the HDR present path for non-RT frames (menus, loading screen, title
 * panorama) so they present correctly to the PQ swapchain instead of being raw-copied (misdisplayed). Same
 * descriptor shape as {@link RtHdrCompositePipeline}: binding 0 = storage out, binding 1 = sampled SDR in.
 */
public final class RtSdrPresentPipeline {
    private final RtPresentPass pass;

    private RtSdrPresentPipeline(RtPresentPass pass) {
        this.pass = pass;
    }

    public static RtSdrPresentPipeline create(RtContext ctx) {
        return new RtSdrPresentPipeline(RtPresentPass.create(ctx, "sdr_present.comp.spv", "sdr present"));
    }

    public void setImages(long outImageView, long sdrImageView, long sampler) {
        pass.setImages(outImageView, sdrImageView, sampler);
    }

    public void invalidateBindings() {
        pass.invalidateBindings();
    }

    public void dispatch(VkCommandBuffer cmd, int width, int height, float paperWhiteNits) {
        pass.dispatch(cmd, width, height, paperWhiteNits);
    }

    public void destroy() {
        pass.destroy();
    }
}
