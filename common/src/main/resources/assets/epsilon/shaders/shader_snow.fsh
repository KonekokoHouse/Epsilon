#version 410 core

uniform sampler2D InputSampler;

layout(std140) uniform ShaderConfig {
    vec4 TargetSize;
    vec4 OutlineParams;
    vec4 AnimationParams;
    vec4 NoiseParams;
    vec4 Outline;
    vec4 SmokeOutline1;
    vec4 SmokeOutline2;
    vec4 Fill;
    vec4 SmokeFill1;
    vec4 SmokeFill2;
};

in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

float snow(vec2 uv, float scale) {
    float time = AnimationParams.y;
    float w = smoothstep(1.0, 0.0, -uv.y * (scale / 10.0));
    if (w < 0.1) return 0.0;
    uv += time / scale;
    uv.y += time * 2.0 / scale;
    uv.x += sin(uv.y + time * 0.5) / scale;
    uv *= scale;
    vec2 s = floor(uv);
    vec2 f = fract(uv);
    vec2 p = 0.5 + 0.35 * sin(11.0 * fract(sin((s + vec2(scale)) * mat2(7, 3, 6, 5)) * 5.0)) - f;
    float k = min(length(p), 3.0);
    k = smoothstep(0.0, k, sin(f.x + f.y) * 0.01);
    return k * w;
}

float glowShader() {
    int sampleRadius = min(int(OutlineParams.x), 6);
    vec2 texelSize = vec2(TargetSize.z * float(sampleRadius), TargetSize.w * float(sampleRadius));
    float alpha = 0.0;
    for (int offsetX = -sampleRadius; offsetX < sampleRadius; offsetX++) {
        for (int offsetY = -sampleRadius; offsetY < sampleRadius; offsetY++) {
            vec2 sampleOffset = vec2(texelSize.x * float(offsetX), texelSize.y * float(offsetY));
            vec4 currentColor = texture(InputSampler, texCoord + sampleOffset);
            if (currentColor.a != 0.0) {
                alpha += max(0.0, (10.0 - length(vec2(offsetX, offsetY))) / 158.0);
            }
        }
    }
    return alpha;
}

void main() {
    vec4 centerCol = texture(InputSampler, texCoord);
    vec2 uv = (gl_FragCoord.xy * 2.0 - TargetSize.xy) / min(TargetSize.x, TargetSize.y);
    float c = smoothstep(1.0, 0.3, clamp(uv.y * 0.3 + 0.8, 0.0, 0.75));
    c += snow(uv, 10.0);
    c += snow(uv, 8.0);
    c += snow(uv, 6.0);
    c += snow(uv, 5.0);

    float alpha = centerCol.a != 0.0 ? Fill.a : glowShader();
    fragColor = vec4(vec3(c) * Fill.rgb, alpha);
}
