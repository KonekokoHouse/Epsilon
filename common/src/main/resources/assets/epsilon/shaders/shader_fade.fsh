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

vec3 wave(vec2 pos) {
    return mix(Fill.rgb, SmokeFill1.rgb, sin((distance(vec2(0.0), pos) - AnimationParams.y * 60.0) / 60.0) * 0.5 + 0.5);
}

void main() {
    vec4 centerCol = texture(InputSampler, vUv);
    int sampleRadius = min(int(OutlineParams.x), 6);
    float lineWidth = OutlineParams.y;
    float alpha0 = OutlineParams.z;
    float fillAlpha = OutlineParams.w;
    vec2 oneTexel = TargetSize.zw;

    if (centerCol.a != 0.0) {
        fragColor = vec4(wave(gl_FragCoord.xy), fillAlpha);
        return;
    }

    float alphaOutline = 0.0;
    vec3 colorFinal = vec3(0.0);
    for (int x = -sampleRadius; x < sampleRadius; x++) {
        for (int y = -sampleRadius; y < sampleRadius; y++) {
            vec4 sampleCol = texture(InputSampler, vUv + vec2(x, y) * oneTexel);
            if (sampleCol.a != 0.0) {
                if (alpha0 == -1.0) {
                    colorFinal = Outline.rgb;
                    alphaOutline += Outline.a * 255.0 > 0.0 ? max(0.0, (lineWidth - distance(vec2(x, y), vec2(0.0))) / (Outline.a * 255.0)) : 1.0;
                } else {
                    fragColor = vec4(Outline.rgb, alpha0);
                    return;
                }
            }
        }
    }

    fragColor = vec4(colorFinal, alphaOutline);
}
