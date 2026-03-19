#version 450 core

// Texture sampler
layout (binding = 1) uniform sampler2D textureSampler;

// Input from vertex shader
layout (location = 0) in vec4 vertexColor;
layout (location = 1) in vec2 texCoord;

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

    // Output the final color
    fragColor = finalColor;
}
