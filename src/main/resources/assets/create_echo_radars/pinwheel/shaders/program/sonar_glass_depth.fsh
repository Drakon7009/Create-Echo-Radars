uniform sampler2D DepthSampler;
uniform sampler2D CutoutDepthSampler;
uniform sampler2D SceneDepthSampler;
uniform mat4 InverseViewProjection;
uniform vec3 CameraPosition;
uniform float MinRenderDistance;
uniform vec2 ScreenSize;
uniform float CycleAge;
uniform float Disconnect;

uniform vec3 CurrentOrigin;
uniform vec3 CurrentForward;
uniform vec3 CurrentRight;
uniform vec3 CurrentUp;
uniform float CurrentRange;
uniform float CurrentHorizontalSector;
uniform float CurrentVerticalSector;
uniform int CurrentType;

uniform vec3 PreviousOrigin;
uniform vec3 PreviousForward;
uniform vec3 PreviousRight;
uniform vec3 PreviousUp;
uniform float PreviousRange;
uniform float PreviousHorizontalSector;
uniform float PreviousVerticalSector;
uniform int PreviousType;

layout(location = 0) out vec4 fragColor;

const float DEG = 57.2957795131;

vec3 reconstructWorldPosition(vec2 uv, float depth) {
    vec4 relative = InverseViewProjection
        * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return CameraPosition + relative.xyz / max(abs(relative.w), 0.000001);
}

bool selectSonarSurface(vec2 uv, float opaqueDepth,
                        out vec3 worldPosition) {
    float cutoutDepth = texture(CutoutDepthSampler, uv).r;
    float sceneDepth = texture(SceneDepthSampler, uv).r;
    bool hasOpaque = opaqueDepth < 0.999999;
    bool hasCutout = cutoutDepth < 0.999999;
    bool hasScene = sceneDepth < 0.999999;

    vec3 cutoutPosition = vec3(0.0);
    float cutoutRange = 1.0e30;
    if (hasCutout) {
        cutoutPosition = reconstructWorldPosition(uv, cutoutDepth);
        cutoutRange = distance(cutoutPosition, CameraPosition);
    }

    // Geometry which is closer than the completed main-world terrain appeared
    // later in Sable's transformed sublevel pass. Project the grid onto it.
    if (hasScene) {
        vec3 scenePosition = reconstructWorldPosition(uv, sceneDepth);
        float sceneRange = distance(scenePosition, CameraPosition);
        if (!hasCutout || sceneRange + 0.04 < cutoutRange) {
            worldPosition = scenePosition;
            return true;
        }
    }

    if (!hasOpaque) {
        return false;
    }
    worldPosition = reconstructWorldPosition(uv, opaqueDepth);
    float opaqueRange = distance(worldPosition, CameraPosition);

    // A nearer surface that already existed in the main-world cutout pass is
    // vegetation, not a Sable construction.
    return !hasCutout || cutoutRange + 0.04 >= opaqueRange;
}

bool insideVolume(vec3 worldPosition, vec3 origin,
                  vec3 forwardAxis, vec3 rightAxis, vec3 upAxis,
                  float range, float horizontalSector, float verticalSector,
                  int sonarType) {
    vec3 relative = worldPosition - origin;
    float distanceToSonar = length(relative);
    if (distanceToSonar > range) {
        return false;
    }
    if (distanceToSonar < 0.00001) {
        return true;
    }

    vec3 forward = normalize(forwardAxis);
    vec3 right = normalize(rightAxis);
    vec3 up = normalize(upAxis);
    float projectedForward = dot(relative, forward);
    float projectedSide = dot(relative, right);
    float projectedUp = dot(relative, up);
    float horizontalLength = length(vec2(projectedForward, projectedSide));

    // Mechanical imaging sonar scans all azimuths around its up axis.
    if (sonarType == 1) {
        if (horizontalLength < 0.00001) {
            return false;
        }
        float elevation = abs(atan(projectedUp, horizontalLength) * DEG);
        return elevation <= verticalSector * 0.5;
    }

    // Side-scan sonar uses two downward-looking lobes.
    if (sonarType == 2) {
        const float diagonal = 0.70710678118;
        vec3 direction = relative / distanceToSonar;
        vec3 leftCenter = normalize(-right * diagonal - up * diagonal);
        vec3 rightCenter = normalize(right * diagonal - up * diagonal);
        float halfDiagonal = min(89.0,
            length(vec2(horizontalSector, verticalSector)) * 0.5);
        float threshold = cos(halfDiagonal / DEG);
        return dot(direction, leftCenter) >= threshold
            || dot(direction, rightCenter) >= threshold;
    }

    if (horizontalLength < 0.00001) {
        return false;
    }
    float yaw = atan(abs(projectedSide), projectedForward) * DEG;
    float pitch = atan(projectedUp, horizontalLength) * DEG;
    return yaw <= horizontalSector * 0.5
        && abs(pitch) <= verticalSector * 0.5;
}

float revealAt(float normalizedDistance) {
    float progress = clamp(CycleAge, 0.0, 80.0) / 80.0;
    float front = smoothstep(0.0, 1.0, progress);
    if (front >= 1.0) {
        return 1.0;
    }
    return 1.0 - smoothstep(front, min(1.0, front + 0.08),
                            normalizedDistance);
}

vec2 surfaceCoordinates(vec3 worldPosition, vec3 normal) {
    vec3 axis = abs(normal);
    if (axis.x >= axis.y && axis.x >= axis.z) {
        return vec2(worldPosition.z, worldPosition.y);
    }
    if (axis.y >= axis.z) {
        return vec2(worldPosition.x, worldPosition.z);
    }
    return vec2(worldPosition.x, worldPosition.y);
}

float periodicLine(float coordinate, float halfWidth) {
    float distanceToLine = abs(fract(coordinate) - 0.5);
    float antiAlias = max(fwidth(coordinate) * 0.85, 0.0005);
    return 1.0 - smoothstep(halfWidth, halfWidth + antiAlias, distanceToLine);
}

vec2 warpGrid(vec2 position) {
    // Continuous periodic domain warping keeps the mesh fixed in world space,
    // while avoiding the appearance of a perfectly uniform triangle mask.
    vec2 first = vec2(
        sin(position.y * 2.17 + sin(position.x * 0.71)),
        sin(position.x * 1.83 - sin(position.y * 0.63))
    );
    vec2 second = vec2(
        sin((position.x + position.y) * 0.91),
        sin((position.x - position.y) * 1.07)
    );
    return position + first * 0.105 + second * 0.045;
}

vec2 triangleMesh(vec2 worldUv) {
    // About two blocks per main cell: visibly larger triangles than the old
    // per-block CPU mesh. All inputs are world coordinates, so adjacent blocks
    // share exactly the same vertices and the pattern never swims with camera.
    vec2 p = warpGrid(worldUv / 1.85);
    float familyA = p.x;
    float familyB = dot(p, vec2(0.5, 0.86602540378));
    float familyC = dot(p, vec2(-0.5, 0.86602540378));

    float core = max(periodicLine(familyA, 0.012),
                 max(periodicLine(familyB, 0.012),
                     periodicLine(familyC, 0.012)));
    float glow = max(periodicLine(familyA, 0.052),
                 max(periodicLine(familyB, 0.052),
                     periodicLine(familyC, 0.052)));
    return vec2(core, glow);
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = texture(DepthSampler, uv).r;
    vec3 worldPosition;
    if (!selectSonarSurface(uv, depth, worldPosition)) discard;
    if (distance(worldPosition, CameraPosition) < MinRenderDistance) {
        discard;
    }
    bool inCurrent = insideVolume(worldPosition, CurrentOrigin,
        CurrentForward, CurrentRight, CurrentUp, CurrentRange,
        CurrentHorizontalSector, CurrentVerticalSector, CurrentType);
    bool inPrevious = insideVolume(worldPosition, PreviousOrigin,
        PreviousForward, PreviousRight, PreviousUp, PreviousRange,
        PreviousHorizontalSector, PreviousVerticalSector, PreviousType);
    if (!inCurrent && !inPrevious) {
        discard;
    }

    float currentDistance = clamp(
        length(worldPosition - CurrentOrigin) / max(CurrentRange, 0.0001),
        0.0, 1.0);
    float reveal = revealAt(currentDistance);
    float newAlpha = inCurrent ? reveal : 0.0;
    float oldDim = 1.0 - 0.7 * clamp(CycleAge / 10.0, 0.0, 1.0);
    float oldAlpha = inPrevious ? oldDim * (1.0 - reveal) : 0.0;
    float scanAlpha = max(newAlpha, oldAlpha) * Disconnect;
    if (scanAlpha < 0.003) {
        discard;
    }

    vec3 dx = dFdx(worldPosition);
    vec3 dy = dFdy(worldPosition);
    vec3 normal = normalize(cross(dx, dy));
    vec2 mesh = triangleMesh(surfaceCoordinates(worldPosition, normal));
    float lineAlpha = min(1.0, mesh.x * 0.92 + mesh.y * 0.24);
    float distanceFade = mix(1.0, 0.62, currentDistance);
    float alpha = lineAlpha * scanAlpha * distanceFade;
    if (alpha < 0.003) {
        discard;
    }

    vec3 glowColor = vec3(1.0, 0.075, 0.055);
    vec3 coreColor = vec3(1.0, 0.30, 0.24);
    vec3 color = mix(glowColor, coreColor, mesh.x);
    fragColor = vec4(color, alpha);
}
