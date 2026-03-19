package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.ShaderProgram;
import com.mojang.minecraft.renderer.resource.ResourceBufferLoader;
import org.lwjgl.opengl.ARBGLSPIRV;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.jemalloc.JEmalloc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL41C.glShaderBinary;
import static org.lwjgl.opengl.GL43.*;

/**
 * OpenGL shader program implementation.
 */
public final class OpenGLShaderProgram implements ShaderProgram {
    private static final String SHADER_ENTRY_POINT = "main";
    private static final Map<String, Integer> EXPLICIT_UNIFORM_LOCATIONS = createExplicitUniformLocationMap();

    private final int programId;
    private final int vertexShaderId;
    private final int fragmentShaderId;
    private final Map<String, Integer> uniformLocations;
    private final BitSet activeUniformLocations;
    private boolean disposed;

    private OpenGLShaderProgram(int programId, int vertexShaderId, int fragmentShaderId) {
        this.programId = programId;
        this.vertexShaderId = vertexShaderId;
        this.fragmentShaderId = fragmentShaderId;
        this.uniformLocations = new HashMap<>();
        this.activeUniformLocations = queryActiveUniformLocations(programId);
    }

    public static OpenGLShaderProgram fromPrecompiledBinaries(String vertexBinaryPath, String fragmentBinaryPath) throws IOException {
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
                    OpenGLShaderProgram.class,
                    vertexBinaryPath,
                    JEmalloc::je_malloc
            );
            fragmentSpirv = ResourceBufferLoader.loadResourceRequired(
                    OpenGLShaderProgram.class,
                    fragmentBinaryPath,
                    JEmalloc::je_malloc
            );

            vertex = compileShaderFromSpirv(GL_VERTEX_SHADER, vertexSpirv, vertexBinaryPath);
            fragment = compileShaderFromSpirv(GL_FRAGMENT_SHADER, fragmentSpirv, fragmentBinaryPath);
            program = linkProgram(vertex, fragment);

            return new OpenGLShaderProgram(program, vertex, fragment);
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

    @Deprecated
    public static OpenGLShaderProgram fromJitSource(String vertexSourcePath, String fragmentSourcePath) throws IOException {
        int vertex = 0;
        int fragment = 0;
        int program = 0;
        try {
            String vertexSource = ResourceBufferLoader.loadUtf8ResourceRequired(OpenGLShaderProgram.class, vertexSourcePath);
            String fragmentSource = ResourceBufferLoader.loadUtf8ResourceRequired(OpenGLShaderProgram.class, fragmentSourcePath);

            vertex = compileShaderFromSource(GL_VERTEX_SHADER, vertexSource, vertexSourcePath);
            fragment = compileShaderFromSource(GL_FRAGMENT_SHADER, fragmentSource, fragmentSourcePath);
            program = linkProgram(vertex, fragment);

            return new OpenGLShaderProgram(program, vertex, fragment);
        } catch (IOException | RuntimeException e) {
            cleanupFailedProgram(program, vertex, fragment);
            throw e;
        }
    }

    public int getProgramId() {
        return programId;
    }

    public boolean hasUniformLocation(int location) {
        return location >= 0 && activeUniformLocations.get(location);
    }

    public int getUniformLocation(String name) {
        Integer cached = uniformLocations.get(name);
        if (cached != null) {
            return cached;
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

    @Override
    public boolean isDisposed() {
        return disposed;
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

    private static BitSet queryActiveUniformLocations(int programId) {
        BitSet locations = new BitSet();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (GL.getCapabilities().OpenGL43 || GL.getCapabilities().GL_ARB_program_interface_query) {
                int activeUniformCount = glGetProgramInterfacei(programId, GL_UNIFORM, GL_ACTIVE_RESOURCES);
                IntBuffer property = stack.ints(GL_LOCATION);
                IntBuffer length = stack.mallocInt(1);
                IntBuffer value = stack.mallocInt(1);
                for (int i = 0; i < activeUniformCount; i++) {
                    glGetProgramResourceiv(programId, GL_UNIFORM, i, property, length, value);
                    int location = value.get(0);
                    if (location >= 0) {
                        locations.set(location);
                    }
                    value.position(0);
                }
                return locations;
            }

            int activeUniformCount = glGetProgrami(programId, GL_ACTIVE_UNIFORMS);
            if (activeUniformCount <= 0) {
                return locations;
            }
            IntBuffer size = stack.mallocInt(1);
            IntBuffer type = stack.mallocInt(1);
            for (int i = 0; i < activeUniformCount; i++) {
                String uniformName = glGetActiveUniform(programId, i, size, type);
                if (uniformName.isEmpty()) {
                    continue;
                }
                int location = glGetUniformLocation(programId, uniformName);
                if (location >= 0) {
                    locations.set(location);
                }
            }
        }
        return locations;
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
        locations.put("ambientLight", 14);
        locations.put("normalMatrix", 16);
        return Collections.unmodifiableMap(locations);
    }
}
