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

float random(vec2 pos) {
    return fract(sin(dot(pos.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(vec2 pos) {
    vec2 i = floor(pos);
    vec2 f = fract(pos);
    float a = random(i + vec2(0.0, 0.0));
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 pos) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < int(NoiseParams.x); i++) {
        v += a * noise(pos);
        a *= 0.5;
    }
    return v;
}

vec3 getColor() {
    vec2 resolution = NoiseParams.yz;
    vec2 p = ((vec2(2.0) * gl_FragCoord.xy) - resolution.xy) * vec2(AnimationParams.w / min(resolution.x, resolution.y));
    float time2 = 3.0 * AnimationParams.y / 2.0;
    vec2 q = vec2(0.0);
    q.x = fbm(p + 0.00);
    q.y = fbm(p + vec2(1.0));

    vec4 temp = vec4(
            vec3(
                    noise(p + vec2(1.0)),
                    noise(p + AnimationParams.z * q + vec2(1.7, 9.2) + 0.15 * time2),
                    noise(p + AnimationParams.z * q + vec2(8.3, 2.8) + 0.126 * time2)
            ),
            AnimationParams.x
    );
    return temp.rgb;
}

float alphaMask(float alpha) {
    return step(1.0e-6, alpha);
}

float modeMask(float value, float target) {
    return 1.0 - step(1.0e-4, abs(value - target));
}

float outlineFalloff(vec2 offset, float lineWidth, float alpha) {
    float alphaScale = alpha * 255.0;
    float faded = max(0.0, (lineWidth - length(offset)) / max(alphaScale, 1.0e-6));
    return mix(1.0, faded, step(1.0e-6, alphaScale));
}

void main() {
    vec4 centerCol = texture(InputSampler, texCoord);
    int quality = int(OutlineParams.x);
    int lineWidth = int(OutlineParams.y);
    float alpha0 = OutlineParams.z;
    float alpha1 = OutlineParams.w;
    float alpha2 = AnimationParams.x;
    vec2 oneTexel = TargetSize.zw;
    float softMode = modeMask(alpha0, -1.0);

    if (centerCol.a != 0.0) {
        fragColor = vec4(getColor(), alpha2);
    } else {
        float alphaOutline = 0.0;
        float outlineHit = 0.0;

        for (int x = -quality; x < quality; x++) {
            for (int y = -quality; y < quality; y++) {
                vec2 offset = vec2(x, y);
                vec2 coord = texCoord + offset * oneTexel;
                float sampleHit = alphaMask(texture(InputSampler, coord).a);
                outlineHit += sampleHit;
                alphaOutline += sampleHit * softMode * outlineFalloff(offset, float(lineWidth), alpha1);
            }
        }

        float hitMask = alphaMask(outlineHit);
        float hardMode = 1.0 - softMode;
        float finalAlpha = alphaOutline + hardMode * alpha0 * hitMask;

        if (outlineHit != 0.0) {
            fragColor = vec4(getColor(), finalAlpha);
        } else {
            fragColor = vec4(vec3(-1.0), 0.0);
        }
    }
}
