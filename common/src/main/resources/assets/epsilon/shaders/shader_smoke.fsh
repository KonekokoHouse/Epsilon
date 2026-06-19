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

float random(in vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(in vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(in vec2 st) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.50));
    for (int i = 0; i < int(NoiseParams.x); ++i) {
        v += a * noise(st);
        st = rot * st * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

vec3 getColor() {
    vec2 resolution = NoiseParams.yz;
    vec2 st = gl_FragCoord.xy / resolution.xy * 3.0;
    vec3 color = vec3(0.0);
    vec2 q = vec2(0.0);
    q.x = fbm(st);
    q.y = fbm(st + vec2(1.0));
    vec2 r = vec2(0.0);
    r.x = fbm(st + 1.0 * q + vec2(1.7, 9.2) + 0.15 * AnimationParams.y);
    r.y = fbm(st + 1.0 * q + vec2(8.3, 2.8) + 0.126 * AnimationParams.y);
    float f = fbm(st + r);
    color = Outline.rgb;
    color = mix(color, SmokeOutline1.rgb, clamp(length(q), 0.0, 1.0));
    color = mix(color, SmokeOutline2.rgb, clamp(length(r.x), 0.0, 1.0));
    vec4 outputColor = vec4((f * f * f + 0.6 * f * f + 0.5 * f) * color, Outline.a);
    return outputColor.rgb;
}

vec3 getFillColor() {
    vec2 resolution = NoiseParams.yz;
    vec2 st = gl_FragCoord.xy / resolution.xy * 3.0;
    vec3 color = vec3(0.0);
    vec2 q = vec2(0.0);
    q.x = fbm(st);
    q.y = fbm(st + vec2(1.0));
    vec2 r = vec2(0.0);
    r.x = fbm(st + 1.0 * q + vec2(1.7, 9.2) + 0.15 * AnimationParams.y);
    r.y = fbm(st + 1.0 * q + vec2(8.3, 2.8) + 0.126 * AnimationParams.y);
    float f = fbm(st + r);
    color = Fill.rgb;
    color = mix(color, SmokeFill1.rgb, clamp(length(q), 0.0, 1.0));
    color = mix(color, SmokeFill2.rgb, clamp(length(r.x), 0.0, 1.0));
    vec4 outputColor = vec4((f * f * f + 0.6 * f * f + 0.5 * f) * color, Fill.a);
    return outputColor.rgb;
}

void main() {
    vec4 centerCol = texture(InputSampler, texCoord);
    int quality = int(OutlineParams.x);
    int lineWidth = int(OutlineParams.y);
    float alpha0 = OutlineParams.z;
    float alpha1 = OutlineParams.w;
    vec2 oneTexel = TargetSize.zw;

    if (centerCol.a != 0.0) {
        fragColor = vec4(getFillColor(), alpha1);
    } else {
        float alphaOutline = 0.0;
        vec3 colorFinal = vec3(-1.0);

        for (int x = -quality; x < quality; x++) {
            for (int y = -quality; y < quality; y++) {
                vec2 offset = vec2(x, y);
                vec2 coord = texCoord + offset * oneTexel;
                vec4 sampleColor = texture(InputSampler, coord);
                if (sampleColor.a != 0.0) {
                    if (alpha0 == -1.0) {
                        alphaOutline += Outline.a * 255.0 > 0.0 ? max(0.0, (float(lineWidth) - distance(offset, vec2(0.0))) / (Outline.a * 255.0)) : 1.0;
                    } else {
                        fragColor = vec4(getColor(), alpha0);
                        return;
                    }
                }
            }
        }

        if (alphaOutline > 0.0) {
            colorFinal = getColor();
        }
        fragColor = vec4(colorFinal, alphaOutline);
    }
}
