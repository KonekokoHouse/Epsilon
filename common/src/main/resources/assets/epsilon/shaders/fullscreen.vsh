#version 410 core

out vec2 vUv;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vec2 position = uv * 2.0 - 1.0;

    gl_Position = vec4(position, 0.0, 1.0);
    vUv = uv;
}
