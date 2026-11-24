package dev.comfyfluffy.caustica.rt;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static dev.comfyfluffy.caustica.rt.RtContext.check;

/** Owns the temporary shader modules for one pipeline construction. */
public final class RtShaderModules implements AutoCloseable {
    private final VkDevice device;
    private final List<Long> handles = new ArrayList<>();

    public RtShaderModules(VkDevice device) {
        this.device = device;
    }

    public long load(String name) {
        String path = "/caustica/rt/" + name;
        byte[] bytes;
        try (InputStream in = RtShaderModules.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing SPIR-V resource: " + path);
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("failed to read SPIR-V resource: " + path, e);
        }

        ByteBuffer code = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            code.put(bytes).flip();
            VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            LongBuffer handle = stack.mallocLong(1);
            check(VK10.vkCreateShaderModule(device, info, null, handle), "vkCreateShaderModule(" + name + ")");
            long module = handle.get(0);
            handles.add(module);
            return module;
        } finally {
            MemoryUtil.memFree(code);
        }
    }

    @Override
    public void close() {
        for (long handle : handles) {
            VK10.vkDestroyShaderModule(device, handle, null);
        }
        handles.clear();
    }
}
