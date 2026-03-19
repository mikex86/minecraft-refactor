#version 450 core

// Matrix uniforms
layout (location = 0) uniform mat4 modelViewMatrix;
layout (location = 4) uniform mat4 projectionMatrix;

// Vertex attributes (only gl_Vertex)
layout(location = 0) in vec3 position;

// Output to fragment shader
layout (location = 0) out vec4 vertexColor;

void main() {
    // Pass vertex position through our custom MVP matrix
    gl_Position = (projectionMatrix * modelViewMatrix) * vec4(position, 1.0);
    
    // Pass color to fragment shader
    vertexColor = vec4(0.0, 0.0, 0.0, 0.5);
}
