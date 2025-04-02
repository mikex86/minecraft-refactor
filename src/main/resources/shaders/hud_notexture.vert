#version 120

// Matrix uniforms
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

// Output to fragment shader
varying vec4 vertexColor;

void main() {
    // Pass vertex position through our custom MVP matrix
    gl_Position = (projectionMatrix * modelViewMatrix) * gl_Vertex;
    
    // Pass color to fragment shader
    vertexColor = gl_Color;
}