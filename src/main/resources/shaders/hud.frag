#version 120

// Texture sampler
uniform sampler2D texture;

// Input from vertex shader
varying vec4 vertexColor;
varying vec2 texCoord;
varying float fogFactor;

void main() {
    // Sample the texture
    vec4 texColor = texture2D(texture, texCoord);

    // Apply vertex color
    vec4 finalColor = texColor * vertexColor;

    if (finalColor.a < 0.01) {
        discard;
    }

    // Output the final color
    gl_FragColor = finalColor;
}