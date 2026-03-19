#version 450 core

// Matrix uniforms
layout (location = 0) uniform mat4 modelViewMatrix;
layout (location = 4) uniform mat4 projectionMatrix;

// Fog uniforms
layout (location = 8) uniform float fogDensity;
layout (location = 9) uniform float fogStart;
layout (location = 10) uniform float fogEnd;
layout (location = 11) uniform vec4 fogColor;

// Directional lighting uniforms
layout (location = 12) uniform vec3 lightDirection;  // normalized light direction in eye space
layout (location = 13) uniform vec3 lightColor;      // directional light color/intensity
layout (location = 14) uniform vec3 ambientColor;    // ambient light color
layout (location = 16) uniform mat3 normalMatrix;    // normal matrix: transpose(inverse(mat3(modelViewMatrix)))

// Vertex attributes (replace gl_Vertex, gl_Color, etc.)
layout (location = 0) in vec3 position;
layout (location = 1) in vec3 color;
layout (location = 2) in vec2 texCoord0;
layout (location = 3) in vec3 normal;

// Output to fragment shader
layout (location = 0) out vec4 vertexColor;
layout (location = 1) out vec2 texCoord;
layout (location = 2) out float fogFactor;

void main() {
    // Pass vertex position through our custom MVP matrix
    gl_Position = (projectionMatrix * modelViewMatrix) * vec4(position, 1.0);

    // Pass texture coordinates to fragment shader
    texCoord = texCoord0;

    // Pass color to fragment shader
    vertexColor = vec4(color, 1.0);

    // Calculate fog
    fogFactor = 1.0; // Default to no fog

    // Use our custom modelViewMatrix instead of gl_ModelViewMatrix
    float eyeDistance = length(modelViewMatrix * vec4(position, 1.0));

    // EXP fog
    fogFactor = exp(-fogDensity * eyeDistance);

    // Clamp fog factor between 0 and 1
    fogFactor = clamp(fogFactor, 0.0, 1.0);
}
