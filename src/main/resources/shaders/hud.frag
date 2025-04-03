#version 330 core

// Texture sampler
uniform sampler2D textureSampler;

// Input from vertex shader
in vec4 vertexColor;
in vec2 texCoord;

// Output color
out vec4 fragColor;

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