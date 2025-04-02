#version 120

// Output to fragment shader
varying vec4 vertexColor;
varying vec2 texCoord;

void main() {
    // Pass vertex position through the current modelview-projection matrix
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;

    // Pass texture coordinates to fragment shader
    texCoord = gl_MultiTexCoord0.xy;

    // Pass color to fragment shader
    vertexColor = gl_Color;
}