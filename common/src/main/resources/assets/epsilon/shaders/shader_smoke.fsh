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

float random(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 st) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 30; i++) {
        if (i >= int(NoiseParams.x)) break;
        v += a * noise(st);
        st = rot * st * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

vec3 smokeColor(vec4 first, vec4 second, vec4 third) {
    vec2 st = gl_FragCoord.xy / TargetSize.xy * 3.0;
    vec2 q = vec2(fbm(st), fbm(st + vec2(1.0)));
    float time = AnimationParams.y;
    vec2 r = vec2(
        fbm(st + q + vec2(1.7, 9.2) + 0.15 * time),
        fbm(st + q + vec2(8.3, 2.8) + 0.126 * time)
    );
    float f = fbm(st + r);
    vec3 color = first.rgb;
    color = mix(color, second.rgb, clamp(length(q), 0.0, 1.0));
    color = mix(color, third.rgb, clamp(length(r.x), 0.0, 1.0));
    return (f * f * f + 0.6 * f * f + 0.5 * f) * color;
}

void main() {
    vec4 centerCol = texture(InputSampler, vUv);
    int sampleRadius = min(int(OutlineParams.x), 6);
    float lineWidth = OutlineParams.y;
    float alpha0 = OutlineParams.z;
    float fillAlpha = OutlineParams.w;
    vec2 oneTexel = TargetSize.zw;

    if (centerCol.a != 0.0) {
        fragColor = vec4(smokeColor(Fill, SmokeFill1, SmokeFill2), fillAlpha);
        return;
    }

    float alphaOutline = 0.0;
    vec3 colorFinal = vec3(0.0);
    for (int x = -sampleRadius; x < sampleRadius; x++) {
        for (int y = -sampleRadius; y < sampleRadius; y++) {
            vec4 sampleCol = texture(InputSampler, vUv + vec2(x, y) * oneTexel);
            if (sampleCol.a != 0.0) {
                if (alpha0 == -1.0) {
                    alphaOutline += Outline.a * 255.0 > 0.0 ? max(0.0, (lineWidth - distance(vec2(x, y), vec2(0.0))) / (Outline.a * 255.0)) : 1.0;
                } else {
                    fragColor = vec4(smokeColor(Outline, SmokeOutline1, SmokeOutline2), alpha0);
                    return;
                }
            }
        }
    }

    if (alphaOutline > 0.0) {
        colorFinal = smokeColor(Outline, SmokeOutline1, SmokeOutline2);
    }
    fragColor = vec4(colorFinal, alphaOutline);
}
