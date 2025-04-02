#version 120

// Matrix uniforms
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelViewProjectionMatrix;

// Fog uniforms
uniform bool fogEnabled;
uniform int fogMode;      // 0 = LINEAR, 1 = EXP, 2 = EXP2
uniform float fogDensity;
uniform float fogStart;
uniform float fogEnd;
uniform vec4 fogColor;

// Output to fragment shader
varying vec4 vertexColor;
varying vec2 texCoord;
varying float fogFactor;

void main() {
    // Pass vertex position through our custom MVP matrix
    gl_Position = modelViewProjectionMatrix * gl_Vertex;

    // Pass texture coordinates to fragment shader
    texCoord = gl_MultiTexCoord0.xy;

    // Pass color to fragment shader
    vertexColor = gl_Color;

    // Calculate fog
    fogFactor = 1.0; // Default to no fog

    if (fogEnabled) {
        // Use our custom modelViewMatrix instead of gl_ModelViewMatrix
        float eyeDistance = length(modelViewMatrix * gl_Vertex);

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