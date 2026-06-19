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
    float w = smoothstep(1.0, 0.0, -uv.y * (scale / 10.0));
    if (w < 0.1) return 0.0;
    uv += AnimationParams.y / scale;
    uv.y += AnimationParams.y * 2.0 / scale;
    uv.x += sin(uv.y + AnimationParams.y * 0.5) / scale;
    uv *= scale;
    vec2 s = floor(uv);
    vec2 f = fract(uv);
    vec2 p;
    float k = 3.0;
    float d;
    p = 0.5 + 0.35 * sin(11.0 * fract(sin((s + p + scale) * mat2(7, 3, 6, 5)) * 5.0)) - f;
    d = length(p);
    k = min(d, k);
    k = smoothstep(0.0, k, sin(f.x + f.y) * 0.01);
    return k * w;
}

float glowShader() {
    float divider = 158.0;
    float maxSample = 10.0;
    vec2 resolution = NoiseParams.yz;
    float quality = OutlineParams.x;
    vec2 texelSize = vec2(1.0 / resolution.x * quality, 1.0 / resolution.y * quality);
    float alpha = 0.0;

    for (float x = -quality; x < quality; x++) {
        for (float y = -quality; y < quality; y++) {
            vec4 currentColor = texture(InputSampler, texCoord + vec2(texelSize.x * x, texelSize.y * y));
            if (currentColor.a != 0.0) {
                alpha += divider > 0.0 ? max(0.0, (maxSample - distance(vec2(x, y), vec2(0.0))) / divider) : 1.0;
            }
        }
    }

    return alpha;
}

void main() {
    vec4 centerCol = texture(InputSampler, texCoord);
    vec2 resolution = NoiseParams.yz;
    vec2 uv = (gl_FragCoord.xy * 2.0 - resolution.xy) / min(resolution.x, resolution.y);
    vec3 finalColor = vec3(0.0);
    float c = smoothstep(1.0, 0.3, clamp(uv.y * 0.3 + 0.8, 0.0, 0.75));
    c += snow(uv, 30.0) * 0.0;
    c += snow(uv, 20.0) * 0.0;
    c += snow(uv, 15.0) * 0.0;
    c += snow(uv, 10.0);
    c += snow(uv, 8.0);
    c += snow(uv, 6.0);
    c += snow(uv, 5.0);
    finalColor = vec3(c) * Fill.rgb;

    float alpha = centerCol.a != 0.0 ? Fill.a : glowShader();
    fragColor = vec4(finalColor, alpha);
}
