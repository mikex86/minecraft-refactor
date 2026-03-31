package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.GraphicsEnums.TextureFormat;
import com.mojang.minecraft.renderer.graphics.ResourceState;
import com.mojang.minecraft.renderer.graphics.Texture;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

final class VulkanTexture implements Texture, VulkanTextureStateTracked {
    private final VulkanContext context;
    private final int width;
    private final int height;
    private final TextureFormat format;

    private long image;
    private long imageMemory;
    private long imageView;
    private long sampler;

    private FilterMode minFilter = FilterMode.NEAREST;
    private FilterMode magFilter = FilterMode.NEAREST;
    private WrapMode wrapS = WrapMode.CLAMP_TO_EDGE;
    private WrapMode wrapT = WrapMode.CLAMP_TO_EDGE;

    private ResourceState.TextureAccess textureAccess = ResourceState.TextureAccess.UNDEFINED;
    private int currentLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED;
    private boolean disposed;

    VulkanTexture(VulkanContext context, int width, int height, TextureFormat format) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Texture dimensions must be > 0");
        }
        if (format == null) {
            throw new IllegalArgumentException("format cannot be null");
        }

        this.context = context;
        this.width = width;
        this.height = height;
        this.format = format;

        int vkFormat = toVkFormat(format);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pImage = stack.mallocLong(1);
            LongBuffer pMemory = stack.mallocLong(1);
            context.createImage(
                    width,
                    height,
                    vkFormat,
                    VK10.VK_IMAGE_TILING_OPTIMAL,
                    VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    pImage,
                    pMemory
            );
            image = pImage.get(0);
            imageMemory = pMemory.get(0);
            imageView = context.createImageView(image, vkFormat, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
        }

        recreateSampler();
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public TextureFormat getFormat() {
        return format;
    }

    @Override
    public void update(int x, int y, int width, int height, ByteBuffer data) {
        if (disposed) {
            throw new IllegalStateException("Texture has been disposed");
        }
        if (textureAccess != ResourceState.TextureAccess.TRANSFER_DST) {
            throw new IllegalStateException("Texture update requires state TRANSFER_DST but was " + textureAccess);
        }
        if (currentLayout != VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            throw new IllegalStateException("Texture update requires transfer-dst layout");
        }
        if (x != 0 || y != 0 || width != this.width || height != this.height) {
            throw new UnsupportedOperationException("Partial Vulkan texture updates are not yet supported");
        }
        if (data == null) {
            throw new IllegalArgumentException("data cannot be null");
        }

        int requiredBytes = this.width * this.height * bytesPerPixel(format);
        if (data.remaining() < requiredBytes) {
            throw new IllegalArgumentException("Texture data is too small. Required " + requiredBytes + " bytes, got " + data.remaining());
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pMemory = stack.mallocLong(1);

            context.createBuffer(
                    requiredBytes,
                    VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer,
                    pMemory
            );

            long stagingBuffer = pBuffer.get(0);
            long stagingMemory = pMemory.get(0);

            PointerBuffer pMapped = stack.mallocPointer(1);
            context.checkVk(VK10.vkMapMemory(context.getDevice(), stagingMemory, 0, requiredBytes, 0, pMapped), "vkMapMemory(texture staging)");
            long dstAddress = pMapped.get(0);
            copyByteBuffer(data, dstAddress, requiredBytes);
            VK10.vkUnmapMemory(context.getDevice(), stagingMemory);

            context.copyBufferToImageImmediate(stagingBuffer, image, this.width, this.height);

            context.destroyBufferWithMemoryImmediate(stagingBuffer, stagingMemory);
        }
    }

    @Override
    public void setFiltering(FilterMode minFilter, FilterMode magFilter) {
        if (disposed) {
            throw new IllegalStateException("Texture has been disposed");
        }
        if (minFilter == null || magFilter == null) {
            throw new IllegalArgumentException("Filter modes cannot be null");
        }
        this.minFilter = minFilter;
        this.magFilter = magFilter;
        recreateSampler();
    }

    @Override
    public void setWrapping(WrapMode wrapS, WrapMode wrapT) {
        if (disposed) {
            throw new IllegalStateException("Texture has been disposed");
        }
        if (wrapS == null || wrapT == null) {
            throw new IllegalArgumentException("Wrap modes cannot be null");
        }
        this.wrapS = wrapS;
        this.wrapT = wrapT;
        recreateSampler();
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }

        org.lwjgl.vulkan.VkDevice device = context.getDevice();
        if (device != null) {
            if (sampler != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroySampler(device, sampler, null);
            }
            if (imageView != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyImageView(device, imageView, null);
            }
            if (image != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyImage(device, image, null);
            }
            if (imageMemory != VK10.VK_NULL_HANDLE) {
                VK10.vkFreeMemory(device, imageMemory, null);
            }
        }
        sampler = VK10.VK_NULL_HANDLE;
        imageView = VK10.VK_NULL_HANDLE;
        image = VK10.VK_NULL_HANDLE;
        imageMemory = VK10.VK_NULL_HANDLE;

        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public ResourceState.TextureAccess getTextureAccess() {
        return textureAccess;
    }

    @Override
    public void setTextureAccess(ResourceState.TextureAccess access) {
        if (access == null) {
            throw new IllegalArgumentException("access cannot be null");
        }
        this.textureAccess = access;
    }

    long getImageView() {
        return imageView;
    }

    long getSampler() {
        return sampler;
    }

    long getImage() {
        return image;
    }

    int getCurrentLayout() {
        return currentLayout;
    }

    void transition(ResourceState.TextureAccess expectedOldAccess, ResourceState.TextureAccess newAccess) {
        if (disposed) {
            throw new IllegalStateException("Texture has been disposed");
        }
        if (textureAccess != expectedOldAccess) {
            throw new IllegalStateException(
                    "Texture transition mismatch: expected " + expectedOldAccess + " but was " + textureAccess
            );
        }

        int newLayout = layoutForAccess(newAccess);
        if (currentLayout != newLayout) {
            int srcStage = stageForLayout(currentLayout);
            int dstStage = stageForLayout(newLayout);
            int srcAccess = accessMaskForLayout(currentLayout);
            int dstAccess = accessMaskForLayout(newLayout);

            context.transitionImageLayoutImmediate(
                    image,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                    currentLayout,
                    newLayout,
                    srcAccess,
                    dstAccess,
                    srcStage,
                    dstStage
            );
            currentLayout = newLayout;
        }

        textureAccess = newAccess;
    }

    private void recreateSampler() {
        org.lwjgl.vulkan.VkDevice device = context.getDevice();
        if (device == null) {
            // Context already torn down; keep handles as null during late disposal.
            sampler = VK10.VK_NULL_HANDLE;
            return;
        }

        if (sampler != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroySampler(device, sampler, null);
            sampler = VK10.VK_NULL_HANDLE;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(toVkFilter(magFilter))
                    .minFilter(toVkFilter(minFilter))
                    .addressModeU(toVkAddressMode(wrapS))
                    .addressModeV(toVkAddressMode(wrapT))
                    .addressModeW(toVkAddressMode(wrapT))
                    .anisotropyEnable(false)
                    .maxAnisotropy(1.0f)
                    .borderColor(VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(false)
                    .compareEnable(false)
                    .compareOp(VK10.VK_COMPARE_OP_ALWAYS)
                    .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .mipLodBias(0.0f)
                    .minLod(0.0f)
                    .maxLod(0.0f);

            LongBuffer pSampler = stack.mallocLong(1);
            context.checkVk(VK10.vkCreateSampler(device, samplerInfo, null, pSampler), "vkCreateSampler");
            sampler = pSampler.get(0);
        }
    }

    private static int layoutForAccess(ResourceState.TextureAccess access) {
        switch (access) {
            case UNDEFINED:
                return VK10.VK_IMAGE_LAYOUT_UNDEFINED;
            case TRANSFER_DST:
                return VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            case SHADER_READ:
                return VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            default:
                throw new IllegalArgumentException("Unsupported texture access state: " + access);
        }
    }

    private static int stageForLayout(int layout) {
        if (layout == VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
            return VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        }
        if (layout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL || layout == VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
            return VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
        }
        if (layout == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            return VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        }
        return VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
    }

    private static int accessMaskForLayout(int layout) {
        if (layout == VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
            return 0;
        }
        if (layout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            return VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
        }
        if (layout == VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
            return VK10.VK_ACCESS_TRANSFER_READ_BIT;
        }
        if (layout == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            return VK10.VK_ACCESS_SHADER_READ_BIT;
        }
        return 0;
    }

    private static int toVkFormat(TextureFormat format) {
        switch (format) {
            case R8:
                return VK10.VK_FORMAT_R8_UNORM;
            case RG8:
                return VK10.VK_FORMAT_R8G8_UNORM;
            case RGB8:
                return VK10.VK_FORMAT_R8G8B8_UNORM;
            case RGBA8:
                return VK10.VK_FORMAT_R8G8B8A8_UNORM;
            case R16F:
                return VK10.VK_FORMAT_R16_SFLOAT;
            case RG16F:
                return VK10.VK_FORMAT_R16G16_SFLOAT;
            case RGB16F:
                return VK10.VK_FORMAT_R16G16B16_SFLOAT;
            case RGBA16F:
                return VK10.VK_FORMAT_R16G16B16A16_SFLOAT;
            case R32F:
                return VK10.VK_FORMAT_R32_SFLOAT;
            case RG32F:
                return VK10.VK_FORMAT_R32G32_SFLOAT;
            case RGB32F:
                return VK10.VK_FORMAT_R32G32B32_SFLOAT;
            case RGBA32F:
                return VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
            case DEPTH16:
                return VK10.VK_FORMAT_D16_UNORM;
            case DEPTH24:
                return VK10.VK_FORMAT_D24_UNORM_S8_UINT;
            case DEPTH32F:
                return VK10.VK_FORMAT_D32_SFLOAT;
            default:
                throw new IllegalArgumentException("Unsupported texture format: " + format);
        }
    }

    private static int bytesPerPixel(TextureFormat format) {
        switch (format) {
            case R8:
                return 1;
            case RG8:
                return 2;
            case RGB8:
                return 3;
            case RGBA8:
                return 4;
            case R16F:
                return 2;
            case RG16F:
                return 4;
            case RGB16F:
                return 6;
            case RGBA16F:
                return 8;
            case R32F:
                return 4;
            case RG32F:
                return 8;
            case RGB32F:
                return 12;
            case RGBA32F:
                return 16;
            case DEPTH16:
                return 2;
            case DEPTH24:
                return 4;
            case DEPTH32F:
                return 4;
            default:
                throw new IllegalArgumentException("Unsupported texture format: " + format);
        }
    }

    private static int toVkFilter(FilterMode mode) {
        switch (mode) {
            case LINEAR:
            case LINEAR_MIPMAP_LINEAR:
            case LINEAR_MIPMAP_NEAREST:
                return VK10.VK_FILTER_LINEAR;
            default:
                return VK10.VK_FILTER_NEAREST;
        }
    }

    private static int toVkAddressMode(WrapMode mode) {
        switch (mode) {
            case REPEAT:
                return VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT;
            case MIRRORED_REPEAT:
                return VK10.VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
            case CLAMP_TO_EDGE:
                return VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            case CLAMP_TO_BORDER:
                return VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER;
            default:
                return VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT;
        }
    }

    private static void copyByteBuffer(ByteBuffer src, long dstAddress, int sizeInBytes) {
        ByteBuffer source = src.duplicate();
        if (source.remaining() < sizeInBytes) {
            throw new IllegalArgumentException("Source buffer has fewer bytes than requested copy size");
        }

        if (source.isDirect()) {
            long srcAddress = MemoryUtil.memAddress(source);
            MemoryUtil.memCopy(srcAddress, dstAddress, sizeInBytes);
            return;
        }

        ByteBuffer dst = MemoryUtil.memByteBuffer(dstAddress, sizeInBytes);
        for (int i = 0; i < sizeInBytes; i++) {
            dst.put(i, source.get(source.position() + i));
        }
    }
}
