#version 330 core

// Matrix uniforms
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

// Vertex attributes (replace gl_Vertex, gl_Color, etc.)
layout(location = 0) in vec3 position;
layout(location = 1) in vec3 color;

// Output to fragment shader
out vec4 vertexColor;

void main() {
    // Pass vertex position through our custom MVP matrix
    gl_Position = (projectionMatrix * modelViewMatrix) * vec4(position, 1.0);
    
    // Pass color to fragment shader
    vertexColor = vec4(color, 1.0);
}