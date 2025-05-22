#version 330 core

// Input from vertex shader
in vec4 vertexColor;

// Output color
out vec4 fragColor;

void main() {
    // Apply vertex color
    vec4 finalColor = vertexColor;

    if (finalColor.a < 0.01) {
        discard;
    }

    // Output the final color
    fragColor = finalColor;
}