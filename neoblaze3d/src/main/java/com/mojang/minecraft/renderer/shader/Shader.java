package com.mojang.minecraft.renderer.shader;

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
public class Shader implements IShader {
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
        
        // Bind attribute locations to match our VAO setup
        glBindAttribLocation(programId, 0, "position");
        glBindAttribLocation(programId, 1, "color");
        glBindAttribLocation(programId, 2, "texCoord0");
        glBindAttribLocation(programId, 3, "normal");
        
        glLinkProgram(programId);
        
        // Check program linking
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            dispose();
            throw new RuntimeException("Shader program linking failed: " + log);
        }
    }
    
    @Override
    public void use() {
        glUseProgram(programId);
    }
    
    @Override
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
    
    @Override
    public void setUniform(String name, boolean value) {
        glUniform1i(getUniformLocation(name), value ? 1 : 0);
    }
    
    @Override
    public void setUniform(String name, int value) {
        glUniform1i(getUniformLocation(name), value);
    }
    
    @Override
    public void setUniform(String name, float value) {
        glUniform1f(getUniformLocation(name), value);
    }
    
    @Override
    public void setUniform(String name, float x, float y) {
        glUniform2f(getUniformLocation(name), x, y);
    }
    
    @Override
    public void setUniform(String name, float x, float y, float z) {
        glUniform3f(getUniformLocation(name), x, y, z);
    }
    
    @Override
    public void setUniform(String name, float x, float y, float z, float w) {
        glUniform4f(getUniformLocation(name), x, y, z, w);
    }
    
    @Override
    public void setUniform4fv(String name, FloatBuffer buffer) {
        glUniform4fv(getUniformLocation(name), buffer);
    }
    
    @Override
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