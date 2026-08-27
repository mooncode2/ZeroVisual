#define GBUFFERS_BLOCK

#include "/lib/common.glsl"

#ifdef FSH

// VSH Data //
in vec4 color;
in vec3 normal;
in vec2 texCoord, lmCoord;

#ifdef PBR
in float dist;
in vec3 binormal, tangent;
in vec3 viewVector;
in vec4 vTexCoord, vTexCoordAM;
#endif

// Uniforms //
uniform int isEyeInWater;
uniform int frameCounter;
uniform int blockEntityId;

#ifdef AURORA_LIGHTING_INFLUENCE
uniform int moonPhase;
#endif

uniform int worldDay;

uniform float frameTimeCounter;
uniform float far, near;
uniform float viewWidth, viewHeight;
uniform float blindFactor, nightVision;
#if MC_VERSION >= 11900
uniform float darknessFactor;
#endif

#ifdef OVERWORLD
uniform float wetness;
uniform float shadowFade;
uniform float timeAngle, timeBrightness;

#if MC_VERSION >= 12104
uniform float isPaleGarden;
#endif

uniform vec3 skyColor;
#endif
#ifdef PBR
uniform ivec2 atlasSize;
#endif

uniform ivec2 eyeBrightnessSmooth;
uniform vec3 cameraPosition;

#ifdef NETHER
uniform vec3 fogColor;
#endif

uniform vec4 lightningBoltPosition;

uniform sampler2D tex, noisetex;

#ifdef PBR
uniform sampler2D specular;
uniform sampler2D normals;
#endif

#ifdef VX_SUPPORT
uniform sampler3D floodfillSampler, floodfillSamplerCopy;
uniform usampler3D voxelSampler;
#endif

uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 shadowProjection;
uniform mat4 shadowModelView;

// Global Variables //
#if defined OVERWORLD
const vec2 sunRotationData = vec2(cos(sunPathRotation * 0.01745329251994), -sin(sunPathRotation * 0.01745329251994));
float fractTimeAngle = fract(timeAngle - 0.25);
float ang = (fractTimeAngle + (cos(fractTimeAngle * 3.14159265358979) * -0.5 + 0.5 - fractTimeAngle) / 3.0) * 6.28318530717959;
vec3 sunVec = normalize((gbufferModelView * vec4(vec3(-sin(ang), cos(ang) * sunRotationData) * 2000.0, 1.0)).xyz);
#elif defined END
const vec2 sunRotationData = vec2(cos(sunPathRotation * 0.01745329251994), -sin(sunPathRotation * 0.01745329251994));
vec3 sunVec = normalize((gbufferModelView * vec4(1.0, sunRotationData * 2000.0, 1.0)).xyz);
#else
vec3 sunVec = vec3(0.0);
#endif

vec3 upVec = normalize(gbufferModelView[1].xyz);
vec3 eastVec = normalize(gbufferModelView[0].xyz);

#ifdef OVERWORLD
float eBS = eyeBrightnessSmooth.y / 240.0;
float caveFactor = fmix(clamp((cameraPosition.y - 56.0) / 16.0, float(sign(isEyeInWater)), 1.0), 1.0, sqrt(eBS));
float sunVisibility = clamp((dot( sunVec, upVec) + 0.15) * 3.0, 0.0, 1.0);
float moonVisibility = clamp((dot(-sunVec, upVec) + 0.15) * 3.0, 0.0, 1.0);
vec3 lightVec = sunVec * ((timeAngle < 0.5325 || timeAngle > 0.9675) ? 1.0 : -1.0);
#endif

#ifdef PBR
vec2 dcdx = dFdx(texCoord);
vec2 dcdy = dFdy(texCoord);
#endif

// Includes //
#include "/lib/util/encode.glsl"
#include "/lib/util/bayerDithering.glsl"
#include "/lib/util/transformMacros.glsl"
#include "/lib/util/ToNDC.glsl"
#include "/lib/util/ToWorld.glsl"
#include "/lib/util/ToShadow.glsl"
#include "/lib/color/lightColor.glsl"
#include "/lib/pbr/ggx.glsl"

#if defined VX_SUPPORT || defined DYNAMIC_HANDLIGHT
#include "/lib/vx/blocklightColor.glsl"
#endif

#ifdef VX_SUPPORT
#include "/lib/vx/voxelization.glsl"
#endif

#ifdef DYNAMIC_HANDLIGHT
#include "/lib/lighting/handlight.glsl"
#endif

#include "/lib/lighting/lightning.glsl"
#include "/lib/lighting/shadows.glsl"

#ifdef VC_SHADOWS
#include "/lib/lighting/cloudShadows.glsl"
#endif

#include "/lib/lighting/gbuffersLighting.glsl"

#ifdef PBR
#if defined PARALLAX || defined SELF_SHADOW
#include "/lib/pbr/parallax.glsl"
#endif
#include "/lib/pbr/complexFresnel.glsl"
#include "/lib/pbr/materialGbuffers.glsl"
#endif

void sampleNebulaNoise(vec2 coord, inout float colorMixer, inout float noise) {
    colorMixer = texture2D(noisetex, coord * 0.25).r;
    noise = texture2D(noisetex, coord * 0.50).r;
    noise *= colorMixer;
    noise *= texture2D(noisetex, coord * 0.125).r;
    noise *= 4.0;
}

// Main //
void main() {
    vec4 albedo = texture2D(tex, texCoord) * color;

    vec2 lightmap = clamp(lmCoord, vec2(0.0), vec2(1.0));
    vec3 newNormal = normal;
    vec3 screenPos = vec3(gl_FragCoord.xy / vec2(viewWidth, viewHeight), gl_FragCoord.z);
    vec3 viewPos = ToNDC(screenPos);
    vec3 worldPos = ToWorld(viewPos);

    float subsurface = 0.0;
    float emission = 0.0, smoothness = 0.0, metalness = 0.0, f0 = 0.0, ao = 1.0, porosity = 0.5, parallaxShadow = 0.0;
    vec3 fresnel3 = vec3(0.0);

    #ifdef PBR
    vec2 newCoord = vTexCoord.st * vTexCoordAM.pq + vTexCoordAM.st;
    mat3 tbnMatrix = mat3(tangent.x, binormal.x, normal.x,
                          tangent.y, binormal.y, normal.y,
                          tangent.z, binormal.z, normal.z);

    if (blockEntityId != 9999) {
        getMaterials(smoothness, metalness, f0, emission, subsurface, porosity, ao, newNormal, newCoord, dcdx, dcdy, tbnMatrix);
        fresnel3 = max(fresnel3, getPBRFresnel(albedo.rgb, newNormal, viewPos, smoothness, metalness, f0, ao));
        albedo.rgb *= ao * ao;
    }
    #endif

    float NoU = clamp(dot(newNormal, upVec), -1.0, 1.0);
    #if defined OVERWORLD
    float NoL = clamp(dot(newNormal, lightVec), 0.0, 1.0);
    #elif defined END
    float NoL = clamp(dot(newNormal, sunVec), 0.0, 1.0);
    #else
    float NoL = 0.0;
    #endif
    float NoE = clamp(dot(newNormal, eastVec), -1.0, 1.0);

    if (blockEntityId == 9999) {
        const vec3[8] portalColors = vec3[](
            vec3(0.35, 0.65, 0.75),
            vec3(0.60, 0.75, 1.10),
            vec3(0.45, 0.80, 0.90),
            vec3(0.35, 1.05, 1.85),
            vec3(0.75, 0.85, 0.65),
            vec3(0.40, 0.55, 0.80),
            vec3(0.50, 0.65, 1.00),
            vec3(0.55, 0.45, 0.80)
        );

        albedo.rgb = endAmbientColSqrt * 0.25;

        float frequency = 1.5;

        for (int i = 1; i <= 8; i++) {
            float colormult = 1.0 / (25.0 + i);
            float rotation = (i - 0.1 * i + 0.71 * i - 11 * i + 21) * 0.01 + i * 0.01;
            float Cos = cos(radians(rotation));
            float Sin = sin(radians(rotation));
            vec2 offset = vec2(0.0, 0.000025 * pow2(16.0 - i));

            vec3 worldPosM = normalize((gbufferModelViewInverse * vec4(viewPos * (i * frequency + 1), 1.0)).xyz);
            if (abs(NoU) > 0.95) {
                worldPosM.xz /= worldPosM.y;
                worldPosM.xz *= 0.05 * sign(- worldPos.y);
                worldPosM.xz *= abs(worldPos.y) + i * frequency;
                worldPosM.xz -= cameraPosition.xz * 0.05;
            } else {
                vec3 absPos = abs(worldPos);
                if (abs(NoE) > 0.9) {
                    worldPosM.xz = worldPosM.yz / worldPosM.x;
                    worldPosM.xz *= 0.05 * sign(- worldPos.x);
                    worldPosM.xz *= abs(worldPos.x) + i * frequency;
                    worldPosM.xz -= cameraPosition.yz * 0.05;
                } else {
                    worldPosM.xz = worldPosM.yx / worldPosM.z;
                    worldPosM.xz *= 0.05 * sign(- worldPos.z);
                    worldPosM.xz *= abs(worldPos.z) + i * frequency;
                    worldPosM.xz -= cameraPosition.yx * 0.05;
                }
            }

            vec2 animation = fract((frameTimeCounter + 1000.0) * (i + 8) * 0.125 * offset);
            vec2 coord = mat2(Cos, Sin, -Sin, Cos) * worldPosM.xz + animation;
            if (i % 2 != 0) coord = coord.yx + vec2(-1.0, 1.0) * animation.y;

            if (i > 6) {
                float nebulaNoise = 0.0;
                float nebulaColorMixer = 0.0;
                sampleNebulaNoise(coord, nebulaColorMixer, nebulaNoise);
                      nebulaColorMixer = pow4(nebulaColorMixer) * 6.0;

                vec3 nebula =  fmix(endNebulaColFirst, endNebulaColSecond, nebulaColorMixer) * nebulaNoise * nebulaNoise;
                        nebula *= length(nebula) * END_NEBULA_BRIGHTNESS;

                albedo.rgb += nebula * colormult;
            }
            vec3 portalSample = pow(texture2D(tex, coord * 2.0).rgb * portalColors[i-1], vec3(0.75));
            albedo.rgb += portalSample * length(portalSample) * colormult * 12.0;
        }
    } else {
        vec3 shadow = vec3(0.0);
        gbuffersLighting(color, albedo, screenPos, viewPos, worldPos, newNormal, shadow, lightmap, NoU, NoL, NoE, subsurface, emission, smoothness, metalness, f0, parallaxShadow);
    }

    #if defined PBR
	/* DRAWBUFFERS:0367 */
	gl_FragData[0] = albedo;
	gl_FragData[1] = vec4(clamp(smoothness, 0.0, 0.95), lightmap.y * 0.5, 0.0, 1.0);
    gl_FragData[2] = vec4(encodeNormal(newNormal), float(gl_FragCoord.z < 1.0), 1.0);
    gl_FragData[3] = vec4(fresnel3, 1.0);
    #else
	/* DRAWBUFFERS:03 */
	gl_FragData[0] = albedo;
	gl_FragData[1] = vec4(0.0, 0.0, 0.0, 1.0);
    #endif
}

#endif


//**//**//**//**//**//**//**//**//**//**//**//**//**//**//


#ifdef VSH

// VSH Data //
out vec4 color;
out vec3 normal;
out vec2 texCoord, lmCoord;

#ifdef PBR
out float dist;
out vec3 binormal, tangent;
out vec3 viewVector;
out vec4 vTexCoord, vTexCoordAM;
#endif

// Attributes //
#ifdef PBR
attribute vec4 mc_midTexCoord;
attribute vec4 at_tangent;
#endif

// Uniforms //
#ifdef TAA
uniform float viewWidth, viewHeight;
#endif

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;

// Includes //
#ifdef TAA
#include "/lib/antialiasing/jitter.glsl"
#endif

// Main //
void main() {
	texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    
	lmCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
	lmCoord = clamp((lmCoord - 0.03125) * 1.06667, vec2(0.0), vec2(0.9333, 1.0));
	
    color = gl_Color;

    normal = normalize(gl_NormalMatrix * gl_Normal);

    #ifdef PBR
    binormal = normalize(gl_NormalMatrix * cross(at_tangent.xyz, gl_Normal.xyz) * at_tangent.w);
    tangent = normalize(gl_NormalMatrix * at_tangent.xyz);

    mat3 tbnMatrix = mat3(tangent.x, binormal.x, normal.x,
                          tangent.y, binormal.y, normal.y,
                          tangent.z, binormal.z, normal.z);

    dist = length(gl_ModelViewMatrix * gl_Vertex);
    viewVector = tbnMatrix * (gl_ModelViewMatrix * gl_Vertex).xyz;

    vec2 midCoord = (gl_TextureMatrix[0] * mc_midTexCoord).st;
    vec2 texMinMidCoord = texCoord - midCoord;
    vTexCoordAM.pq = abs(texMinMidCoord) * 2.0;
    vTexCoordAM.st = min(texCoord, midCoord - texMinMidCoord);
    vTexCoord.xy = sign(texMinMidCoord) * 0.5 + 0.5;
    #endif

	//Position
	vec4 position = gbufferModelViewInverse * gl_ModelViewMatrix * gl_Vertex;

	gl_Position = gl_ProjectionMatrix * gbufferModelView * position;

	//TAA jittering
    #ifdef TAA
	gl_Position.xy = TAAJitter(gl_Position.xy, gl_Position.w);
    #endif
}

#endif