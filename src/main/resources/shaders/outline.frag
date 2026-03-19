#version 450 core

// Input from vertex shader
layout (location = 0) in vec4 vertexColor;

// Output color
layout (location = 0) out vec4 fragColor;

void main() {
    // Apply vertex color
    vec4 finalColor = vertexColor;

    if (finalColor.a < 0.01) {
        discard;
    }

    // Output the final color
    fragColor = finalColor;
}
