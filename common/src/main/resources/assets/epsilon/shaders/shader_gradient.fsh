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

in vec2 vUv;

layout(location = 0) out vec4 fragColor;

float random(vec2 pos) {
    return fract(sin(dot(pos.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(vec2 pos) {
    vec2 i = floor(pos);
    vec2 f = fract(pos);
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 pos) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(cos(0.1), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 30; i++) {
        if (i >= int(NoiseParams.x)) break;
        v += a * noise(pos);
        pos = rot * pos * 2.0;
        a *= 0.5;
    }
    return v;
}

vec3 gradientColor() {
    vec2 resolution = TargetSize.xy;
    float time = AnimationParams.y;
    float factor = AnimationParams.z;
    float moreGradient = AnimationParams.w;
    vec2 p = (((vec2(2.0) * gl_FragCoord.xy) - resolution.xy) * vec2(moreGradient / min(resolution.x, resolution.y)));
    float time2 = 1.5 * time;
    vec2 q = vec2(fbm(p), fbm(p + vec2(1.0)));
    return vec3(
        noise(p + vec2(1.0)),
        noise(p + factor * q + vec2(1.7, 9.2) + 0.15 * time2),
        noise(p + factor * q + vec2(8.3, 2.8) + 0.126 * time2)
    );
}

void main() {
    vec4 centerCol = texture(InputSampler, vUv);
    int sampleRadius = min(int(OutlineParams.x), 6);
    float lineWidth = OutlineParams.y;
    float alpha0 = OutlineParams.z;
    float fillAlpha = OutlineParams.w;
    float alpha2 = AnimationParams.x;
    vec2 oneTexel = TargetSize.zw;

    if (centerCol.a != 0.0) {
        fragColor = vec4(gradientColor(), alpha2);
        return;
    }

    float alphaOutline = 0.0;
    vec3 colorFinal = vec3(0.0);
    for (int x = -sampleRadius; x < sampleRadius; x++) {
        for (int y = -sampleRadius; y < sampleRadius; y++) {
            vec4 sampleCol = texture(InputSampler, vUv + vec2(x, y) * oneTexel);
            if (sampleCol.a != 0.0) {
                if (alpha0 == -1.0) {
                    alphaOutline += fillAlpha * 255.0 > 0.0 ? max(0.0, (lineWidth - distance(vec2(x, y), vec2(0.0))) / (fillAlpha * 255.0)) : 1.0;
                } else {
                    fragColor = vec4(gradientColor(), alpha0);
                    return;
                }
            }
        }
    }

    if (alphaOutline > 0.0) {
        colorFinal = gradientColor();
    }
    fragColor = vec4(colorFinal, alphaOutline);
}
