#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Keep the aperture just in front of the translucent glass depth so
    // sub-pixel precision changes while moving cannot make the grid flicker.
    gl_Position.z -= 0.0005 * gl_Position.w;
}
