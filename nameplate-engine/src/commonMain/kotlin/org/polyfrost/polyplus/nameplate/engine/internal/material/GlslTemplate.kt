package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.nameplate.engine.api.shader.FlatUniform
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslDialect
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslGlobals

internal object GlslTemplate {
    const val INCLUDE = "polyplus:nameplate.glsl"
    const val INPUT_PREFIX = "NameplateInputs"
    const val ATLAS_PREFIX = "NameplateAtlas"
    const val TONE_PREFIX = "NameplateTone"
    val CLOCK_NAMES: List<String> = listOf("NameplateFrame", "NameplateCycle", "NameplateFps", "NameplateStep")

    private const val MAX_WIDTH = 4

    private val COMPONENTS = listOf("x", "y", "z", "w")

    private val SCALAR_HELPERS = """
        float nDiv(float a, float b) { return b == 0.0 ? 0.0 : a / b; }
        float nMod(float a, float b) { return b == 0.0 ? 0.0 : a - b * floor(a / b); }
        float nPow(float a, float b) { return a > 0.0 ? pow(a, b) : 0.0; }
        float nAtan2(float y, float x) { return x == 0.0 && y == 0.0 ? 0.0 : atan(y, x); }
        float nClamp(float x, float lo, float hi) { return min(max(x, lo), hi); }
        float nMix(float a, float b, float t) { return a * (1.0 - t) + b * t; }
        float nSmooth(float e0, float e1, float x) {
            float t = nClamp(nDiv(x - e0, e1 - e0), 0.0, 1.0);
            return t * t * (3.0 - 2.0 * t);
        }
    """.trimIndent()

    private val LIBRARY = """
        const int N_BAYER[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);

        int nWrap(int x, int p) {
            if (p <= 0) {
                return x;
            }
            int q = x >= 0 ? x / p : -((-x - 1) / p) - 1;
            return x - p * q;
        }

        float nHash(ivec2 p, int seed) {
            uint h = uint(p.x) * 0x27D4EB2Du ^ uint(p.y) * 0x165667B1u ^ uint(seed) * 0x5BD1E995u;
            h ^= h >> 15u;
            h *= 0x2C1B3C6Du;
            h ^= h >> 12u;
            h *= 0x297A2D39u;
            h ^= h >> 15u;
            return float(h >> 8u) / 16777216.0;
        }

        float nHashAt(vec2 p, int seed) {
            return nHash(ivec2(floor(p)), seed);
        }

        float nNoise(vec2 p, ivec2 period, int seed) {
            vec2 cell = floor(p);
            vec2 f = p - cell;
            f = f * f * (3.0 - 2.0 * f);
            int x0 = nWrap(int(cell.x), period.x);
            int x1 = nWrap(int(cell.x) + 1, period.x);
            int y0 = nWrap(int(cell.y), period.y);
            int y1 = nWrap(int(cell.y) + 1, period.y);
            float top = nMix(nHash(ivec2(x0, y0), seed), nHash(ivec2(x1, y0), seed), f.x);
            float bottom = nMix(nHash(ivec2(x0, y1), seed), nHash(ivec2(x1, y1), seed), f.x);
            return nMix(top, bottom, f.y);
        }

        float nFbm(vec2 p, int octaves, ivec2 period, int seed) {
            float sum = 0.0;
            float weight = 1.0;
            float total = 0.0;
            for (int i = 0; i < octaves; i++) {
                sum += nNoise(p, period, seed + i) * weight;
                total += weight;
                p *= 2.0;
                period *= 2;
                weight *= 0.5;
            }
            return sum / total;
        }

        float nBayer(vec2 p) {
            ivec2 c = ivec2(floor(p));
            return float(N_BAYER[nWrap(c.y, 4) * 4 + nWrap(c.x, 4)]) / 16.0;
        }

        float nChecker(vec2 p, int size) {
            return float(nWrap(int(floor(p.x / float(size))) + int(floor(p.y / float(size))), 2));
        }

        float nStripes(vec2 p, int period, int width, ivec2 d) {
            int t = nWrap(int(floor(p.x)) * d.x + int(floor(p.y)) * d.y, period);
            return t < width ? 1.0 : 0.0;
        }

        float nGradient(vec2 p, vec2 a, vec2 b) {
            vec2 d = b - a;
            return nClamp(nDiv(dot(p - a, d), dot(d, d)), 0.0, 1.0);
        }

        float nBodyEdge(vec2 p, vec2 size, vec4 radii) {
            vec2 c = p - 0.5 * size;
            float r = c.x < 0.0 ? (c.y < 0.0 ? radii.x : radii.w) : (c.y < 0.0 ? radii.y : radii.z);
            vec2 q = abs(c) - 0.5 * size + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - r;
        }

        vec4 nTone(int ref) {
            return ref < 0 ? vec4(0.0) : Tones[ref];
        }

        int nQuantize(float level, int base, int count) {
            return base + clamp(int(floor(level * float(count - 1) + 0.5)), 0, count - 1);
        }

        int nDither(float level, vec2 p, int base, int count) {
            return base + clamp(int(floor(level * float(count - 1) + nBayer(p))), 0, count - 1);
        }

        int nCycle(float index, float shift, int base, int count) {
            return base + nWrap(int(floor(index)) + int(floor(shift)), count);
        }

        vec4 nHsv(float h, float s, float v) {
            float h6 = (h - floor(h)) * 6.0;
            float sector = floor(h6);
            float f = h6 - sector;
            float p = v * (1.0 - s);
            float q = v * (1.0 - s * f);
            float t = v * (1.0 - s * (1.0 - f));
            int i = nWrap(int(sector), 6);
            vec3 c = i == 0 ? vec3(v, t, p) : i == 1 ? vec3(q, v, p) : i == 2 ? vec3(p, v, t)
                : i == 3 ? vec3(p, q, v) : i == 4 ? vec3(t, p, v) : vec3(v, p, q);
            return vec4(c, 1.0);
        }

        vec4 nLighten(vec4 c, float a) {
            return vec4(c.rgb + (1.0 - c.rgb) * a, c.a);
        }

        vec4 nDarken(vec4 c, float a) {
            return vec4(c.rgb * (1.0 - a), c.a);
        }

        vec4 nAlpha(vec4 c, float a) {
            return vec4(c.rgb, c.a * a);
        }

        vec2 nPolar(vec2 v) {
            return vec2(length(v), nAtan2(v.y, v.x));
        }

        vec4 nSample(sampler2D s, vec2 uv) {
            ivec2 size = textureSize(s, 0);
            ivec2 t = ivec2(floor(uv * vec2(size)));
            return texelFetch(s, ivec2(nWrap(t.x, size.x), nWrap(t.y, size.y)), 0);
        }

        vec4 nAtlas(sampler2D s, vec4 rect, vec2 uv) {
            ivec2 size = ivec2(rect.zw);
            ivec2 t = ivec2(floor(uv * rect.zw));
            return texelFetch(s, ivec2(rect.xy) + ivec2(nWrap(t.x, size.x), nWrap(t.y, size.y)), 0);
        }

        vec2 nTagUv(vec2 p) {
            return (p - AreaRect.xy) / (AreaRect.zw - AreaRect.xy);
        }

        float nSeconds(float frame) {
            return frame * float(NameplateClock.w) / float(NameplateClock.z);
        }

        mat2 nScreenJ;

        vec2 nScreen(vec2 p) {
            return (gl_FragCoord.xy + nScreenJ * (p - pixelPos)) / ScreenSize;
        }

        struct MaterialInputs {
            vec2 pos;
            float frame;
        };

        vec4 material(MaterialInputs m);
    """.trimIndent()

    private val MAIN = """
        const float NAMEPLATE_MIN_ALPHA = 0.5 / 255.0;

        bool nCovered(vec2 pos, float frame) {
            return material(MaterialInputs(pos, frame)).a >= NAMEPLATE_MIN_ALPHA;
        }

        void main() {
            nScreenJ = inverse(mat2(dFdx(pixelPos), dFdy(pixelPos)));
        #ifdef NAMEPLATE_PIXEL
            vec2 pos = floor(pixelPos) + 0.5;
        #else
            vec2 pos = pixelPos;
        #endif
            float frame = float(NameplateClock.x);
            vec4 color = material(MaterialInputs(pos, frame));
            if (color.a < NAMEPLATE_MIN_ALPHA) {
        #ifdef NAMEPLATE_OUTLINE
                bool edge = nCovered(pos + vec2(1.0, 0.0), frame) || nCovered(pos - vec2(1.0, 0.0), frame)
                    || nCovered(pos + vec2(0.0, 1.0), frame) || nCovered(pos - vec2(0.0, 1.0), frame);
                if (!edge) {
                    discard;
                }
                color = nTone(NAMEPLATE_OUTLINE);
        #else
                discard;
        #endif
            }
            nameplateEmit(color);
        }
    """.trimIndent()

    private val CORE_HEADER = """
        layout(location = 0) in float sphericalVertexDistance;
        layout(location = 1) in float cylindricalVertexDistance;
        layout(location = 2) in vec2 pixelPos;
        layout(location = 3) in vec4 vertexColor;
        layout(location = 4) in vec4 lightColor;

        #ifndef OIT_ALPHA_ONLY
        layout(location = 0) out vec4 fragColor;
        #endif

        #ifndef NAMEPLATE_TONES
        #define NAMEPLATE_TONES 16
        #endif

        layout(std140) uniform NameplateMaterial {
            vec4 AreaRect;
            vec4 BodyRect;
            ivec4 NameplateClock;
            vec4 Inputs[4];
            vec4 AtlasRects[4];
            vec4 Tones[NAMEPLATE_TONES];
        };
    """.trimIndent()

    private val CORE_INDEXED_HEADER = """
        layout(location = 0) in float sphericalVertexDistance;
        layout(location = 1) in float cylindricalVertexDistance;
        layout(location = 2) in vec2 pixelPos;
        layout(location = 3) in vec4 vertexColor;
        layout(location = 4) in vec4 lightColor;
        layout(location = 5) flat in int nameplateIndex;

        #ifndef OIT_ALPHA_ONLY
        layout(location = 0) out vec4 fragColor;
        #endif

        #ifndef NAMEPLATE_TONES
        #define NAMEPLATE_TONES 16
        #endif

        #ifndef NAMEPLATE_BLOCKS
        #define NAMEPLATE_BLOCKS 1
        #endif

        struct NameplateBlock {
            vec4 areaRect;
            vec4 bodyRect;
            ivec4 clock;
            vec4 inputs[4];
            vec4 atlasRects[4];
            vec4 tones[NAMEPLATE_TONES];
        };

        layout(std140) uniform NameplateMaterial {
            NameplateBlock nameplateBlocks[NAMEPLATE_BLOCKS];
        };

        #define AreaRect nameplateBlocks[nameplateIndex].areaRect
        #define BodyRect nameplateBlocks[nameplateIndex].bodyRect
        #define NameplateClock nameplateBlocks[nameplateIndex].clock
        #define Inputs nameplateBlocks[nameplateIndex].inputs
        #define AtlasRects nameplateBlocks[nameplateIndex].atlasRects
        #define Tones nameplateBlocks[nameplateIndex].tones
    """.trimIndent()

    private val CORE_EMIT = """
        const float NAMEPLATE_DEPTH_ALPHA = 0.1;

        void nameplateEmit(vec4 color) {
        #ifdef NAMEPLATE_PARITY
            fragColor = color;
        #else
        #ifndef NAMEPLATE_EMISSIVE
            color.rgb *= lightColor.rgb;
        #endif
            color *= vertexColor * ColorModulator;
        #ifdef NAMEPLATE_DEPTH_ONLY
            if (color.a < NAMEPLATE_DEPTH_ALPHA) {
                discard;
            }
        #endif
        #ifdef OIT_ALPHA_ONLY
            executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
        #else
        #if defined(OIT_ACCUMULATE)
            color = sampleColorForAccumulation(color);
            vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
        #elif defined(NAMEPLATE_ADDITIVE)
            vec4 fogColor = vec4(0.0, 0.0, 0.0, FogColor.a);
        #else
            vec4 fogColor = FogColor;
        #endif
            fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart,
                FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
        #endif
        #endif
        }
    """.trimIndent()

    private val ES_HEADER = """
        precision highp float;
        precision highp int;

        in vec2 pixelPos;
        in vec4 vertexColor;

        out vec4 fragColor;

        uniform vec4 AreaRect;
        uniform vec4 BodyRect;
        uniform ivec4 NameplateClock;
        uniform vec4 Inputs[4];
        uniform vec4 AtlasRects[4];
        uniform vec4 Tones[NAMEPLATE_TONES];
        uniform vec2 ScreenSize;
        uniform vec4 ColorModulator;
        uniform vec4 NameplateLight;
    """.trimIndent()

    private val ES_EMIT = """
        void nameplateEmit(vec4 color) {
        #ifndef NAMEPLATE_EMISSIVE
            color.rgb *= NameplateLight.rgb;
        #endif
            fragColor = color * vertexColor * ColorModulator;
        }
    """.trimIndent()

    private val CORE_VERTEX = """
        #version 330
        #extension GL_ARB_separate_shader_objects : require

        #include <minecraft:fog.glsl>
        #include <minecraft:projection.glsl>
        #include <minecraft:dynamictransforms.glsl>

        layout(location = 0) in vec3 Position;
        layout(location = 1) in vec2 UV0;
        layout(location = 2) in vec4 Color;
        layout(location = 3) in ivec2 UV2;

        uniform sampler2D Sampler2;

        layout(location = 0) out float sphericalVertexDistance;
        layout(location = 1) out float cylindricalVertexDistance;
        layout(location = 2) out vec2 pixelPos;
        layout(location = 3) out vec4 vertexColor;
        layout(location = 4) out vec4 lightColor;
        layout(location = 5) flat out int nameplateIndex;

        void main() {
            gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
            sphericalVertexDistance = fog_spherical_distance(Position);
            cylindricalVertexDistance = fog_cylindrical_distance(Position);
            pixelPos = UV0;
            vertexColor = Color;
            lightColor = texelFetch(Sampler2, ivec2(UV2.x & 255, UV2.y) / 16, 0);
            nameplateIndex = UV2.x >> 8;
        }
    """.trimIndent()

    private val ES_VERTEX = """
        #version 300 es
        precision highp float;

        in vec3 Position;
        in vec4 Color;
        in vec2 UV0;

        uniform mat4 ProjMat;
        uniform mat4 ModelViewMat;

        out vec2 pixelPos;
        out vec4 vertexColor;

        void main() {
            gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
            pixelPos = UV0;
            vertexColor = Color;
        }
    """.trimIndent()

    private val CORE_INCLUDES = """
        #include <minecraft:fog.glsl>
        #include <minecraft:globals.glsl>
        #include <minecraft:dynamictransforms.glsl>
        #include <minecraft:oit.glsl>
    """.trimIndent()

    private val UBO_FOG = """
        layout(std140) uniform Fog {
            vec4 FogColor;
            float FogEnvironmentalStart;
            float FogEnvironmentalEnd;
            float FogRenderDistanceStart;
            float FogRenderDistanceEnd;
            float FogSkyEnd;
            float FogCloudsEnd;
        };

        float nFogValue(float distance, float start, float end) {
            if (distance <= start) {
                return 0.0;
            }
            return distance >= end ? 1.0 : (distance - start) / (end - start);
        }

        vec4 apply_fog(vec4 color, float spherical, float cylindrical, float environmentalStart, float environmentalEnd,
            float renderDistanceStart, float renderDistanceEnd, vec4 fogColor) {
            float value = max(nFogValue(spherical, environmentalStart, environmentalEnd),
                nFogValue(cylindrical, renderDistanceStart, renderDistanceEnd));
            return vec4(mix(color.rgb, fogColor.rgb, value * fogColor.a), color.a);
        }
    """.trimIndent()

    private val UBO_GLOBALS_SCREEN_FIRST = """
        layout(std140) uniform Globals {
            vec2 ScreenSize;
            float GlintAlpha;
            float GameTime;
            int MenuBlurRadius;
        };
    """.trimIndent()

    private val UBO_GLOBALS_CAMERA_FIRST = """
        layout(std140) uniform Globals {
            ivec3 CameraBlockPos;
            vec3 CameraOffset;
            vec2 ScreenSize;
            float GlintAlpha;
            float GameTime;
            int MenuBlurRadius;
            int UseRgss;
        };
    """.trimIndent()

    private val UBO_TRANSFORMS = """
        layout(std140) uniform DynamicTransforms {
            mat4 ModelViewMat;
            vec4 ColorModulator;
            vec3 ModelOffset;
            mat4 TextureMat;
        };
    """.trimIndent()

    private val UBO_PROJECTION = """
        #version 330

        layout(std140) uniform Projection {
            mat4 ProjMat;
        };
    """.trimIndent()

    private val UBO_VERTEX_BODY = """
        in vec3 Position;
        in vec4 Color;
        in vec2 UV0;
        in ivec2 UV2;

        uniform sampler2D Sampler2;

        out float sphericalVertexDistance;
        out float cylindricalVertexDistance;
        out vec2 pixelPos;
        out vec4 vertexColor;
        out vec4 lightColor;

        void main() {
            gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
            sphericalVertexDistance = length(Position);
            cylindricalVertexDistance = max(length(Position.xz), abs(Position.y));
            pixelPos = UV0;
            vertexColor = Color;
            lightColor = texelFetch(Sampler2, UV2 / 16, 0);
        }
    """.trimIndent()

    private val UBO_VERTEX = "$UBO_PROJECTION\n\n$UBO_TRANSFORMS\n\n$UBO_VERTEX_BODY"

    private val UBO_HEADER = CORE_HEADER.replace(Regex("layout\\(location = \\d+\\) "), "")

    private val FLAT_VERTEX = """
        #version 150

        in vec3 Position;
        in vec4 Color;
        in vec2 UV0;
        in ivec2 UV2;

        uniform sampler2D Sampler2;

        uniform mat4 ModelViewMat;
        uniform mat4 ProjMat;
        uniform int FogShape;

        out float vertexDistance;
        out vec2 pixelPos;
        out vec4 vertexColor;
        out vec4 lightColor;

        void main() {
            gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
            vertexDistance = FogShape == 0 ? length(Position) : max(length(Position.xz), abs(Position.y));
            pixelPos = UV0;
            vertexColor = Color;
            lightColor = texelFetch(Sampler2, UV2 / 16, 0);
        }
    """.trimIndent()

    private val FLAT_HEADER = """
        in float vertexDistance;
        in vec2 pixelPos;
        in vec4 vertexColor;
        in vec4 lightColor;

        out vec4 fragColor;

        uniform vec4 ColorModulator;
        uniform float FogStart;
        uniform float FogEnd;
        uniform vec4 FogColor;
        uniform vec2 ScreenSize;
    """.trimIndent()

    private val FLAT_STATE = """
        ivec4 NameplateClock;
        vec4 Inputs[4];
        vec4 AtlasRects[4];
        vec4 Tones[NAMEPLATE_TONES];
    """.trimIndent()

    private val FLAT_EMIT = """
        const float NAMEPLATE_DEPTH_ALPHA = 0.1;

        vec4 nLinearFog(vec4 color, float distance, float start, float end, vec4 fogColor) {
            if (distance <= start) {
                return color;
            }
            float value = distance < end ? smoothstep(start, end, distance) : 1.0;
            return vec4(mix(color.rgb, fogColor.rgb, value * fogColor.a), color.a);
        }

        void nameplateEmit(vec4 color) {
        #ifdef NAMEPLATE_PARITY
            fragColor = color;
        #else
        #ifndef NAMEPLATE_EMISSIVE
            color.rgb *= lightColor.rgb;
        #endif
            color *= vertexColor * ColorModulator;
        #ifdef NAMEPLATE_DEPTH_ONLY
            if (color.a < NAMEPLATE_DEPTH_ALPHA) {
                discard;
            }
        #endif
        #ifdef NAMEPLATE_ADDITIVE
            vec4 fogColor = vec4(0.0, 0.0, 0.0, FogColor.a);
        #else
            vec4 fogColor = FogColor;
        #endif
            fragColor = nLinearFog(color, vertexDistance, FogStart, FogEnd, fogColor);
        #endif
        }
    """.trimIndent()

    private val COMPAT_VERTEX = """
        #version 130

        out float vertexDistance;
        out vec2 pixelPos;
        out vec4 vertexColor;
        out vec2 lightUv;

        void main() {
            vec4 eye = gl_ModelViewMatrix * gl_Vertex;
            gl_Position = gl_ProjectionMatrix * eye;
            vertexDistance = length(eye.xyz);
            pixelPos = gl_MultiTexCoord0.xy;
            vertexColor = gl_Color;
            lightUv = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
        }
    """.trimIndent()

    private val COMPAT_HEADER = """
        #define fragColor gl_FragColor

        in float vertexDistance;
        in vec2 pixelPos;
        in vec4 vertexColor;
        in vec2 lightUv;

        uniform vec4 AreaRect;
        uniform vec4 BodyRect;
        uniform ivec4 NameplateClock;
        uniform vec4 Inputs[4];
        uniform vec4 AtlasRects[4];
        uniform vec4 Tones[NAMEPLATE_TONES];
        uniform vec2 ScreenSize;
        uniform sampler2D NameplateLightmap;
        uniform int NameplateFogMode;
        uniform int NameplateLit;

        mat2 nInverse(mat2 m) {
            float d = m[0][0] * m[1][1] - m[0][1] * m[1][0];
            return d == 0.0 ? mat2(0.0) : mat2(m[1][1], -m[0][1], -m[1][0], m[0][0]) / d;
        }
    """.trimIndent()

    private val COMPAT_EMIT = """
        float nFogFactor(float distance) {
            if (NameplateFogMode == 1) {
                return clamp((gl_Fog.end - distance) * gl_Fog.scale, 0.0, 1.0);
            }
            if (NameplateFogMode == 2) {
                return clamp(exp(-gl_Fog.density * distance), 0.0, 1.0);
            }
            if (NameplateFogMode == 3) {
                float e = gl_Fog.density * distance;
                return clamp(exp(-e * e), 0.0, 1.0);
            }
            return 1.0;
        }

        void nameplateEmit(vec4 color) {
        #ifdef NAMEPLATE_PARITY
            fragColor = color;
        #else
        #ifndef NAMEPLATE_EMISSIVE
            if (NameplateLit != 0) {
                color.rgb *= texture(NameplateLightmap, lightUv).rgb;
            }
        #endif
            color *= vertexColor;
        #ifdef NAMEPLATE_ADDITIVE
            vec3 fogColor = vec3(0.0);
        #else
            vec3 fogColor = gl_Fog.color.rgb;
        #endif
            fragColor = vec4(mix(fogColor, color.rgb, nFogFactor(vertexDistance)), color.a);
        #endif
        }
    """.trimIndent()

    private const val MAIN_OPEN = "void main() {\n"
    private const val INVERSE = "inverse("
    private const val COMPAT_INVERSE = "nInverse("
    private const val FLAT_VECTORS = 4

    fun vertex(dialect: GlslDialect): String = clean(
        when (dialect) {
            GlslDialect.CORE_330 -> CORE_VERTEX
            GlslDialect.ES_300 -> ES_VERTEX
            GlslDialect.CORE_330_UBO -> UBO_VERTEX
            GlslDialect.LEGACY_FLAT -> FLAT_VERTEX
            GlslDialect.COMPAT_130 -> COMPAT_VERTEX
        },
    )

    fun include(): String = clean(
        "#ifndef POLYPLUS_NAMEPLATE_GLSL\n#define POLYPLUS_NAMEPLATE_GLSL\n\n$CORE_INDEXED_HEADER\n\n${library()}\n\n" +
            "$CORE_EMIT\n\n$MAIN\n\n#endif\n",
    )

    fun fragment(
        dialect: GlslDialect,
        defines: List<String>,
        code: String,
        globals: GlslGlobals = GlslGlobals.CAMERA_FIRST,
        uniforms: List<FlatUniform> = emptyList(),
    ): String {
        val header = defines.joinToString("") { "#define $it\n" }
        return clean(
            when (dialect) {
                GlslDialect.CORE_330 ->
                    "#version 330\n#extension GL_ARB_separate_shader_objects : require\n\n$header\n$CORE_INCLUDES\n" +
                        "#include <${INCLUDE}>\n\n$code"
                GlslDialect.ES_300 -> "#version 300 es\n\n$header\n$ES_HEADER\n\n${library()}\n\n$ES_EMIT\n\n$code\n$MAIN\n"
                GlslDialect.CORE_330_UBO -> ubo(header, code, globals)
                GlslDialect.LEGACY_FLAT -> flat(header, code, uniforms)
                GlslDialect.COMPAT_130 ->
                    "#version 130\n\n$header\n$COMPAT_HEADER\n\n${library()}\n\n$COMPAT_EMIT\n\n$code\n" +
                        MAIN.replace(INVERSE, COMPAT_INVERSE) + "\n"
            },
        )
    }

    private fun ubo(header: String, code: String, globals: GlslGlobals): String {
        val blocks = when (globals) {
            GlslGlobals.SCREEN_FIRST -> UBO_GLOBALS_SCREEN_FIRST
            GlslGlobals.CAMERA_FIRST -> UBO_GLOBALS_CAMERA_FIRST
        }
        return "#version 330\n\n$header\n$UBO_FOG\n\n$blocks\n\n$UBO_TRANSFORMS\n\n$UBO_HEADER\n\n${library()}\n\n" +
            "$CORE_EMIT\n\n$code\n$MAIN\n"
    }

    private fun flat(header: String, code: String, uniforms: List<FlatUniform>): String {
        val declared = uniforms.joinToString("") { "uniform ${it.type} ${it.name};\n" }
        val main = MAIN.replace(MAIN_OPEN, "$MAIN_OPEN    nameplateLoad();\n")
        return "#version 150\n\n$header\n$FLAT_HEADER\n$declared\n$FLAT_STATE\n\n${flatLoad(uniforms)}\n\n" +
            "${library()}\n\n$FLAT_EMIT\n\n$code\n$main\n"
    }

    private fun flatLoad(uniforms: List<FlatUniform>): String {
        val names = uniforms.map { it.name }.toSet()
        val out = StringBuilder("void nameplateLoad() {\n")
        out.append("    NameplateClock = ivec4(${CLOCK_NAMES.joinToString(", ")});\n")
        for (v in 0 until FLAT_VECTORS) {
            val input = "${INPUT_PREFIX}$v"
            out.append("    Inputs[$v] = ${if (input in names) input else "vec4(0.0)"};\n")
        }
        for (k in 0 until FLAT_VECTORS) {
            val atlas = "${ATLAS_PREFIX}$k"
            out.append("    AtlasRects[$k] = ${if (atlas in names) atlas else "vec4(0.0)"};\n")
        }
        for (uniform in uniforms) {
            if (uniform.name.startsWith(TONE_PREFIX)) {
                out.append("    Tones[${uniform.name.removePrefix(TONE_PREFIX)}] = ${uniform.name};\n")
            }
        }
        return out.append("}").toString()
    }

    private fun library(): String = "$SCALAR_HELPERS\n${vectorHelpers()}\n$LIBRARY"

    private fun vectorHelpers(): String {
        val out = StringBuilder()
        for (width in 2..MAX_WIDTH) {
            val type = SocketType.vector(width).glsl
            val parts = COMPONENTS.take(width)
            fun helper(name: String, params: List<String>) {
                val signature = params.joinToString(", ") { "$type $it" }
                val body = parts.joinToString(", ") { c -> "$name(${params.joinToString(", ") { "$it.$c" }})" }
                out.append("$type $name($signature) { return $type($body); }\n")
            }
            helper("nDiv", listOf("a", "b"))
            helper("nMod", listOf("a", "b"))
            helper("nPow", listOf("a", "b"))
            helper("nAtan2", listOf("y", "x"))
            helper("nSmooth", listOf("e0", "e1", "x"))
            out.append("$type nClamp($type x, $type lo, $type hi) { return min(max(x, lo), hi); }\n")
            out.append("$type nMix($type a, $type b, $type t) { return a * (1.0 - t) + b * t; }\n")
        }
        return out.toString()
    }

    private fun clean(text: String): String = text.replace("\r", "")
}
