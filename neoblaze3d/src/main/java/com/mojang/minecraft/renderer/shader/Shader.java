package com.mojang.minecraft.renderer.shader;

import org.lwjgl.opengl.ARBGLSPIRV;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.jemalloc.JEmalloc;
import com.mojang.minecraft.renderer.resource.ResourceBufferLoader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL41C.glShaderBinary;

/**
 * Represents an OpenGL shader program.
 * Handles loading, compiling/specializing, and linking shader programs.
 */
public class Shader implements IShader {
    private static final String SHADER_ENTRY_POINT = "main";
    private static final Map<String, Integer> EXPLICIT_UNIFORM_LOCATIONS = createExplicitUniformLocationMap();

    private final int programId;
    private final int vertexShaderId;
    private final int fragmentShaderId;
    private final Map<String, Integer> uniformLocations;
    private boolean disposed = false;

    private Shader(int programId, int vertexShaderId, int fragmentShaderId) {
        this.programId = programId;
        this.vertexShaderId = vertexShaderId;
        this.fragmentShaderId = fragmentShaderId;
        this.uniformLocations = new HashMap<>();
    }

    /**
     * Creates a shader from precompiled SPIR-V binaries.
     * Paths must be explicit classpath resources and are not modified.
     */
    public static Shader fromPrecompiledBinaries(String vertexBinaryPath, String fragmentBinaryPath) throws IOException {
        if (!supportsSpirv()) {
            throw new RuntimeException("Precompiled SPIR-V loading requires ARB_gl_spirv or OpenGL 4.6");
        }

        int vertex = 0;
        int fragment = 0;
        int program = 0;
        ByteBuffer vertexSpirv = null;
        ByteBuffer fragmentSpirv = null;
        try {
            vertexSpirv = ResourceBufferLoader.loadResourceRequired(
                    Shader.class,
                    vertexBinaryPath,
                    size -> JEmalloc.je_malloc((long) size)
            );
            fragmentSpirv = ResourceBufferLoader.loadResourceRequired(
                    Shader.class,
                    fragmentBinaryPath,
                    size -> JEmalloc.je_malloc((long) size)
            );

            vertex = compileShaderFromSpirv(GL_VERTEX_SHADER, vertexSpirv, vertexBinaryPath);
            fragment = compileShaderFromSpirv(GL_FRAGMENT_SHADER, fragmentSpirv, fragmentBinaryPath);
            program = linkProgram(vertex, fragment);

            return new Shader(program, vertex, fragment);
        } catch (IOException | RuntimeException e) {
            cleanupFailedProgram(program, vertex, fragment);
            throw e;
        } finally {
            if (vertexSpirv != null) {
                JEmalloc.je_free(vertexSpirv);
            }
            if (fragmentSpirv != null) {
                JEmalloc.je_free(fragmentSpirv);
            }
        }
    }

    /**
     * @deprecated Runtime GLSL JIT compilation is deprecated in favor of precompiled SPIR-V binaries.
     */
    @Deprecated
    public static Shader fromJitSource(String vertexSourcePath, String fragmentSourcePath) throws IOException {
        int vertex = 0;
        int fragment = 0;
        int program = 0;
        try {
            String vertexSource = ResourceBufferLoader.loadUtf8ResourceRequired(Shader.class, vertexSourcePath);
            String fragmentSource = ResourceBufferLoader.loadUtf8ResourceRequired(Shader.class, fragmentSourcePath);

            vertex = compileShaderFromSource(GL_VERTEX_SHADER, vertexSource, vertexSourcePath);
            fragment = compileShaderFromSource(GL_FRAGMENT_SHADER, fragmentSource, fragmentSourcePath);
            program = linkProgram(vertex, fragment);

            return new Shader(program, vertex, fragment);
        } catch (IOException | RuntimeException e) {
            cleanupFailedProgram(program, vertex, fragment);
            throw e;
        }
    }

    private static boolean supportsSpirv() {
        GLCapabilities capabilities = GL.getCapabilities();
        return capabilities.OpenGL46 || capabilities.GL_ARB_gl_spirv;
    }

    private static void cleanupFailedProgram(int programId, int vertexShaderId, int fragmentShaderId) {
        if (programId != 0) {
            glDeleteProgram(programId);
        }
        if (vertexShaderId != 0) {
            glDeleteShader(vertexShaderId);
        }
        if (fragmentShaderId != 0) {
            glDeleteShader(fragmentShaderId);
        }
    }

    private static int compileShaderFromSpirv(int shaderType, ByteBuffer spirvBytes, String debugPath) {
        int shaderId = glCreateShader(shaderType);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer shaders = stack.ints(shaderId);
            ByteBuffer bytes = spirvBytes.duplicate();
            bytes.rewind();
            glShaderBinary(shaders, ARBGLSPIRV.GL_SHADER_BINARY_FORMAT_SPIR_V_ARB, bytes);

            // No specialization constants are provided, but LWJGL requires non-null buffers.
            IntBuffer constantIndices = stack.mallocInt(0);
            IntBuffer constantValues = stack.mallocInt(0);
            ARBGLSPIRV.glSpecializeShaderARB(shaderId, SHADER_ENTRY_POINT, constantIndices, constantValues);
        }

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shaderId);
            glDeleteShader(shaderId);
            throw new RuntimeException("SPIR-V shader specialization failed (" + debugPath + "): " + log);
        }

        return shaderId;
    }

    @Deprecated
    private static int compileShaderFromSource(int shaderType, String source, String debugPath) {
        int shaderId = glCreateShader(shaderType);
        glShaderSource(shaderId, source);
        glCompileShader(shaderId);

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shaderId);
            glDeleteShader(shaderId);
            throw new RuntimeException("Shader compilation failed (" + debugPath + "): " + log);
        }

        return shaderId;
    }

    private static int linkProgram(int vertexShaderId, int fragmentShaderId) {
        int programId = glCreateProgram();
        glAttachShader(programId, vertexShaderId);
        glAttachShader(programId, fragmentShaderId);

        // Bind attribute locations to match our VAO setup
        glBindAttribLocation(programId, 0, "position");
        glBindAttribLocation(programId, 1, "color");
        glBindAttribLocation(programId, 2, "texCoord0");
        glBindAttribLocation(programId, 3, "normal");

        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            glDeleteProgram(programId);
            throw new RuntimeException("Shader program linking failed: " + log);
        }

        return programId;
    }

    private static Map<String, Integer> createExplicitUniformLocationMap() {
        Map<String, Integer> locations = new HashMap<>();
        locations.put("modelViewMatrix", 0);
        locations.put("projectionMatrix", 4);
        locations.put("fogDensity", 8);
        locations.put("fogStart", 9);
        locations.put("fogEnd", 10);
        locations.put("fogColor", 11);
        locations.put("lightDirection", 12);
        locations.put("lightColor", 13);
        locations.put("ambientColor", 14);
        // Backward-compat alias used by LightingShader helper.
        locations.put("ambientLight", 14);
        locations.put("normalMatrix", 16);
        return Collections.unmodifiableMap(locations);
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
        if (location < 0) {
            Integer explicitLocation = EXPLICIT_UNIFORM_LOCATIONS.get(name);
            if (explicitLocation != null) {
                location = explicitLocation;
            }
        }
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
