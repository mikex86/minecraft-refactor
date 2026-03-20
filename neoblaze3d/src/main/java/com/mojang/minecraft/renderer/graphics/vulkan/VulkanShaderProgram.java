package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.ShaderProgram;
import com.mojang.minecraft.renderer.resource.ResourceBufferLoader;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.jemalloc.JEmalloc;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

final class VulkanShaderProgram implements ShaderProgram {
    private final VulkanContext context;
    private final long vertexShaderModule;
    private final long fragmentShaderModule;
    private boolean disposed;

    private VulkanShaderProgram(VulkanContext context, long vertexShaderModule, long fragmentShaderModule) {
        this.context = context;
        this.vertexShaderModule = vertexShaderModule;
        this.fragmentShaderModule = fragmentShaderModule;
    }

    static VulkanShaderProgram fromPrecompiledBinaries(VulkanContext context,
                                                        String vertexBinaryPath,
                                                        String fragmentBinaryPath) throws IOException {
        ByteBuffer vertexCode = null;
        ByteBuffer fragmentCode = null;
        long vertexModule = VK10.VK_NULL_HANDLE;
        long fragmentModule = VK10.VK_NULL_HANDLE;
        try {
            vertexCode = ResourceBufferLoader.loadResourceRequired(
                    VulkanShaderProgram.class,
                    VulkanPathResolver.resolve(vertexBinaryPath),
                    JEmalloc::je_malloc
            );
            fragmentCode = ResourceBufferLoader.loadResourceRequired(
                    VulkanShaderProgram.class,
                    VulkanPathResolver.resolve(fragmentBinaryPath),
                    JEmalloc::je_malloc
            );

            vertexModule = createShaderModule(context, vertexCode, vertexBinaryPath);
            fragmentModule = createShaderModule(context, fragmentCode, fragmentBinaryPath);
            return new VulkanShaderProgram(context, vertexModule, fragmentModule);
        } catch (IOException | RuntimeException e) {
            if (vertexModule != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyShaderModule(context.getDevice(), vertexModule, null);
            }
            if (fragmentModule != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyShaderModule(context.getDevice(), fragmentModule, null);
            }
            throw e;
        } finally {
            if (vertexCode != null) {
                JEmalloc.je_free(vertexCode);
            }
            if (fragmentCode != null) {
                JEmalloc.je_free(fragmentCode);
            }
        }
    }

    long getVertexShaderModule() {
        return vertexShaderModule;
    }

    long getFragmentShaderModule() {
        return fragmentShaderModule;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        org.lwjgl.vulkan.VkDevice device = context.getDevice();
        if (device != null) {
            VK10.vkDestroyShaderModule(device, vertexShaderModule, null);
            VK10.vkDestroyShaderModule(device, fragmentShaderModule, null);
        }
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private static long createShaderModule(VulkanContext context, ByteBuffer spirv, String debugPath) {
        if ((spirv.remaining() % 4) != 0) {
            throw new IllegalArgumentException("SPIR-V bytecode length must be aligned to 4 bytes: " + debugPath);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(spirv);

            LongBuffer pShaderModule = stack.mallocLong(1);
            context.checkVk(VK10.vkCreateShaderModule(context.getDevice(), createInfo, null, pShaderModule), "vkCreateShaderModule(" + debugPath + ")");
            return pShaderModule.get(0);
        }
    }
}
