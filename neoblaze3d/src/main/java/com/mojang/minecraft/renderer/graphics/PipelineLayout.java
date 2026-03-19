package com.mojang.minecraft.renderer.graphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Backend-neutral pipeline layout.
 * Encodes shader-visible resource binding slots.
 */
public interface PipelineLayout extends GraphicsResource {

    /**
     * Gets the debug label for this layout.
     */
    String getDebugName();

    /**
     * Gets immutable binding metadata.
     */
    List<Binding> getBindings();

    /**
     * Looks up the numeric binding for a semantic.
     *
     * @return Binding index, or {@code -1} if not declared by this layout
     */
    int findBinding(BindingSemantic semantic);

    /**
     * Returns whether this layout declares the given numeric binding.
     */
    boolean hasBinding(int binding);

    enum ResourceType {
        UNIFORM_BUFFER,
        STORAGE_BUFFER,
        SAMPLED_TEXTURE,
        SAMPLER,
        COMBINED_IMAGE_SAMPLER
    }

    enum ShaderStage {
        VERTEX,
        FRAGMENT,
        COMPUTE
    }

    /**
     * Well-known semantics that a pipeline layout may expose.
     * These are backend-neutral logical names that map to backend-specific slots.
     */
    enum BindingSemantic {
        NONE,
        MODEL_VIEW_MATRIX,
        PROJECTION_MATRIX
    }

    /**
     * Single binding declaration in a layout.
     */
    final class Binding {
        private final int binding;
        private final ResourceType resourceType;
        private final ShaderStage stage;
        private final BindingSemantic semantic;

        public Binding(int binding, ResourceType resourceType, ShaderStage stage) {
            this(binding, resourceType, stage, BindingSemantic.NONE);
        }

        public Binding(int binding, ResourceType resourceType, ShaderStage stage, BindingSemantic semantic) {
            if (binding < 0) {
                throw new IllegalArgumentException("binding must be >= 0");
            }
            this.binding = binding;
            this.resourceType = resourceType;
            this.stage = stage;
            this.semantic = semantic == null ? BindingSemantic.NONE : semantic;
        }

        public int getBinding() {
            return binding;
        }

        public ResourceType getResourceType() {
            return resourceType;
        }

        public ShaderStage getStage() {
            return stage;
        }

        public BindingSemantic getSemantic() {
            return semantic;
        }
    }

    /**
     * Value object used to create pipeline layouts.
     */
    final class Descriptor {
        private final String debugName;
        private final List<Binding> bindings;

        public Descriptor(String debugName, List<Binding> bindings) {
            this.debugName = debugName == null ? "" : debugName;
            if (bindings == null || bindings.isEmpty()) {
                this.bindings = Collections.emptyList();
            } else {
                this.bindings = Collections.unmodifiableList(new ArrayList<>(bindings));
            }
        }

        public String getDebugName() {
            return debugName;
        }

        public List<Binding> getBindings() {
            return bindings;
        }
    }
}
