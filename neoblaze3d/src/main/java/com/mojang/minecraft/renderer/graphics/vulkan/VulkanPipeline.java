package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.DataType;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.PipelineLayout;
import com.mojang.minecraft.renderer.graphics.ShaderProgram;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

final class VulkanPipeline implements Pipeline {
    private final VulkanContext context;
    private final Descriptor descriptor;

    private final long[] pipelinesByPrimitive;
    private boolean disposed;

    VulkanPipeline(VulkanContext context, Descriptor descriptor) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor cannot be null");
        }
        if (descriptor.getLayout() == null) {
            throw new IllegalArgumentException("pipeline layout cannot be null");
        }
        if (!(descriptor.getLayout() instanceof VulkanPipelineLayout)) {
            throw new IllegalArgumentException("layout must be a VulkanPipelineLayout");
        }
        if (descriptor.getProgram() == null) {
            throw new IllegalArgumentException("program cannot be null");
        }
        if (!(descriptor.getProgram() instanceof VulkanShaderProgram)) {
            throw new IllegalArgumentException("program must be a VulkanShaderProgram");
        }
        if (descriptor.getVertexFormat() == null) {
            throw new IllegalArgumentException("vertex format cannot be null");
        }
        if (descriptor.getBlendState() == null) {
            throw new IllegalArgumentException("blend state cannot be null");
        }
        if (descriptor.getDepthState() == null) {
            throw new IllegalArgumentException("depth state cannot be null");
        }
        if (descriptor.getRasterizerState() == null) {
            throw new IllegalArgumentException("rasterizer state cannot be null");
        }

        this.context = context;
        this.descriptor = descriptor;
        this.pipelinesByPrimitive = new long[GraphicsEnums.PrimitiveType.values().length];
    }

    @Override
    public PipelineLayout getLayout() {
        return descriptor.getLayout();
    }

    @Override
    public ShaderProgram getProgram() {
        return descriptor.getProgram();
    }

    @Override
    public VertexBuffer.VertexFormat getVertexFormat() {
        return descriptor.getVertexFormat();
    }

    @Override
    public BlendState getBlendState() {
        return descriptor.getBlendState();
    }

    @Override
    public DepthState getDepthState() {
        return descriptor.getDepthState();
    }

    @Override
    public RasterizerState getRasterizerState() {
        return descriptor.getRasterizerState();
    }

    @Override
    public String getDebugName() {
        return descriptor.getDebugName();
    }

    long getVkPipeline(GraphicsEnums.PrimitiveType primitiveType) {
        if (primitiveType == null) {
            throw new IllegalArgumentException("primitiveType cannot be null");
        }
        if (disposed) {
            throw new IllegalStateException("Pipeline has been disposed");
        }

        int index = primitiveType.ordinal();
        long pipeline = pipelinesByPrimitive[index];
        if (pipeline != VK10.VK_NULL_HANDLE) {
            return pipeline;
        }

        pipeline = createVkPipeline(primitiveType);
        pipelinesByPrimitive[index] = pipeline;
        return pipeline;
    }

    long getVkPipelineLayoutHandle() {
        return ((VulkanPipelineLayout) descriptor.getLayout()).getVkPipelineLayout();
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        org.lwjgl.vulkan.VkDevice device = context.getDevice();
        for (int i = 0; i < pipelinesByPrimitive.length; i++) {
            long pipeline = pipelinesByPrimitive[i];
            if (pipeline != VK10.VK_NULL_HANDLE && device != null) {
                VK10.vkDestroyPipeline(device, pipeline, null);
                pipelinesByPrimitive[i] = VK10.VK_NULL_HANDLE;
            }
            pipelinesByPrimitive[i] = VK10.VK_NULL_HANDLE;
        }
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private long createVkPipeline(GraphicsEnums.PrimitiveType primitiveType) {
        VulkanShaderProgram program = (VulkanShaderProgram) descriptor.getProgram();
        VulkanPipelineLayout layout = (VulkanPipelineLayout) descriptor.getLayout();
        VertexBuffer.VertexFormat format = descriptor.getVertexFormat();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            shaderStages.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                    .module(program.getVertexShaderModule())
                    .pName(stack.UTF8("main"));
            shaderStages.get(1)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(program.getFragmentShaderModule())
                    .pName(stack.UTF8("main"));

            VkVertexInputBindingDescription.Buffer bindingDescription = VkVertexInputBindingDescription.calloc(1, stack);
            bindingDescription.get(0)
                    .binding(0)
                    .stride(format.getStrideInBytes())
                    .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);

            VkVertexInputAttributeDescription.Buffer attributes = VkVertexInputAttributeDescription.calloc(4, stack);
            int attributeCount = writeVertexAttributes(format, attributes);
            attributes.limit(attributeCount);

            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(bindingDescription)
                    .pVertexAttributeDescriptions(attributes);

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(toVkTopology(primitiveType))
                    .primitiveRestartEnable(false);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .scissorCount(1);

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(toVkPolygonMode(descriptor.getRasterizerState().getFillMode()))
                    .lineWidth(1.0f)
                    .cullMode(toVkCullMode(descriptor.getRasterizerState().getCullMode()))
                    // Vulkan uses positive viewport height in this backend.
                    // Y inversion is handled in shaders under VULKAN_BACKEND.
                    .frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(false);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);

            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(descriptor.getDepthState().isDepthTest())
                    .depthWriteEnable(descriptor.getDepthState().isDepthMask())
                    .depthCompareOp(toVkCompareOp(descriptor.getDepthState().getCompareFunc()))
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            colorBlendAttachment.get(0)
                    .colorWriteMask(
                            VK10.VK_COLOR_COMPONENT_R_BIT
                                    | VK10.VK_COLOR_COMPONENT_G_BIT
                                    | VK10.VK_COLOR_COMPONENT_B_BIT
                                    | VK10.VK_COLOR_COMPONENT_A_BIT
                    )
                    .blendEnable(descriptor.getBlendState().isEnabled())
                    .srcColorBlendFactor(toVkBlendFactor(descriptor.getBlendState().getSrcFactor()))
                    .dstColorBlendFactor(toVkBlendFactor(descriptor.getBlendState().getDstFactor()))
                    .colorBlendOp(VK10.VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(toVkBlendFactor(descriptor.getBlendState().getSrcFactor()))
                    .dstAlphaBlendFactor(toVkBlendFactor(descriptor.getBlendState().getDstFactor()))
                    .alphaBlendOp(VK10.VK_BLEND_OP_ADD);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .pAttachments(colorBlendAttachment);

            IntBuffer dynamicStates = stack.ints(
                    VK10.VK_DYNAMIC_STATE_VIEWPORT,
                    VK10.VK_DYNAMIC_STATE_SCISSOR
            );
            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(dynamicStates);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInputInfo)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicState)
                    .layout(layout.getVkPipelineLayout())
                    .renderPass(context.getRenderPass())
                    .subpass(0)
                    .basePipelineHandle(VK10.VK_NULL_HANDLE)
                    .basePipelineIndex(-1);

            LongBuffer pPipeline = stack.mallocLong(1);
            context.checkVk(
                    VK10.vkCreateGraphicsPipelines(context.getDevice(), context.getPipelineCache(), pipelineInfo, null, pPipeline),
                    "vkCreateGraphicsPipelines(" + descriptor.getDebugName() + ", " + primitiveType + ")"
            );
            return pPipeline.get(0);
        }
    }

    private static int writeVertexAttributes(VertexBuffer.VertexFormat format, VkVertexInputAttributeDescription.Buffer dst) {
        int offset = 0;
        int index = 0;

        if (format.hasTexCoords()) {
            dst.get(index)
                    .location(2)
                    .binding(0)
                    .format(toVkVertexFormatForFloatingInput(format.getTexCoordDataType(), 2))
                    .offset(offset);
            offset += 2 * format.getTexCoordDataType().getSize();
            index++;
        }

        if (format.hasColors()) {
            dst.get(index)
                    .location(1)
                    .binding(0)
                    .format(toVkVertexFormatForFloatingInput(format.getColorDataType(), 3))
                    .offset(offset);
            offset += 3 * format.getColorDataType().getSize();
            index++;
        } else if (format.hasGrayScale()) {
            int grayFormat;
            if (isIntegerInputDataType(format.getGrayScaleDataType())) {
                grayFormat = toVkVertexFormatForIntegerInput(format.getGrayScaleDataType(), 1);
            } else {
                grayFormat = toVkVertexFormatForFloatingInput(format.getGrayScaleDataType(), 1);
            }
            dst.get(index)
                    .location(1)
                    .binding(0)
                    .format(grayFormat)
                    .offset(offset);
            offset += format.getGrayScaleDataType().getSize();
            index++;
        }

        if (format.hasNormals()) {
            dst.get(index)
                    .location(3)
                    .binding(0)
                    .format(toVkVertexFormatForFloatingInput(format.getNormalDataType(), 3))
                    .offset(offset);
            offset += 3 * format.getNormalDataType().getSize();
            index++;
        }

        if (format.hasPositions()) {
            dst.get(index)
                    .location(0)
                    .binding(0)
                    .format(toVkVertexFormatForFloatingInput(format.getPositionDataType(), 3))
                    .offset(offset);
            index++;
        }

        return index;
    }

    private static int toVkTopology(GraphicsEnums.PrimitiveType primitiveType) {
        switch (primitiveType) {
            case POINTS:
                return VK10.VK_PRIMITIVE_TOPOLOGY_POINT_LIST;
            case LINES:
                return VK10.VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
            case LINE_STRIP:
                return VK10.VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
            case TRIANGLES:
                return VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
            case TRIANGLE_STRIP:
                return VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
            case TRIANGLE_FAN:
                return VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;
            default:
                return VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        }
    }

    private static int toVkCullMode(GraphicsEnums.CullMode cullMode) {
        switch (cullMode) {
            case NONE:
                return VK10.VK_CULL_MODE_NONE;
            case FRONT:
                return VK10.VK_CULL_MODE_FRONT_BIT;
            case BACK:
                return VK10.VK_CULL_MODE_BACK_BIT;
            default:
                return VK10.VK_CULL_MODE_BACK_BIT;
        }
    }

    private static int toVkPolygonMode(GraphicsEnums.FillMode fillMode) {
        switch (fillMode) {
            case POINT:
                return VK10.VK_POLYGON_MODE_POINT;
            case WIREFRAME:
                return VK10.VK_POLYGON_MODE_LINE;
            case SOLID:
                return VK10.VK_POLYGON_MODE_FILL;
            default:
                return VK10.VK_POLYGON_MODE_FILL;
        }
    }

    private static int toVkCompareOp(GraphicsEnums.CompareFunc compareFunc) {
        switch (compareFunc) {
            case NEVER:
                return VK10.VK_COMPARE_OP_NEVER;
            case LESS:
                return VK10.VK_COMPARE_OP_LESS;
            case EQUAL:
                return VK10.VK_COMPARE_OP_EQUAL;
            case LESS_EQUAL:
                return VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
            case GREATER:
                return VK10.VK_COMPARE_OP_GREATER;
            case NOT_EQUAL:
                return VK10.VK_COMPARE_OP_NOT_EQUAL;
            case GREATER_EQUAL:
                return VK10.VK_COMPARE_OP_GREATER_OR_EQUAL;
            case ALWAYS:
                return VK10.VK_COMPARE_OP_ALWAYS;
            default:
                return VK10.VK_COMPARE_OP_LESS;
        }
    }

    private static int toVkBlendFactor(GraphicsEnums.BlendFactor factor) {
        switch (factor) {
            case ZERO:
                return VK10.VK_BLEND_FACTOR_ZERO;
            case ONE:
                return VK10.VK_BLEND_FACTOR_ONE;
            case SRC_COLOR:
                return VK10.VK_BLEND_FACTOR_SRC_COLOR;
            case ONE_MINUS_SRC_COLOR:
                return VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case DST_COLOR:
                return VK10.VK_BLEND_FACTOR_DST_COLOR;
            case ONE_MINUS_DST_COLOR:
                return VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case SRC_ALPHA:
                return VK10.VK_BLEND_FACTOR_SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA:
                return VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA:
                return VK10.VK_BLEND_FACTOR_DST_ALPHA;
            case ONE_MINUS_DST_ALPHA:
                return VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case CONSTANT_COLOR:
                return VK10.VK_BLEND_FACTOR_CONSTANT_COLOR;
            case ONE_MINUS_CONSTANT_COLOR:
                return VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
            case CONSTANT_ALPHA:
                return VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA;
            case ONE_MINUS_CONSTANT_ALPHA:
                return VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
            case SRC_ALPHA_SATURATE:
                return VK10.VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
            default:
                return VK10.VK_BLEND_FACTOR_ONE;
        }
    }

    private static int toVkVertexFormatForFloatingInput(DataType dataType, int componentCount) {
        switch (dataType) {
            case UNSIGNED_BYTE:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R8_USCALED;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R8G8_USCALED;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R8G8B8_USCALED;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R8G8B8A8_USCALED;
                }
                break;
            case BYTE:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R8_SSCALED;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R8G8_SSCALED;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R8G8B8_SSCALED;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R8G8B8A8_SSCALED;
                }
                break;
            case UNSIGNED_SHORT:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R16_USCALED;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R16G16_USCALED;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R16G16B16_USCALED;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R16G16B16A16_USCALED;
                }
                break;
            case SHORT:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R16_SSCALED;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R16G16_SSCALED;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R16G16B16_SSCALED;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R16G16B16A16_SSCALED;
                }
                break;
            case FLOAT:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R32_SFLOAT;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R32G32_SFLOAT;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R32G32B32_SFLOAT;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
                }
                break;
            case HALF_FLOAT:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R16_SFLOAT;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R16G16_SFLOAT;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R16G16B16_SFLOAT;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R16G16B16A16_SFLOAT;
                }
                break;
            case INT:
                throw new IllegalArgumentException(
                        "DataType.INT with floating-point shader input is unsupported in Vulkan vertex format mapping"
                );
            default:
                break;
        }
        throw new IllegalArgumentException(
                "Unsupported floating vertex attribute format: dataType=" + dataType + ", componentCount=" + componentCount
        );
    }

    private static int toVkVertexFormatForIntegerInput(DataType dataType, int componentCount) {
        switch (dataType) {
            case UNSIGNED_BYTE:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R8_UINT;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R8G8_UINT;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R8G8B8_UINT;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R8G8B8A8_UINT;
                }
                break;
            case BYTE:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R8_SINT;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R8G8_SINT;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R8G8B8_SINT;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R8G8B8A8_SINT;
                }
                break;
            case UNSIGNED_SHORT:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R16_UINT;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R16G16_UINT;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R16G16B16_UINT;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R16G16B16A16_UINT;
                }
                break;
            case SHORT:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R16_SINT;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R16G16_SINT;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R16G16B16_SINT;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R16G16B16A16_SINT;
                }
                break;
            case FLOAT:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R32_SFLOAT;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R32G32_SFLOAT;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R32G32B32_SFLOAT;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
                }
                break;
            case HALF_FLOAT:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R16_SFLOAT;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R16G16_SFLOAT;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R16G16B16_SFLOAT;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R16G16B16A16_SFLOAT;
                }
                break;
            case INT:
                if (componentCount == 1) {
                    return VK10.VK_FORMAT_R32_SINT;
                }
                if (componentCount == 2) {
                    return VK10.VK_FORMAT_R32G32_SINT;
                }
                if (componentCount == 3) {
                    return VK10.VK_FORMAT_R32G32B32_SINT;
                }
                if (componentCount == 4) {
                    return VK10.VK_FORMAT_R32G32B32A32_SINT;
                }
                break;
            default:
                break;
        }
        throw new IllegalArgumentException(
                "Unsupported integer vertex attribute format: dataType=" + dataType + ", componentCount=" + componentCount
        );
    }

    private static boolean isIntegerInputDataType(DataType dataType) {
        return dataType == DataType.UNSIGNED_BYTE
                || dataType == DataType.BYTE
                || dataType == DataType.UNSIGNED_SHORT
                || dataType == DataType.SHORT
                || dataType == DataType.INT;
    }
}
