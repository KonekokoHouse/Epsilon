#version 410 core

uniform sampler2D InputSampler;

layout(std140) uniform ShaderConfig {
    vec4 Params0;
    vec4 Params1;
    vec4 Params2;
    vec4 Params3;
    vec4 Outline;
    vec4 SmokeOutline1;
    vec4 SmokeOutline2;
    vec4 Fill;
    vec4 SmokeFill1;
    vec4 SmokeFill2;
};

in vec2 vUv;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 centerCol = texture(InputSampler, vUv);
    int quality = int(Params1.x);
    float lineWidth = Params1.y;
    float alpha0 = Params1.z;
    vec2 oneTexel = Params0.zw;

    if (centerCol.a != 0.0) {
        fragColor = Fill;
        return;
    }

    float alphaOutline = 0.0;
    vec3 colorFinal = vec3(0.0);
    for (int x = -6; x < 6; x++) {
        for (int y = -6; y < 6; y++) {
            if (x < -quality || x >= quality || y < -quality || y >= quality) {
                continue;
            }
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
