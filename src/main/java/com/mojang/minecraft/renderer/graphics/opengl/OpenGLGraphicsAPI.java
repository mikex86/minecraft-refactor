package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.*;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.*;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import com.mojang.minecraft.renderer.shader.Shader;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

/**
 * OpenGL implementation of the GraphicsAPI interface.
 * This implementation uses LWJGL to interact with OpenGL.
 */
public class OpenGLGraphicsAPI implements GraphicsAPI {

    // OpenGL-specific resources
    private final FloatBuffer matrixBuffer;
    private final FloatBuffer colorBuffer;
    
    // Current shader
    private Shader currentShader = null;
    
    // Matrix buffers
    private final FloatBuffer modelViewBuffer = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
    
    /**
     * Creates a new OpenGL graphics API implementation.
     */
    public OpenGLGraphicsAPI() {
        this.matrixBuffer = BufferUtils.createFloatBuffer(16);
        this.colorBuffer = BufferUtils.createFloatBuffer(4);
    }
    
    @Override
    public void initialize() {
        // Enable texture mapping
        glEnable(GL_TEXTURE_2D);
        
        // Set up blending
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Set up depth testing
        glClearDepth(1.0f);
        glDepthFunc(GL_LEQUAL);
        
        // Enable alpha testing
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.0f);
        
        // Set up smooth shading
        glShadeModel(GL_SMOOTH);
        
        // Initialize matrix stacks
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }
    
    @Override
    public void shutdown() {
        // Nothing to do for OpenGL shutdown
    }
    
    @Override
    public VertexBuffer createVertexBuffer(BufferUsage usage) {
        return new OpenGLVertexBuffer(translateBufferUsage(usage));
    }
    
    @Override
    public Texture createTexture(int width, int height, TextureFormat format) {
        return new OpenGLTexture(width, height, format);
    }
    
    @Override
    public Texture createTexture(int width, int height, TextureFormat format, ByteBuffer data) {
        OpenGLTexture texture = new OpenGLTexture(width, height, format);
        texture.update(0, 0, width, height, data);
        return texture;
    }
    
    @Override
    public void setBlendState(boolean enabled, BlendFactor srcFactor, BlendFactor dstFactor) {
        if (enabled) {
            glEnable(GL_BLEND);
            glBlendFunc(translateBlendFactor(srcFactor), translateBlendFactor(dstFactor));
        } else {
            glDisable(GL_BLEND);
        }
    }
    
    @Override
    public void setDepthState(boolean depthTest, boolean depthMask, CompareFunc depthFunc) {
        if (depthTest) {
            glEnable(GL_DEPTH_TEST);
        } else {
            glDisable(GL_DEPTH_TEST);
        }
        glDepthFunc(translateCompareFunc(depthFunc));
        glDepthMask(depthMask);
    }
    
    @Override
    public void setRasterizerState(CullMode cullMode, FillMode fillMode) {
        // Set face culling
        if (cullMode == CullMode.NONE) {
            glDisable(GL_CULL_FACE);
        } else {
            glEnable(GL_CULL_FACE);
            glCullFace(translateCullMode(cullMode));
        }
        
        // Set fill mode
        glPolygonMode(GL_FRONT_AND_BACK, translateFillMode(fillMode));
    }
    
    @Override
    public void setViewport(int x, int y, int width, int height) {
        glViewport(x, y, width, height);
    }
    
    @Override
    public void clear(boolean clearColor, boolean clearDepth, float r, float g, float b, float a) {
        int bits = 0;
        
        if (clearColor) {
            bits |= GL_COLOR_BUFFER_BIT;
            glClearColor(r, g, b, a);
        }
        
        if (clearDepth) {
            bits |= GL_DEPTH_BUFFER_BIT;
        }
        
        glClear(bits);
    }
    
    @Override
    public void setPerspectiveProjection(float fov, float aspect, float nearPlane, float farPlane) {
        // Calculate perspective matrix parameters
        float yScale = (float) (1.0 / Math.tan(Math.toRadians(fov / 2.0)));
        float xScale = yScale / aspect;
        float frustumLength = farPlane - nearPlane;
        
        // Set up the projection matrix
        matrixBuffer.clear();
        matrixBuffer.put(xScale).put(0).put(0).put(0);
        matrixBuffer.put(0).put(yScale).put(0).put(0);
        matrixBuffer.put(0).put(0).put(-((farPlane + nearPlane) / frustumLength)).put(-1);
        matrixBuffer.put(0).put(0).put(-((2 * nearPlane * farPlane) / frustumLength)).put(0);
        matrixBuffer.flip();
        
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glLoadMatrixf(matrixBuffer);
        glMatrixMode(GL_MODELVIEW);
    }
    
    @Override
    public void setOrthographicProjection(float left, float right, float bottom, float top, float near, float far) {
        matrixBuffer.clear();
        matrixBuffer.put(2 / (right - left)).put(0).put(0).put(0);
        matrixBuffer.put(0).put(2 / (top - bottom)).put(0).put(0);
        matrixBuffer.put(0).put(0).put(-2 / (far - near)).put(0);
        matrixBuffer.put(-(right + left) / (right - left)).put(-(top + bottom) / (top - bottom)).put(-(far + near) / (far - near)).put(1);
        matrixBuffer.flip();
        
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glLoadMatrixf(matrixBuffer);
        glMatrixMode(GL_MODELVIEW);
    }
    
    @Override
    public void pushMatrix() {
        glPushMatrix();
    }
    
    @Override
    public void popMatrix() {
        glPopMatrix();
    }
    
    @Override
    public void loadIdentity() {
        glLoadIdentity();
    }
    
    @Override
    public void translate(float x, float y, float z) {
        glTranslatef(x, y, z);
    }
    
    @Override
    public void rotateX(float angle) {
        glRotatef(angle, 1.0f, 0.0f, 0.0f);
    }
    
    @Override
    public void rotateY(float angle) {
        glRotatef(angle, 0.0f, 1.0f, 0.0f);
    }
    
    @Override
    public void rotateZ(float angle) {
        glRotatef(angle, 0.0f, 0.0f, 1.0f);
    }
    
    @Override
    public void scale(float x, float y, float z) {
        glScalef(x, y, z);
    }
    
    @Override
    public void setMatrixMode(MatrixMode mode) {
        switch (mode) {
            case MODELVIEW:
                glMatrixMode(GL_MODELVIEW);
                break;
            case PROJECTION:
                glMatrixMode(GL_PROJECTION);
                break;
            case TEXTURE:
                glMatrixMode(GL_TEXTURE);
                break;
        }
    }
    
    @Override
    public void drawPrimitives(VertexBuffer buffer, PrimitiveType type, int start, int count) {
        if (buffer instanceof OpenGLVertexBuffer) {
            OpenGLVertexBuffer glBuffer = (OpenGLVertexBuffer) buffer;
            VertexBuffer.VertexFormat format = buffer.getFormat();
            
            // Bind VBO
            glBuffer.bind();
            
            // Set up vertex attribute pointers
            int stride = format.getStrideInBytes();
            int offset = 0;
            
            // Enable vertex arrays
            glEnableClientState(GL_VERTEX_ARRAY);
            
            // Set up texture coordinates if present
            if (format.hasTexCoords()) {
                glEnableClientState(GL_TEXTURE_COORD_ARRAY);
                glTexCoordPointer(2, GL_FLOAT, stride, offset);
                offset += 2 * 4; // 2 floats * 4 bytes
            }
            
            // Set up colors if present
            if (format.hasColors()) {
                glEnableClientState(GL_COLOR_ARRAY);
                glColorPointer(3, GL_FLOAT, stride, offset);
                offset += 3 * 4; // 3 floats * 4 bytes
            }
            
            // Set up normals if present
            if (format.hasNormals()) {
                glEnableClientState(GL_NORMAL_ARRAY);
                glNormalPointer(GL_FLOAT, stride, offset);
                offset += 3 * 4; // 3 floats * 4 bytes
            }
            
            // Set up positions (must be present)
            if (format.hasPositions()) {
                glVertexPointer(3, GL_FLOAT, stride, offset);
            }
            
            // Draw the primitives
            glDrawArrays(translatePrimitiveType(type), start, count);
            
            // Disable vertex arrays
            glDisableClientState(GL_VERTEX_ARRAY);
            if (format.hasTexCoords()) {
                glDisableClientState(GL_TEXTURE_COORD_ARRAY);
            }
            if (format.hasColors()) {
                glDisableClientState(GL_COLOR_ARRAY);
            }
            if (format.hasNormals()) {
                glDisableClientState(GL_NORMAL_ARRAY);
            }
            
            // Unbind VBO
            glBuffer.unbind();
        } else {
            throw new IllegalArgumentException("Buffer must be an OpenGLVertexBuffer");
        }
    }
    
    @Override
    public void setTexture(Texture texture) {
        if (texture == null) {
            glBindTexture(GL_TEXTURE_2D, 0);
        } else if (texture instanceof OpenGLTexture) {
            ((OpenGLTexture) texture).bind();
        } else {
            throw new IllegalArgumentException("Not an OpenGL texture");
        }
    }
    
    @Override
    public void setTexturingEnabled(boolean enabled) {
        if (enabled) {
            glEnable(GL_TEXTURE_2D);
        } else {
            glDisable(GL_TEXTURE_2D);
        }
    }
    
    @Override
    public void setVertexColorEnabled(boolean enabled) {
        if (enabled) {
            glEnable(GL_COLOR_MATERIAL);
        } else {
            glDisable(GL_COLOR_MATERIAL);
        }
    }

    @Override
    public void setShader(Shader shader) {
        if (shader != null) {
            shader.use();
            currentShader = shader;
        } else {
            if (currentShader != null) {
                currentShader.detach();
                currentShader = null;
            }
        }
    }
    
    @Override
    public void setLighting(boolean enabled, float ambientR, float ambientG, float ambientB) {
        if (enabled) {
            glEnable(GL_LIGHTING);
            
            // Set ambient light
            colorBuffer.clear();
            colorBuffer.put(ambientR).put(ambientG).put(ambientB).put(1.0f);
            colorBuffer.flip();
            glLightModelfv(GL_LIGHT_MODEL_AMBIENT, colorBuffer);
            
        } else {
            glDisable(GL_LIGHTING);
        }
    }
    
    //--------------------------------------------------
    // Helper methods to translate enums to OpenGL constants
    //--------------------------------------------------
    
    private int translateBufferUsage(BufferUsage usage) {
        switch (usage) {
            case STATIC:
                return GL_STATIC_DRAW;
            case DYNAMIC:
                return GL_DYNAMIC_DRAW;
            case STREAM:
                return GL_STREAM_DRAW;
            default:
                return GL_STATIC_DRAW;
        }
    }
    
    private int translateBlendFactor(BlendFactor factor) {
        switch (factor) {
            case ZERO:
                return GL_ZERO;
            case ONE:
                return GL_ONE;
            case SRC_COLOR:
                return GL_SRC_COLOR;
            case ONE_MINUS_SRC_COLOR:
                return GL_ONE_MINUS_SRC_COLOR;
            case DST_COLOR:
                return GL_DST_COLOR;
            case ONE_MINUS_DST_COLOR:
                return GL_ONE_MINUS_DST_COLOR;
            case SRC_ALPHA:
                return GL_SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA:
                return GL_ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA:
                return GL_DST_ALPHA;
            case ONE_MINUS_DST_ALPHA:
                return GL_ONE_MINUS_DST_ALPHA;
            case CONSTANT_COLOR:
                return GL_CONSTANT_COLOR;
            case ONE_MINUS_CONSTANT_COLOR:
                return GL_ONE_MINUS_CONSTANT_COLOR;
            case CONSTANT_ALPHA:
                return GL_CONSTANT_ALPHA;
            case ONE_MINUS_CONSTANT_ALPHA:
                return GL_ONE_MINUS_CONSTANT_ALPHA;
            case SRC_ALPHA_SATURATE:
                return GL_SRC_ALPHA_SATURATE;
            default:
                return GL_ONE;
        }
    }
    
    private int translateCompareFunc(CompareFunc func) {
        switch (func) {
            case NEVER:
                return GL_NEVER;
            case LESS:
                return GL_LESS;
            case EQUAL:
                return GL_EQUAL;
            case LESS_EQUAL:
                return GL_LEQUAL;
            case GREATER:
                return GL_GREATER;
            case NOT_EQUAL:
                return GL_NOTEQUAL;
            case GREATER_EQUAL:
                return GL_GEQUAL;
            case ALWAYS:
                return GL_ALWAYS;
            default:
                return GL_LESS;
        }
    }
    
    private int translateCullMode(CullMode mode) {
        switch (mode) {
            case FRONT:
                return GL_FRONT;
            case BACK:
                return GL_BACK;
            default:
                return GL_BACK;
        }
    }
    
    private int translateFillMode(FillMode mode) {
        switch (mode) {
            case POINT:
                return GL_POINT;
            case WIREFRAME:
                return GL_LINE;
            case SOLID:
                return GL_FILL;
            default:
                return GL_FILL;
        }
    }
    
    private int translatePrimitiveType(PrimitiveType type) {
        switch (type) {
            case POINTS:
                return GL_POINTS;
            case LINES:
                return GL_LINES;
            case LINE_STRIP:
                return GL_LINE_STRIP;
            case TRIANGLES:
                return GL_TRIANGLES;
            case TRIANGLE_STRIP:
                return GL_TRIANGLE_STRIP;
            case TRIANGLE_FAN:
                return GL_TRIANGLE_FAN;
            case QUADS:
                return GL_QUADS;
            default:
                return GL_TRIANGLES;
        }
    }
    
    private int translateFogMode(FogMode mode) {
        switch (mode) {
            case LINEAR:
                return GL_LINEAR;
            case EXP:
                return GL_EXP;
            case EXP2:
                return GL_EXP2;
            default:
                return GL_EXP;
        }
    }
} 