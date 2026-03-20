#version 450 core

layout (location = 0) in vec3 position;
layout (location = 1) in vec3 color;

layout(std140, binding = 0) uniform ModelViewUniform {
    mat4 modelViewMatrix;
};
layout(std140, binding = 4) uniform ProjectionUniform {
    mat4 projectionMatrix;
};

layout (location = 0) out vec3 vColor;

vec4 backendClipPosition(vec4 clipPos) {
#ifdef VULKAN_BACKEND
    clipPos.z = 0.5 * (clipPos.z + clipPos.w);
#endif
    return clipPos;
}

void main() {
    vColor = color;
    gl_Position = backendClipPosition(projectionMatrix * modelViewMatrix * vec4(position, 1.0));
}
