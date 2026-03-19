#version 450 core

// Matrix uniforms
layout (location = 0) uniform mat4 modelViewMatrix;
layout (location = 4) uniform mat4 projectionMatrix;

// Fog uniforms
layout (location = 8) uniform float fogDensity;
layout (location = 9) uniform float fogStart;
layout (location = 10) uniform float fogEnd;
layout (location = 11) uniform vec4 fogColor;

// Vertex attributes (replace gl_Vertex, gl_Color, etc.)
layout (location = 0) in vec3 position;
layout (location = 1) in uint color;
layout (location = 2) in vec2 texCoord0;
layout (location = 3) in vec3 normal;

// Output to fragment shader
layout (location = 0) out float vertexColor;
layout (location = 1) out vec2 texCoord;
layout (location = 2) out float fogFactor;

void main() {
    gl_Position = (projectionMatrix * modelViewMatrix) * vec4(position, 1.0);

    // Pass texture coordinates to fragment shader
    texCoord = texCoord0;

    // Pass color to fragment shader
    vertexColor = color / 255.0f;

    float eyeDistance = length(modelViewMatrix * vec4(position, 1.0));

    // EXP fog
    fogFactor = exp(-fogDensity * eyeDistance);

    // Clamp fog factor between 0 and 1
    fogFactor = clamp(fogFactor, 0.0, 1.0);
} 
