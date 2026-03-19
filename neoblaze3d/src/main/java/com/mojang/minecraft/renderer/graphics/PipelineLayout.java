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
     * Single binding declaration in a layout.
     */
    final class Binding {
        private final int binding;
        private final ResourceType resourceType;
        private final ShaderStage stage;

        public Binding(int binding, ResourceType resourceType, ShaderStage stage) {
            if (binding < 0) {
                throw new IllegalArgumentException("binding must be >= 0");
            }
            this.binding = binding;
            this.resourceType = resourceType;
            this.stage = stage;
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

