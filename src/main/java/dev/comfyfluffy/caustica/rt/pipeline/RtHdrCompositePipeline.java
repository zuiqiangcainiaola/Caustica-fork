package dev.comfyfluffy.caustica.rt.pipeline;

import dev.comfyfluffy.caustica.rt.RtContext;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Compute pass that composites the vanilla UI overlay (premultiplied sRGB rgba8, sampled) over the
 * PQ-encoded HDR display image in place (decode-blend-reencode, since PQ is nonlinear), at paper white. Used
 * by the HDR present path (step C.2) after the GUI has rendered into the overlay, before the HDR image is
 * blitted to the PQ swapchain.
 */
public final class RtHdrCompositePipeline {
    private final RtPresentPass pass;

    private RtHdrCompositePipeline(RtPresentPass pass) {
        this.pass = pass;
    }

    public static RtHdrCompositePipeline create(RtContext ctx) {
        return new RtHdrCompositePipeline(RtPresentPass.create(ctx, "hdr_ui_composite.comp.spv", "hdr ui composite"));
    }

    public void setImages(long hdrImageView, long overlayImageView, long sampler) {
        pass.setImages(hdrImageView, overlayImageView, sampler);
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
