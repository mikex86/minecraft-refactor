#version 450 core

// Fog uniforms
layout (location = 11) uniform vec4 fogColor;

// Texture sampler
layout (binding = 1) uniform sampler2D textureSampler;

// Input from vertex shader
layout (location = 0) in vec4 vertexColor;
layout (location = 1) in vec2 texCoord;
layout (location = 2) in float fogFactor;

// Output color
layout (location = 0) out vec4 fragColor;

void main() {
    // Sample the texture
    vec4 texColor = texture(textureSampler, texCoord);

    // Apply vertex color
    vec4 finalColor = texColor * vertexColor;

    if (finalColor.a < 0.01) {
        discard;
    }

    // Apply fog
    finalColor = mix(fogColor, finalColor, fogFactor);

    // Output the final color
    fragColor = finalColor;
} 
