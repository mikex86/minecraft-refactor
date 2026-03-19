package com.mojang.minecraft.renderer.graphics;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Groups uniforms under a specific pipeline layout and validates compatibility once.
 */
public final class UniformCollection implements GraphicsResource {
    private final PipelineLayout layout;
    private final Map<Integer, Uniform> uniformsByBinding;
    private final EnumMap<PipelineLayout.BindingSemantic, Uniform> uniformsBySemantic;
    private boolean disposed;

    public UniformCollection(PipelineLayout layout, Collection<Uniform> uniforms) {
        if (layout == null) {
            throw new IllegalArgumentException("layout cannot be null");
        }
        if (uniforms == null) {
            throw new IllegalArgumentException("uniforms cannot be null");
        }
        this.layout = layout;

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
            Uniform previous = byBinding.put(binding, uniform);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate uniform binding: " + binding);
            }
        }
        this.uniformsByBinding = Collections.unmodifiableMap(byBinding);

        this.uniformsBySemantic = new EnumMap<>(PipelineLayout.BindingSemantic.class);
        for (PipelineLayout.Binding binding : layout.getBindings()) {
            PipelineLayout.BindingSemantic semantic = binding.getSemantic();
            if (semantic == PipelineLayout.BindingSemantic.NONE) {
                continue;
            }
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

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        for (Uniform uniform : uniformsByBinding.values()) {
            if (uniform != null && !uniform.isDisposed()) {
                uniform.dispose();
            }
        }
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
