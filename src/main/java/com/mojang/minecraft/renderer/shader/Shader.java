package com.mojang.minecraft.renderer.shader;

import com.mojang.minecraft.renderer.Disposable;
import org.lwjgl.BufferUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

/**
 * Represents an OpenGL shader program.
 * Handles loading, compiling, and linking shader programs.
 */
public class Shader implements Disposable {
    private final int programId;
    private final int vertexShaderId;
    private final int fragmentShaderId;
    private final Map<String, Integer> uniformLocations;
    private boolean disposed = false;
    
    // Buffer for uniform operations
    private final FloatBuffer float4Buffer = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer float16Buffer = BufferUtils.createFloatBuffer(16);
    
    /**
     * Creates a new shader from the specified vertex and fragment shader sources.
     * 
     * @param vertexPath Path to the vertex shader source
     * @param fragmentPath Path to the fragment shader source
     * @throws IOException If shader loading fails
     */
    public Shader(String vertexPath, String fragmentPath) throws IOException {
        uniformLocations = new HashMap<>();
        
        // Load shader sources
        String vertexSource = loadSource(vertexPath);
        String fragmentSource = loadSource(fragmentPath);
        
        // Create and compile vertex shader
        vertexShaderId = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShaderId, vertexSource);
        glCompileShader(vertexShaderId);
        
        // Check vertex shader compilation
        if (glGetShaderi(vertexShaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(vertexShaderId);
            glDeleteShader(vertexShaderId);
            throw new RuntimeException("Vertex shader compilation failed: " + log);
        }
        
        // Create and compile fragment shader
        fragmentShaderId = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShaderId, fragmentSource);
        glCompileShader(fragmentShaderId);
        
        // Check fragment shader compilation
        if (glGetShaderi(fragmentShaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(fragmentShaderId);
            glDeleteShader(vertexShaderId);
            glDeleteShader(fragmentShaderId);
            throw new RuntimeException("Fragment shader compilation failed: " + log);
        }
        
        // Create and link program
        programId = glCreateProgram();
        glAttachShader(programId, vertexShaderId);
        glAttachShader(programId, fragmentShaderId);
        glLinkProgram(programId);
        
        // Check program linking
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            dispose();
            throw new RuntimeException("Shader program linking failed: " + log);
        }
    }
    
    /**
     * Uses this shader program.
     */
    public void use() {
        glUseProgram(programId);
    }
    
    /**
     * Stops using this shader program.
     */
    public void detach() {
        glUseProgram(0);
    }
    
    /**
     * Gets the location of a uniform variable.
     * 
     * @param name The name of the uniform
     * @return The location of the uniform
     */
    public int getUniformLocation(String name) {
        if (uniformLocations.containsKey(name)) {
            return uniformLocations.get(name);
        }
        
        int location = glGetUniformLocation(programId, name);
        uniformLocations.put(name, location);
        return location;
    }
    
    /**
     * Sets a boolean uniform.
     * 
     * @param name The name of the uniform
     * @param value The value to set
     */
    public void setUniform(String name, boolean value) {
        glUniform1i(getUniformLocation(name), value ? 1 : 0);
    }
    
    /**
     * Sets an integer uniform.
     * 
     * @param name The name of the uniform
     * @param value The value to set
     */
    public void setUniform(String name, int value) {
        glUniform1i(getUniformLocation(name), value);
    }
    
    /**
     * Sets a float uniform.
     * 
     * @param name The name of the uniform
     * @param value The value to set
     */
    public void setUniform(String name, float value) {
        glUniform1f(getUniformLocation(name), value);
    }
    
    /**
     * Sets a vec2 uniform.
     * 
     * @param name The name of the uniform
     * @param x The x value
     * @param y The y value
     */
    public void setUniform(String name, float x, float y) {
        glUniform2f(getUniformLocation(name), x, y);
    }
    
    /**
     * Sets a vec3 uniform.
     * 
     * @param name The name of the uniform
     * @param x The x value
     * @param y The y value
     * @param z The z value
     */
    public void setUniform(String name, float x, float y, float z) {
        glUniform3f(getUniformLocation(name), x, y, z);
    }
    
    /**
     * Sets a vec4 uniform.
     * 
     * @param name The name of the uniform
     * @param x The x value
     * @param y The y value
     * @param z The z value
     * @param w The w value
     */
    public void setUniform(String name, float x, float y, float z, float w) {
        glUniform4f(getUniformLocation(name), x, y, z, w);
    }
    
    /**
     * Sets a vec4 uniform from a float buffer.
     * 
     * @param name The name of the uniform
     * @param buffer The buffer containing the values
     */
    public void setUniform4fv(String name, FloatBuffer buffer) {
        glUniform4fv(getUniformLocation(name), buffer);
    }
    
    /**
     * Sets a matrix4 uniform.
     * 
     * @param name The name of the uniform
     * @param matrix The matrix
     */
    public void setUniformMatrix4fv(String name, FloatBuffer matrix) {
        glUniformMatrix4fv(getUniformLocation(name), false, matrix);
    }
    
    /**
     * Loads shader source from a resource file.
     * 
     * @param path The path to the shader source
     * @return The shader source
     * @throws IOException If loading fails
     */
    private String loadSource(String path) throws IOException {
        StringBuilder source = new StringBuilder();
        try (InputStream in = getClass().getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                source.append(line).append("\n");
            }
        }
        return source.toString();
    }
    
    /**
     * Disposes of this shader program.
     */
    @Override
    public void dispose() {
        if (!disposed) {
            glDetachShader(programId, vertexShaderId);
            glDetachShader(programId, fragmentShaderId);
            glDeleteShader(vertexShaderId);
            glDeleteShader(fragmentShaderId);
            glDeleteProgram(programId);
            disposed = true;
        }
    }
} 