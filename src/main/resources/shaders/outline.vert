#version 450 core

layout(std140, binding = 0) uniform ModelViewUniform {
    mat4 modelViewMatrix;
};
layout(std140, binding = 4) uniform ProjectionUniform {
    mat4 projectionMatrix;
};

// Vertex attributes (only gl_Vertex)
layout(location = 0) in vec3 position;

// Output to fragment shader
layout (location = 0) out vec4 vertexColor;

vec4 backendClipPosition(vec4 clipPos) {
#ifdef VULKAN_BACKEND
    clipPos.y = -clipPos.y;
    clipPos.z = 0.5 * (clipPos.z + clipPos.w);
#endif
    return clipPos;
}

void main() {
    // Pass vertex position through our custom MVP matrix
    gl_Position = backendClipPosition((projectionMatrix * modelViewMatrix) * vec4(position, 1.0));
    
    // Pass color to fragment shader
    vertexColor = vec4(0.0, 0.0, 0.0, 0.5);
}
