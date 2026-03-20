package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.PipelineLayout;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;

import java.nio.LongBuffer;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;

final class VulkanPipelineLayout implements PipelineLayout {
    private final VulkanContext context;
    private final Descriptor descriptor;
    private final EnumMap<BindingSemantic, Integer> semanticBindings;
    private final BitSet declaredBindings;

    private final long descriptorSetLayout;
    private final long pipelineLayout;

    private boolean disposed;

    VulkanPipelineLayout(VulkanContext context, Descriptor descriptor) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor cannot be null");
        }

        this.context = context;
        this.descriptor = descriptor;
        this.semanticBindings = new EnumMap<BindingSemantic, Integer>(BindingSemantic.class);
        this.declaredBindings = new BitSet();

        List<Binding> bindings = descriptor.getBindings();
        for (Binding binding : bindings) {
            declaredBindings.set(binding.getBinding());
            BindingSemantic semantic = binding.getSemantic();
            if (semantic != BindingSemantic.NONE) {
                Integer previous = semanticBindings.put(semantic, Integer.valueOf(binding.getBinding()));
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate semantic binding for " + semantic + " in layout '" + descriptor.getDebugName() + "'");
                }
            }
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer layoutBindings = VkDescriptorSetLayoutBinding.calloc(bindings.size(), stack);
            for (int i = 0; i < bindings.size(); i++) {
                Binding binding = bindings.get(i);
                layoutBindings.get(i)
                        .binding(binding.getBinding())
                        .descriptorType(toVkDescriptorType(binding.getResourceType()))
                        .descriptorCount(1)
                        .stageFlags(toVkShaderStage(binding.getStage()))
                        .pImmutableSamplers(null);
            }

            VkDescriptorSetLayoutCreateInfo descriptorSetLayoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(layoutBindings);

            LongBuffer pDescriptorSetLayout = stack.mallocLong(1);
            context.checkVk(VK10.vkCreateDescriptorSetLayout(context.getDevice(), descriptorSetLayoutInfo, null, pDescriptorSetLayout), "vkCreateDescriptorSetLayout");
            descriptorSetLayout = pDescriptorSetLayout.get(0);

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(descriptorSetLayout));

            LongBuffer pPipelineLayout = stack.mallocLong(1);
            context.checkVk(VK10.vkCreatePipelineLayout(context.getDevice(), pipelineLayoutInfo, null, pPipelineLayout), "vkCreatePipelineLayout");
            pipelineLayout = pPipelineLayout.get(0);
        }
    }

    @Override
    public String getDebugName() {
        return descriptor.getDebugName();
    }

    @Override
    public List<Binding> getBindings() {
        return descriptor.getBindings();
    }

    @Override
    public int findBinding(BindingSemantic semantic) {
        Integer binding = semanticBindings.get(semantic);
        if (binding == null) {
            return -1;
        }
        return binding.intValue();
    }

    @Override
    public boolean hasBinding(int binding) {
        return binding >= 0 && declaredBindings.get(binding);
    }

    long getVkDescriptorSetLayout() {
        return descriptorSetLayout;
    }

    long getVkPipelineLayout() {
        return pipelineLayout;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        org.lwjgl.vulkan.VkDevice device = context.getDevice();
        if (device != null) {
            VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
            VK10.vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        }
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private static int toVkDescriptorType(ResourceType resourceType) {
        switch (resourceType) {
            case UNIFORM_BUFFER:
                return VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
            case STORAGE_BUFFER:
                return VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            case SAMPLED_TEXTURE:
            case SAMPLER:
            case COMBINED_IMAGE_SAMPLER:
                return VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            default:
                throw new IllegalArgumentException("Unsupported resource type: " + resourceType);
        }
    }

    private static int toVkShaderStage(ShaderStage stage) {
        switch (stage) {
            case VERTEX:
                return VK10.VK_SHADER_STAGE_VERTEX_BIT;
            case FRAGMENT:
                return VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
            case COMPUTE:
                return VK10.VK_SHADER_STAGE_COMPUTE_BIT;
            default:
                throw new IllegalArgumentException("Unsupported shader stage: " + stage);
        }
    }
}
