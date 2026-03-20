#version 450 core

layout(std140, binding = 0) uniform ModelViewUniform {
    mat4 modelViewMatrix;
};
layout(std140, binding = 4) uniform ProjectionUniform {
    mat4 projectionMatrix;
};

// Vertex attributes (replace gl_Vertex, gl_Color, etc.)
layout(location = 0) in vec3 position;
layout(location = 1) in vec3 color;
layout(location = 2) in vec2 texCoord0;

// Output to fragment shader
layout (location = 0) out vec4 vertexColor;
layout (location = 1) out vec2 texCoord;

vec4 backendClipPosition(vec4 clipPos) {
#ifdef VULKAN_BACKEND
    clipPos.y = -clipPos.y;
    clipPos.z = 0.5 * (clipPos.z + clipPos.w);
#endif
    return clipPos;
}

void main() {
    gl_Position = backendClipPosition((projectionMatrix * modelViewMatrix) * vec4(position, 1.0));

    // Pass texture coordinates to fragment shader
    texCoord = texCoord0;

    // Pass color to fragment shader
    vertexColor = vec4(color, 1.0);
} 
