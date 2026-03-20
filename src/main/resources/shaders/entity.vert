#version 450 core

layout(std140, binding = 0) uniform ModelViewUniform {
    mat4 modelViewMatrix;
};
layout(std140, binding = 4) uniform ProjectionUniform {
    mat4 projectionMatrix;
};
layout(std140, binding = 8) uniform FogDensityUniform {
    float fogDensity;
};
layout(std140, binding = 9) uniform FogStartUniform {
    float fogStart;
};
layout(std140, binding = 10) uniform FogEndUniform {
    float fogEnd;
};
layout(std140, binding = 11) uniform FogColorUniform {
    vec4 fogColor;
};

// Vertex attributes (replace gl_Vertex, gl_Color, etc.)
layout (location = 0) in vec3 position;
layout (location = 1) in vec3 color;
layout (location = 2) in vec2 texCoord0;
layout (location = 3) in vec3 normal;

// Output to fragment shader
layout (location = 0) out vec4 vertexColor;
layout (location = 1) out vec2 texCoord;
layout (location = 2) out float fogFactor;

vec4 backendClipPosition(vec4 clipPos) {
#ifdef VULKAN_BACKEND
    clipPos.y = -clipPos.y;
    clipPos.z = 0.5 * (clipPos.z + clipPos.w);
#endif
    return clipPos;
}

void main() {
    // Pass vertex position through our custom MVP matrix
    gl_Position = backendClipPosition((projectionMatrix * modelViewMatrix) * vec4(position, 1.0));

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
