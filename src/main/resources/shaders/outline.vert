#version 330 core

// Matrix uniforms
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

// Vertex attributes (only gl_Vertex)
layout(location = 0) in vec3 position;

// Output to fragment shader
out vec4 vertexColor;

void main() {
    // Pass vertex position through our custom MVP matrix
    gl_Position = (projectionMatrix * modelViewMatrix) * vec4(position, 1.0);
    
    // Pass color to fragment shader
    vertexColor = vec4(0.0, 0.0, 0.0, 0.5);
}