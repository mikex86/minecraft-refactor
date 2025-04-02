#version 120

// Input from vertex shader
varying vec4 vertexColor;
varying float fogFactor;

void main() {
    // Apply vertex color
    vec4 finalColor = vertexColor;

    if (finalColor.a < 0.01) {
        discard;
    }

    // Output the final color
    gl_FragColor = finalColor;
}