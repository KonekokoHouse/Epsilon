#version 410 core

in vec2 f_Position;
in vec4 f_Color;
in vec4 f_InnerRect;
in vec4 f_Radius;
in float f_BlurRadius;

layout(location = 0) out vec4 fragColor;

const float TAU = 6.28318530718;
const int DIRECTION_COUNT = 16;
const int SAMPLE_COUNT = 5;
const float SAMPLE_WEIGHT = 1.0 / 81.0;

float roundedRectDistance(vec2 position) {
    vec2 halfSize = (f_InnerRect.zw - f_InnerRect.xy) * 0.5;
    vec2 center = (f_InnerRect.xy + f_InnerRect.zw) * 0.5;
    vec2 p = position - center;
    vec2 side = step(0.0, p);

    float radius = mix(
        mix(f_Radius.x, f_Radius.w, side.y),
        mix(f_Radius.y, f_Radius.z, side.y),
        side.x
    );

    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float maskAt(vec2 position, float antialias) {
    float distance = roundedRectDistance(position);
    return 1.0 - smoothstep(0.0, antialias, distance);
}

void main() {
    vec2 positionWidth = fwidth(f_Position);
    float antialias = max(max(positionWidth.x, positionWidth.y) * 2.0, 0.0001);
    float baseDistance = roundedRectDistance(f_Position);
    float originalMask = maskAt(f_Position, antialias);
    float blurredMask = originalMask;

    for (int directionIndex = 0; directionIndex < DIRECTION_COUNT; directionIndex++) {
        float angle = TAU * float(directionIndex) / float(DIRECTION_COUNT);
        vec2 direction = vec2(cos(angle), sin(angle));

        for (int sampleIndex = 1; sampleIndex <= SAMPLE_COUNT; sampleIndex++) {
            float sampleDistance = float(sampleIndex) / float(SAMPLE_COUNT);
            blurredMask += maskAt(f_Position + direction * f_BlurRadius * sampleDistance, antialias);
        }
    }

    blurredMask *= SAMPLE_WEIGHT;
    float outsideMask = smoothstep(-antialias, 0.0, baseDistance);
    float outsideAlpha = clamp(blurredMask * outsideMask, 0.0, 1.0);
    float alpha = f_Color.a * outsideAlpha;
    if (alpha < 0.001) discard;

    fragColor = vec4(f_Color.rgb, alpha);
}
