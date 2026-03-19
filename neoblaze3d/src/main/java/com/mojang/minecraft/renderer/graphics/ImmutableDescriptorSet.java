package com.mojang.minecraft.renderer.graphics;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable descriptor-set implementation.
 * Resource bindings (uniform handles / texture handles) are fixed at construction time.
 * Uniform payloads may still be updated through their respective Uniform objects.
 */
public final class ImmutableDescriptorSet implements DescriptorSet {
    private final PipelineLayout layout;
    private final Map<Integer, Uniform> uniformsByBinding;
    private final Map<Integer, Texture> texturesByBinding;
    private final Map<Integer, PipelineLayout.Binding> layoutBindingsByBinding;
    private final EnumMap<PipelineLayout.BindingSemantic, Uniform> uniformsBySemantic;
    private final EnumMap<PipelineLayout.BindingSemantic, Integer> bindingsBySemantic;
    private final boolean ownsUniforms;
    private boolean disposed;

    public ImmutableDescriptorSet(PipelineLayout layout, Collection<Uniform> uniforms) {
        this(layout, uniforms, Collections.emptyMap(), true);
    }

    public ImmutableDescriptorSet(PipelineLayout layout,
                                  Collection<Uniform> uniforms,
                                  Map<Integer, Texture> texturesByBinding) {
        this(layout, uniforms, texturesByBinding, true);
    }

    public ImmutableDescriptorSet(PipelineLayout layout,
                                  Collection<Uniform> uniforms,
                                  Map<Integer, Texture> texturesByBinding,
                                  boolean ownsUniforms) {
        if (layout == null) {
            throw new IllegalArgumentException("layout cannot be null");
        }
        if (uniforms == null) {
            throw new IllegalArgumentException("uniforms cannot be null");
        }
        if (texturesByBinding == null) {
            throw new IllegalArgumentException("texturesByBinding cannot be null");
        }

        this.layout = layout;
        this.layoutBindingsByBinding = new HashMap<>();
        for (PipelineLayout.Binding binding : layout.getBindings()) {
            PipelineLayout.Binding previous = this.layoutBindingsByBinding.put(binding.getBinding(), binding);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate binding " + binding.getBinding() + " in layout " + layout.getDebugName()
                );
            }
        }

        Map<Integer, Uniform> uniformMap = new HashMap<>();
        for (Uniform uniform : uniforms) {
            if (uniform == null) {
                throw new IllegalArgumentException("uniforms cannot contain null");
            }
            int binding = uniform.getBinding();
            if (!layout.hasBinding(binding)) {
                throw new IllegalArgumentException(
                        "Uniform binding " + binding + " is not declared by layout " + layout.getDebugName()
                );
            }
            PipelineLayout.Binding declaredBinding = this.layoutBindingsByBinding.get(binding);
            if (declaredBinding == null || !isUniformResourceType(declaredBinding.getResourceType())) {
                throw new IllegalArgumentException(
                        "Uniform binding " + binding + " is not a uniform slot in layout " + layout.getDebugName()
                );
            }
            Uniform previous = uniformMap.put(binding, uniform);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate uniform binding: " + binding);
            }
        }
        for (PipelineLayout.Binding declaredBinding : this.layoutBindingsByBinding.values()) {
            if (!isUniformResourceType(declaredBinding.getResourceType())) {
                continue;
            }
            int binding = declaredBinding.getBinding();
            if (!uniformMap.containsKey(binding)) {
                throw new IllegalArgumentException(
                        "Missing uniform for required binding " + binding + " in layout " + layout.getDebugName()
                );
            }
        }

        Map<Integer, Texture> textureMap = new HashMap<>();
        for (Map.Entry<Integer, Texture> entry : texturesByBinding.entrySet()) {
            int binding = entry.getKey();
            if (!layout.hasBinding(binding)) {
                throw new IllegalArgumentException(
                        "Texture binding " + binding + " is not declared by layout " + layout.getDebugName()
                );
            }
            PipelineLayout.Binding declaredBinding = this.layoutBindingsByBinding.get(binding);
            if (declaredBinding == null || !isTextureResourceType(declaredBinding.getResourceType())) {
                throw new IllegalArgumentException(
                        "Texture binding " + binding + " is not a texture slot in layout " + layout.getDebugName()
                );
            }
            Texture texture = entry.getValue();
            if (texture != null) {
                textureMap.put(binding, texture);
            }
        }

        this.uniformsByBinding = Collections.unmodifiableMap(uniformMap);
        this.texturesByBinding = Collections.unmodifiableMap(textureMap);
        this.ownsUniforms = ownsUniforms;

        this.uniformsBySemantic = new EnumMap<>(PipelineLayout.BindingSemantic.class);
        this.bindingsBySemantic = new EnumMap<>(PipelineLayout.BindingSemantic.class);
        for (PipelineLayout.Binding binding : layout.getBindings()) {
            PipelineLayout.BindingSemantic semantic = binding.getSemantic();
            if (semantic == PipelineLayout.BindingSemantic.NONE) {
                continue;
            }
            bindingsBySemantic.put(semantic, binding.getBinding());
            Uniform uniform = uniformMap.get(binding.getBinding());
            if (uniform != null) {
                uniformsBySemantic.put(semantic, uniform);
            }
        }
    }

    @Override
    public PipelineLayout getLayout() {
        return layout;
    }

    public Uniform getByBinding(int binding) {
        return uniformsByBinding.get(binding);
    }

    @Override
    public Uniform getUniform(int binding) {
        return uniformsByBinding.get(binding);
    }

    public Uniform getRequiredByBinding(int binding) {
        Uniform uniform = uniformsByBinding.get(binding);
        if (uniform == null) {
            throw new IllegalStateException("No uniform registered for binding " + binding);
        }
        return uniform;
    }

    public Uniform getBySemantic(PipelineLayout.BindingSemantic semantic) {
        return uniformsBySemantic.get(semantic);
    }

    public Uniform getRequired(PipelineLayout.BindingSemantic semantic) {
        Uniform uniform = uniformsBySemantic.get(semantic);
        if (uniform == null) {
            throw new IllegalStateException("No uniform registered for semantic " + semantic);
        }
        return uniform;
    }

    public int getBindingForSemantic(PipelineLayout.BindingSemantic semantic) {
        Integer binding = bindingsBySemantic.get(semantic);
        if (binding == null) {
            return -1;
        }
        return binding;
    }

    @Override
    public Texture getTexture(int binding) {
        return texturesByBinding.get(binding);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        if (ownsUniforms) {
            for (Uniform uniform : uniformsByBinding.values()) {
                if (uniform != null && !uniform.isDisposed()) {
                    uniform.dispose();
                }
            }
        }
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private static boolean isTextureResourceType(PipelineLayout.ResourceType resourceType) {
        return resourceType == PipelineLayout.ResourceType.SAMPLED_TEXTURE
                || resourceType == PipelineLayout.ResourceType.SAMPLER
                || resourceType == PipelineLayout.ResourceType.COMBINED_IMAGE_SAMPLER;
    }

    private static boolean isUniformResourceType(PipelineLayout.ResourceType resourceType) {
        return resourceType == PipelineLayout.ResourceType.UNIFORM_BUFFER
                || resourceType == PipelineLayout.ResourceType.STORAGE_BUFFER;
    }
}
