#version 330 core

// Fog uniforms
uniform vec4 fogColor;

// Texture sampler
uniform sampler2D textureSampler;

// Input from vertex shader
in float vertexColor;
in vec2 texCoord;
in float fogFactor;

// Output color
out vec4 fragColor;

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