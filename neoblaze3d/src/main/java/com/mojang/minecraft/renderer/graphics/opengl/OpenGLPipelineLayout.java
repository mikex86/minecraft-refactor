package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.PipelineLayout;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;

/**
 * OpenGL implementation of PipelineLayout.
 */
public final class OpenGLPipelineLayout implements PipelineLayout {
    private final Descriptor descriptor;
    private final EnumMap<BindingSemantic, Integer> semanticBindings;
    private final BitSet declaredBindings;
    private boolean disposed;

    public OpenGLPipelineLayout(Descriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor cannot be null");
        }
        this.descriptor = descriptor;
        this.semanticBindings = new EnumMap<>(BindingSemantic.class);
        this.declaredBindings = new BitSet();
        for (Binding binding : descriptor.getBindings()) {
            declaredBindings.set(binding.getBinding());
            BindingSemantic semantic = binding.getSemantic();
            if (semantic == BindingSemantic.NONE) {
                continue;
            }
            Integer previous = semanticBindings.putIfAbsent(semantic, binding.getBinding());
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate semantic binding for " + semantic + " in layout '" + descriptor.getDebugName() + "'");
            }
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
        return binding;
    }

    @Override
    public boolean hasBinding(int binding) {
        return binding >= 0 && declaredBindings.get(binding);
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
