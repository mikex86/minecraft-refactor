#version 120

// Matrix uniforms
uniform mat4 modelViewProjectionMatrix;

// Output to fragment shader
varying vec4 vertexColor;

void main() {
    // Pass vertex position through our custom MVP matrix
    gl_Position = modelViewProjectionMatrix * gl_Vertex;
    
    // Pass color to fragment shader
    vertexColor = gl_Color;
}