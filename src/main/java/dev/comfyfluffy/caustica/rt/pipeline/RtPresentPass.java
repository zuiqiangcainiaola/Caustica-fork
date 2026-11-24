package dev.comfyfluffy.caustica.rt.pipeline;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;
import dev.comfyfluffy.caustica.rt.RtShaderModules;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.LongBuffer;

import static dev.comfyfluffy.caustica.rt.RtContext.check;

/** One present pass with a storage destination, a sampled source and a paper-white push constant. */
final class RtPresentPass {
    private final RtContext ctx;
    private final String label;
    private long descriptorSetLayout;
    private long descriptorPool;
    private long descriptorSet;
    private long pipelineLayout;
    private long pipeline;
    private long boundDestination;
    private long boundSource;
    private long boundSampler;
    private boolean bindingsDirty = true;
    private boolean destroyed;

    private RtPresentPass(RtContext ctx, String label) {
        this.ctx = ctx;
        this.label = label;
    }

    static RtPresentPass create(RtContext ctx, String shader, String label) {
        RtPresentPass pass = new RtPresentPass(ctx, label);
        try {
            pass.initialize(shader);
            return pass;
        } catch (RuntimeException | Error failure) {
            pass.destroy();
            throw failure;
        }
    }

    private void initialize(String shader) {
        VkDevice vk = ctx.vk();
        try (MemoryStack stack = MemoryStack.stackPush(); RtShaderModules modules = new RtShaderModules(vk)) {
            LongBuffer handle = stack.mallocLong(1);
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
            bindings.get(0).binding(0).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(1).binding(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            VkDescriptorSetLayoutCreateInfo setLayoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default().pBindings(bindings);
            check(VK10.vkCreateDescriptorSetLayout(vk, setLayoutInfo, null, handle), "vkCreateDescriptorSetLayout(" + label + ")");
            descriptorSetLayout = handle.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT, descriptorSetLayout, label + " descriptor set layout");

            VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(2, stack);
            sizes.get(0).type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1);
            sizes.get(1).type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default().maxSets(1).pPoolSizes(sizes);
            check(VK10.vkCreateDescriptorPool(vk, poolInfo, null, handle), "vkCreateDescriptorPool(" + label + ")");
            descriptorPool = handle.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_DESCRIPTOR_POOL, descriptorPool, label + " descriptor pool");

            VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(descriptorPool).pSetLayouts(stack.longs(descriptorSetLayout));
            check(VK10.vkAllocateDescriptorSets(vk, setInfo, handle), "vkAllocateDescriptorSets(" + label + ")");
            descriptorSet = handle.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_DESCRIPTOR_SET, descriptorSet, label + " descriptor set");

            VkPushConstantRange.Buffer push = VkPushConstantRange.calloc(1, stack);
            push.get(0).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(Float.BYTES);
            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                    .pSetLayouts(stack.longs(descriptorSetLayout)).pPushConstantRanges(push);
            check(VK10.vkCreatePipelineLayout(vk, layoutInfo, null, handle), "vkCreatePipelineLayout(" + label + ")");
            pipelineLayout = handle.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_PIPELINE_LAYOUT, pipelineLayout, label + " pipeline layout");

            long module = modules.load(shader);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_SHADER_MODULE, module, label + " shader module");
            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default()
                    .stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT).module(module).pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0).sType$Default().stage(stage).layout(pipelineLayout);
            check(VK10.vkCreateComputePipelines(vk, VK10.VK_NULL_HANDLE, pipelineInfo, null, handle),
                    "vkCreateComputePipelines(" + label + ")");
            pipeline = handle.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_PIPELINE, pipeline, label + " compute pipeline");
        }
    }

    void setImages(long destination, long source, long sampler) {
        if (!bindingsDirty && boundDestination == destination && boundSource == source && boundSampler == sampler) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer output = VkDescriptorImageInfo.calloc(1, stack);
            output.get(0).imageView(destination).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
            VkDescriptorImageInfo.Buffer input = VkDescriptorImageInfo.calloc(1, stack);
            input.get(0).imageView(source).sampler(sampler).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
            writes.get(0).sType$Default().dstSet(descriptorSet).dstBinding(0)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).pImageInfo(output);
            writes.get(1).sType$Default().dstSet(descriptorSet).dstBinding(1)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(input);
            VK10.vkUpdateDescriptorSets(ctx.vk(), writes, null);
        }
        boundDestination = destination;
        boundSource = source;
        boundSampler = sampler;
        bindingsDirty = false;
    }

    /** Image views can be recreated with the same numeric handle. */
    void invalidateBindings() {
        bindingsDirty = true;
    }

    void dispatch(VkCommandBuffer cmd, int width, int height, float paperWhiteNits) {
        try (MemoryStack stack = MemoryStack.stackPush(); RtDebugLabels.Scope ignored = RtDebugLabels.scope(ctx, cmd, label)) {
            VK10.vkCmdBindPipeline(cmd, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            VK10.vkCmdBindDescriptorSets(cmd, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, 0,
                    stack.longs(descriptorSet), null);
            VK10.vkCmdPushConstants(cmd, pipelineLayout, VK10.VK_SHADER_STAGE_COMPUTE_BIT, 0, stack.floats(paperWhiteNits));
            VK10.vkCmdDispatch(cmd, (width + 15) / 16, (height + 15) / 16, 1);
        }
    }

    void destroy() {
        if (destroyed) {
            return;
        }
        VkDevice vk = ctx.vk();
        VK10.vkDestroyPipeline(vk, pipeline, null);
        VK10.vkDestroyPipelineLayout(vk, pipelineLayout, null);
        VK10.vkDestroyDescriptorPool(vk, descriptorPool, null);
        VK10.vkDestroyDescriptorSetLayout(vk, descriptorSetLayout, null);
        destroyed = true;
    }
}
