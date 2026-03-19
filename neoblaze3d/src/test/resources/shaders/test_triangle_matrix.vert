#version 450 core

layout (location = 0) in vec3 position;
layout (location = 1) in vec3 color;

layout (location = 0) uniform mat4 modelViewMatrix;
layout (location = 4) uniform mat4 projectionMatrix;

layout (location = 0) out vec3 vColor;

void main() {
    vColor = color;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
