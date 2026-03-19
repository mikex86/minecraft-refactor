package com.mojang.minecraft.renderer.graphics;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutable descriptor-set implementation.
 * Texture bindings are mutable; uniform object instances are created once and reused.
 */
public final class MutableDescriptorSet implements DescriptorSet {
    private final PipelineLayout layout;
    private final Map<Integer, Uniform> uniformsByBinding;
    private final Map<Integer, PipelineLayout.Binding> layoutBindingsByBinding;
    private final Map<Integer, Texture> texturesByBinding;
    private final EnumMap<PipelineLayout.BindingSemantic, Uniform> uniformsBySemantic;
    private final EnumMap<PipelineLayout.BindingSemantic, Integer> bindingsBySemantic;
    private final boolean ownsUniforms;
    private boolean disposed;

    public MutableDescriptorSet(PipelineLayout layout, Collection<Uniform> uniforms) {
        this(layout, uniforms, true);
    }

    public MutableDescriptorSet(PipelineLayout layout, Collection<Uniform> uniforms, boolean ownsUniforms) {
        if (layout == null) {
            throw new IllegalArgumentException("layout cannot be null");
        }
        if (uniforms == null) {
            throw new IllegalArgumentException("uniforms cannot be null");
        }
        this.layout = layout;
        this.layoutBindingsByBinding = new HashMap<>();
        for (PipelineLayout.Binding binding : layout.getBindings()) {
            PipelineLayout.Binding previous = layoutBindingsByBinding.put(binding.getBinding(), binding);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate binding " + binding.getBinding() + " in layout " + layout.getDebugName()
                );
            }
        }

        Map<Integer, Uniform> byBinding = new HashMap<>();
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
            PipelineLayout.Binding declaredBinding = layoutBindingsByBinding.get(binding);
            if (declaredBinding == null || !isUniformResourceType(declaredBinding.getResourceType())) {
                throw new IllegalArgumentException(
                        "Uniform binding " + binding + " is not a uniform slot in layout " + layout.getDebugName()
                );
            }
            Uniform previous = byBinding.put(binding, uniform);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate uniform binding: " + binding);
            }
        }
        this.uniformsByBinding = Collections.unmodifiableMap(byBinding);
        this.texturesByBinding = new HashMap<>();
        this.ownsUniforms = ownsUniforms;

        for (PipelineLayout.Binding declaredBinding : layoutBindingsByBinding.values()) {
            if (!isUniformResourceType(declaredBinding.getResourceType())) {
                continue;
            }
            int binding = declaredBinding.getBinding();
            if (!byBinding.containsKey(binding)) {
                throw new IllegalArgumentException(
                        "Missing uniform for required binding " + binding + " in layout " + layout.getDebugName()
                );
            }
        }

        this.uniformsBySemantic = new EnumMap<>(PipelineLayout.BindingSemantic.class);
        this.bindingsBySemantic = new EnumMap<>(PipelineLayout.BindingSemantic.class);
        for (PipelineLayout.Binding binding : layout.getBindings()) {
            PipelineLayout.BindingSemantic semantic = binding.getSemantic();
            if (semantic == PipelineLayout.BindingSemantic.NONE) {
                continue;
            }
            bindingsBySemantic.put(semantic, binding.getBinding());
            Uniform uniform = byBinding.get(binding.getBinding());
            if (uniform != null) {
                uniformsBySemantic.put(semantic, uniform);
            }
        }
    }

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

    public void setTexture(PipelineLayout.BindingSemantic semantic, Texture texture) {
        Integer binding = bindingsBySemantic.get(semantic);
        if (binding == null) {
            throw new IllegalStateException("No binding registered for semantic " + semantic);
        }
        setTexture(binding, texture);
    }

    @Override
    public Texture getTexture(int binding) {
        return texturesByBinding.get(binding);
    }

    public void setTexture(int binding, Texture texture) {
        if (!layout.hasBinding(binding)) {
            throw new IllegalArgumentException(
                    "Texture binding " + binding + " is not declared by layout " + layout.getDebugName()
            );
        }
        PipelineLayout.Binding declaredBinding = layoutBindingsByBinding.get(binding);
        if (declaredBinding == null || !isTextureResourceType(declaredBinding.getResourceType())) {
            throw new IllegalArgumentException(
                    "Texture binding " + binding + " is not a texture slot in layout " + layout.getDebugName()
            );
        }
        if (texture == null) {
            texturesByBinding.remove(binding);
            return;
        }
        texturesByBinding.put(binding, texture);
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
        texturesByBinding.clear();
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
