#version 120

// Matrix uniforms
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

// Output to fragment shader
varying vec4 vertexColor;
varying vec2 texCoord;

void main() {
    // Pass vertex position through our custom MVP matrix
    gl_Position = (projectionMatrix * modelViewMatrix) * gl_Vertex;
    
    // Pass texture coordinates to fragment shader
    texCoord = gl_MultiTexCoord0.xy;
    
    // Pass color to fragment shader
    vertexColor = gl_Color;
}