#version 330 core

// Matrix uniforms
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

// Fog uniforms
uniform float fogDensity;
uniform float fogStart;
uniform float fogEnd;
uniform vec4 fogColor;

// Vertex attributes (replace gl_Vertex, gl_Color, etc.)
layout (location = 0) in vec3 position;
layout (location = 1) in float color;
layout (location = 2) in vec2 texCoord0;
layout (location = 3) in vec3 normal;

// Output to fragment shader
out float vertexColor;
out vec2 texCoord;
out float fogFactor;

void main() {
    gl_Position = (projectionMatrix * modelViewMatrix) * vec4(position, 1.0);

    // Pass texture coordinates to fragment shader
    texCoord = texCoord0;

    // Pass color to fragment shader
    vertexColor = color;

    // Calculate fog
    fogFactor = 1.0; // Default to no fog

    float eyeDistance = length(modelViewMatrix * vec4(position, 1.0));

    // EXP fog
    fogFactor = exp(-fogDensity * eyeDistance);

    // Clamp fog factor between 0 and 1
    fogFactor = clamp(fogFactor, 0.0, 1.0);
} 