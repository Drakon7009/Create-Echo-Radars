#version 150

uniform sampler2D DepthSampler;
uniform sampler2D WorldDepthSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D IrisPostHandDepthSampler;
uniform sampler2D IrisPreHandDepthSampler;
uniform mat4 InverseViewProjection;
uniform vec2 ScreenSize;
uniform float FogOpacity;
uniform int OwnerBoundsEnabled;
uniform vec3 OwnerBoundsMin;
uniform vec3 OwnerBoundsMax;

out vec4 fragColor;

float surfaceDistance(vec2 uv, float depth) {
    vec4 relative = InverseViewProjection
        * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return length(relative.xyz / max(abs(relative.w), 0.000001));
}

vec3 surfacePosition(vec2 uv, float depth) {
    vec4 relative = InverseViewProjection
        * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return relative.xyz / max(abs(relative.w), 0.000001);
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float postHandDepth = texture(IrisPostHandDepthSampler, uv).r;
    float preHandDepth = texture(IrisPreHandDepthSampler, uv).r;
    if (postHandDepth < 0.75
        && postHandDepth + 0.0005 < preHandDepth) {
        // depthtex1 includes the hand but excludes transparent glass;
        // depthtex2 was copied before the hand was drawn.
        discard;
    }
    float worldDepth = texture(WorldDepthSampler, uv).r;
    float sceneDepth = texture(SceneDepthSampler, uv).r;
    bool sableInFront = sceneDepth < 0.999999
        && (worldDepth >= 0.999999
            || surfaceDistance(uv, sceneDepth) + 0.04
                < surfaceDistance(uv, worldDepth));
    if (sableInFront) {
        vec3 position = surfacePosition(uv, sceneDepth);
        if (OwnerBoundsEnabled == 0
            || (all(greaterThanEqual(position, OwnerBoundsMin - vec3(0.1)))
                && all(lessThanEqual(position, OwnerBoundsMax + vec3(0.1))))) {
            // Keep the player's own cabin clear. If its bounds are unavailable,
            // preserve the clear interior instead of fogging it by mistake.
            discard;
        }
    }
    float depth = sableInFront ? sceneDepth : texture(DepthSampler, uv).r;
    float distanceToSurface = 1000.0;
    if (depth < 0.999999) {
        distanceToSurface = surfaceDistance(uv, depth);
    }
    float amount = clamp((distanceToSurface - 2.0) / 38.0, 0.0, 1.0);
    fragColor = vec4(0.02, 0.05, 0.2, amount * 0.97 * FogOpacity);
}
