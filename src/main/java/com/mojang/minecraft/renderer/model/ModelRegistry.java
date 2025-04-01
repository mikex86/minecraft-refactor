package com.mojang.minecraft.renderer.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages shared model resources across the game.
 * This registry ensures that model resources are efficiently shared and properly disposed.
 */
public class ModelRegistry {
    private static final ModelRegistry INSTANCE = new ModelRegistry();

    private final Map<String, Model> models = new HashMap<>();

    /**
     * Private constructor for singleton pattern
     */
    private ModelRegistry() {
    }

    /**
     * Gets the singleton instance of the model registry
     */
    public static ModelRegistry getInstance() {
        return INSTANCE;
    }


    /**
     * Gets a model with the specified ID, creating it if needed
     *
     * @param modelId      Unique ID for the model
     * @param modelCreator Function to create a new model instance
     * @return The model instance
     */
    @SuppressWarnings("unchecked")
    public <T> T getModel(String modelId, ModelCreator<? extends Model> modelCreator) {
        if (!models.containsKey(modelId)) {
            models.put(modelId, modelCreator.create());
        }
        return (T) models.get(modelId);
    }

    /**
     * Disposes all models in the registry
     */
    public void disposeAll() {
        for (Model model : models.values()) {
            model.dispose();
        }
        models.clear();
    }

    /**
     * Interface for model creation
     */
    @FunctionalInterface
    public interface ModelCreator<T extends Model> {
        T create();
    }
} 