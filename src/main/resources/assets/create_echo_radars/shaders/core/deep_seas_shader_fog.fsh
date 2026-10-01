#version 150

uniform sampler2D DepthSampler;
uniform sampler2D WorldDepthSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D IrisDepthSampler;
uniform sampler2D IrisPreHandDepthSampler;
uniform mat4 InverseViewProjection;
uniform vec2 ScreenSize;
uniform float FogOpacity;

out vec4 fragColor;

float surfaceDistance(vec2 uv, float depth) {
    vec4 relative = InverseViewProjection
        * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return length(relative.xyz / max(abs(relative.w), 0.000001));
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float finalDepth = texture(IrisDepthSampler, uv).r;
    float preHandDepth = texture(IrisPreHandDepthSampler, uv).r;
    if (finalDepth < 0.75 && finalDepth + 0.0005 < preHandDepth) {
        // Iris draws the hand before its final composite. Its compressed
        // hand projection writes depth that was absent from depthtex2.
        discard;
    }
    float worldDepth = texture(WorldDepthSampler, uv).r;
    float sceneDepth = texture(SceneDepthSampler, uv).r;
    if (sceneDepth < 0.999999
        && (worldDepth >= 0.999999
            || surfaceDistance(uv, sceneDepth) + 0.04
                < surfaceDistance(uv, worldDepth))) {
        // A Sable construction is in front of the main-world water view.
        // The cabin, its walls and furniture are air-filled and stay clear.
        discard;
    }
    float depth = texture(DepthSampler, uv).r;
    float distanceToSurface = 1000.0;
    if (depth < 0.999999) {
        distanceToSurface = surfaceDistance(uv, depth);
    }
    float amount = clamp((distanceToSurface - 2.0) / 38.0, 0.0, 1.0);
    fragColor = vec4(0.02, 0.05, 0.2, amount * 0.97 * FogOpacity);
}
