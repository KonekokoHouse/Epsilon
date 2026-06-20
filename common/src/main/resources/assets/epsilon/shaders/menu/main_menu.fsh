#version 410 core

layout(std140) uniform GlslSandboxInfo {
    vec4 SandboxResolutionTime;
    vec4 SandboxMouse;
};

layout(location = 0) out vec4 fragColor;

#define resolution SandboxResolutionTime.xy
#define time SandboxResolutionTime.z

void mainImage(out vec4 o, vec2 u) {
    float s = 0.3, i = 0.0, n;
    vec3 r = vec3(resolution, 1.0), p = vec3(0.0);
    for (u = (u - resolution / 2.0) / resolution.y - s; i++ < 32.0 && ++s > 0.001;) {
        for (p += vec3(u * s, s), s = p.y, n = 0.01; n < 1.0; n += n) {
            s += abs(dot(sin(p.z + time + p / n), r / r)) * n * 0.1;
        }
    }
    o = tanh(i * vec4(5.0, 2.0, 1.0, 0.0) / length(u - 0.1) / 5e2);
    o.a = 1.0;
}

void main() {
    mainImage(fragColor, gl_FragCoord.xy);
}
