package org.rassvet.create_echo_radars.client;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Structural adapters for loaded (include-expanded) shader sources. No pack files are edited. */
public final class IrisWaterFogPatcher {
    private IrisWaterFogPatcher() {}
    public record Result(String source, String family) {
        public boolean patched() { return family != null; }
    }

    public static Result patch(String name, String original) {
        if (!name.startsWith("composite") || original.contains("CER_NATIVE_WATER_FOG"))
            return new Result(original, null);
        String s = original.replace("\r\n", "\n");
        String family = null;
        if (s.contains("GetWaterFog(viewPos.xyz") && s.contains("sqrt(waterFog.rgb)")) {
            family = "BSL";
            s = s.replace("if (isEyeInWater == 1.0)", "if (cerEye(isEyeInWater) == 1)");
            s = function(s, "GetWaterFog", body -> body
                    .replace("length(viewPos)", "cerWaterDistance(length(viewPos))")
                    .replace("isEyeInWater", "cerEye(isEyeInWater)")
                    .replaceAll("\\bfogColor\\b", "cerBiomeFog(fogColor)")
                    .replaceAll("\\beBS\\b", "cerSky(eBS)"));
        } else if (s.contains("switch (isEyeInWater)") && s.contains("raymarch_water_fog(")) {
            family = "Photon volumetric";
            s = s.replace("switch (isEyeInWater)", "switch (cerEye(isEyeInWater))");
            s = s.replaceAll("raymarch_water_fog\\(\\s*world_start_pos\\s*,\\s*world_end_pos\\s*,",
                    "raymarch_water_fog(cerWaterStart(world_start_pos), cerWaterEnd(world_end_pos),");
            s = function(s, "raymarch_water_fog", body -> body.replaceAll(
                    "\\beye_skylight\\b", "cerSky(eye_skylight)"));
        } else if (s.contains("mat2x3 analytic_fog = water_fog_simple(")) {
            family = "Photon analytic";
            s = s.replace("if (isEyeInWater == 1)", "if (cerEye(isEyeInWater) == 1)");
            int start = s.indexOf("mat2x3 analytic_fog = water_fog_simple(");
            int end = s.indexOf(");", start);
            String call = s.substring(start, end + 2)
                    .replace("view_distance", "cerWaterDistance(view_distance)")
                    .replace("eye_skylight", "cerSky(eye_skylight)");
            s = s.substring(0, start) + call + s.substring(end + 2);
        } else if (s.contains("vec3 underwaterMult = vec3(0.80, 0.87, 0.97)")
                && s.contains("float GetWaterFog(")) {
            family = "Complementary";
            s = s.replace("if (isEyeInWater == 1) {", "if (cerEye(isEyeInWater) == 1) {\n"
                    + "        if (cerVirtualWater()) color.rgb = mix(color.rgb, waterFogColor, GetWaterFog(cerWaterDistance(lViewPos1)));\n");
            s = s.replace("pow(fogColor, vec3(0.33, 0.21, 0.26))",
                    "pow(cerBiomeFog(fogColor), vec3(0.33, 0.21, 0.26))");
        } else if (s.contains("waterVolumetrics(vl, vec3(0.0), viewPos0")
                && s.contains("totEpsilon")) {
            family = "Bliss volumetric";
            s = s.replace("if (isEyeInWater == 1)", "if (cerEye(isEyeInWater) == 1)");
            s = s.replace("waterVolumetrics(vl, vec3(0.0), viewPos0",
                    "waterVolumetrics(vl, cerViewStart(vec3(0.0)), cerViewEnd(viewPos0)");
            s = s.replace("length(viewPos0), noise_1, totEpsilon",
                    "cerWaterDistance(length(viewPos0)), noise_1, totEpsilon");
        } else if (s.contains("vec3 absorbColor = exp(-totEpsilon*linearDistance)")
                && s.contains("thresholdAbsorbedColor")) {
            family = "Bliss absorption";
            s = s.replace("if (isEyeInWater == 1)", "if (cerEye(isEyeInWater) == 1)");
            s = s.replace("exp(-totEpsilon*linearDistance)",
                    "exp(-totEpsilon*cerWaterDistance(linearDistance))");
            s = s.replace("float fogfade =  exp(-0.001*(linearDistance*linearDistance));",
                    "float cerDistance = cerWaterDistance(linearDistance);\n"
                    + "      float fogfade = exp(-0.001*cerDistance*cerDistance);");
        }
        if (family == null || s.equals(original)) return new Result(original, null);
        // Standard Iris depth samplers are allocated by Iris, including in reduced-resolution passes.
        String declarations = DECLARATIONS;
        if (!Pattern.compile("uniform\\s+sampler2D\\s+depthtex1\\s*;").matcher(s).find())
            declarations += "uniform sampler2D depthtex1;\n";
        if (!Pattern.compile("uniform\\s+sampler2D\\s+depthtex2\\s*;").matcher(s).find())
            declarations += "uniform sampler2D depthtex2;\n";
        Matcher version = Pattern.compile("(?m)^\\s*#version[^\\n]*\\n").matcher(s);
        int insertion = version.find() ? version.end() : 0;
        s = s.substring(0, insertion) + declarations + s.substring(insertion) + FUNCTIONS;
        return new Result(s, family);
    }

    private static String function(String s, String name, java.util.function.UnaryOperator<String> edit) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*\\([^;{}]*\\)\\s*\\{").matcher(s);
        if (!m.find()) return s;
        int begin = m.end(), end = begin, depth = 1;
        for (; end < s.length() && depth > 0; end++) {
            if (s.charAt(end) == '{') depth++;
            else if (s.charAt(end) == '}') depth--;
        }
        if (depth != 0) return s;
        return s.substring(0, begin) + edit.apply(s.substring(begin, end - 1)) + s.substring(end - 1);
    }

    private static final String DECLARATIONS = """
        // CER_NATIVE_WATER_FOG
        uniform int cerSubmarine;
        uniform mat4 cerInverseVP;
        uniform mat4 cerView;
        uniform vec3 cerCamera;
        uniform vec3 cerBoundsMin;
        uniform vec3 cerBoundsMax;
        uniform vec3 cerWaterBiomeColor;
        uniform float cerExteriorSky;
        uniform vec2 cerViewport;
        bool cerVirtualWater();
        int cerEye(int actual);
        float cerWaterDistance(float actual);
        float cerSky(float actual);
        vec3 cerBiomeFog(vec3 actual);
        vec3 cerWaterStart(vec3 actual);
        vec3 cerWaterEnd(vec3 actual);
        vec3 cerViewStart(vec3 actual);
        vec3 cerViewEnd(vec3 actual);
        """;

    private static final String FUNCTIONS = """
        vec2 cerUV() { return gl_FragCoord.xy / max(cerViewport, vec2(1.0)); }
        vec3 cerPosition(float depth) {
            vec4 p = cerInverseVP * vec4(cerUV() * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
            return p.xyz / max(abs(p.w), 0.000001);
        }
        bool cerVirtualWater() {
            if (cerSubmarine == 0) return false;
            float d1 = texture2D(depthtex1, cerUV()).r;
            float d2 = texture2D(depthtex2, cerUV()).r;
            if (d1 < 0.75 && d1 + 0.0005 < d2) return false;
            vec3 p = cerPosition(d2);
            if (d2 < 0.999999 && all(greaterThanEqual(p, cerBoundsMin - vec3(0.1)))
                && all(lessThanEqual(p, cerBoundsMax + vec3(0.1)))) return false;
            return true;
        }
        vec3 cerDirection() { return normalize(cerPosition(0.99999)); }
        float cerExit() {
            vec3 ray = cerDirection();
            vec3 invRay = 1.0 / (sign(ray + vec3(0.0000001)) * max(abs(ray), vec3(0.000001)));
            vec3 a = cerBoundsMin * invRay, b = cerBoundsMax * invRay;
            vec3 exits = max(a, b);
            return max(0.0, min(exits.x, min(exits.y, exits.z)));
        }
        int cerEye(int actual) { return actual == 0 && cerVirtualWater() ? 1 : actual; }
        float cerWaterDistance(float actual) {
            if (!cerVirtualWater()) return actual;
            float d = texture2D(depthtex2, cerUV()).r;
            return max(0.0, (d >= 0.999999 ? 128.0 : length(cerPosition(d))) - cerExit());
        }
        float cerSky(float actual) { return cerSubmarine != 0 ? cerExteriorSky : actual; }
        vec3 cerBiomeFog(vec3 actual) { return cerSubmarine != 0 ? cerWaterBiomeColor : actual; }
        vec3 cerWaterStart(vec3 actual) {
            return cerVirtualWater() ? cerCamera + cerDirection() * cerExit() : actual;
        }
        vec3 cerWaterEnd(vec3 actual) {
            return cerVirtualWater() ? cerCamera + cerDirection() * (cerExit() + cerWaterDistance(0.0)) : actual;
        }
        vec3 cerViewStart(vec3 actual) {
            return cerVirtualWater() ? (cerView * vec4(cerDirection() * cerExit(), 1.0)).xyz : actual;
        }
        vec3 cerViewEnd(vec3 actual) {
            return cerVirtualWater() ? (cerView * vec4(cerDirection() * (cerExit() + cerWaterDistance(0.0)), 1.0)).xyz : actual;
        }
        """;
}
