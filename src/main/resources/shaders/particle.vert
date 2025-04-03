#version 330 core

// Matrix uniforms
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

// Fog uniforms
uniform bool fogEnabled;
uniform int fogMode;      // 0 = LINEAR, 1 = EXP, 2 = EXP2
uniform float fogDensity;
uniform float fogStart;
uniform float fogEnd;
uniform vec4 fogColor;

// Vertex attributes (replace gl_Vertex, gl_Color, etc.)
layout(location = 0) in vec3 position;
layout(location = 1) in vec3 color;
layout(location = 2) in vec2 texCoord0;

// Output to fragment shader
out vec4 vertexColor;
out vec2 texCoord;
out float fogFactor;

void main() {
    // Pass vertex position through our custom MVP matrix
    gl_Position = (projectionMatrix * modelViewMatrix) * vec4(position, 1.0);

    // Pass texture coordinates to fragment shader
    texCoord = texCoord0;

    // Pass color to fragment shader
    vertexColor = vec4(color, 1.0);

    // Calculate fog
    fogFactor = 1.0; // Default to no fog

    if (fogEnabled) {
        // Use our custom modelViewMatrix instead of gl_ModelViewMatrix
        float eyeDistance = length(modelViewMatrix * vec4(position, 1.0));

        if (fogMode == 0) {
            // LINEAR fog
            fogFactor = (fogEnd - eyeDistance) / (fogEnd - fogStart);
        } else if (fogMode == 1) {
            // EXP fog
            fogFactor = exp(-fogDensity * eyeDistance);
        } else if (fogMode == 2) {
            // EXP2 fog
            fogFactor = exp(-fogDensity * fogDensity * eyeDistance * eyeDistance);
        }

        // Clamp fog factor between 0 and 1
        fogFactor = clamp(fogFactor, 0.0, 1.0);
    }
} 