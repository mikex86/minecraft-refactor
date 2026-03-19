#version 450 core

// Fog uniforms
layout (location = 11) uniform vec4 fogColor;

// Texture sampler
uniform sampler2D textureSampler;

// Input from vertex shader
layout (location = 0) in float vertexColor;
layout (location = 1) in vec2 texCoord;
layout (location = 2) in float fogFactor;

// Output color
layout (location = 0) out vec4 fragColor;

void main() {
    // Sample the texture
    vec4 texColor = texture(textureSampler, texCoord);
    
    // Apply vertex color
    vec4 finalColor = texColor * vec4(vertexColor, vertexColor, vertexColor, 1.0);

    if (finalColor.a < 0.01) {
        discard;
    }

    finalColor = mix(fogColor, finalColor, fogFactor);

    // Output the final color
    fragColor = finalColor;
} 
