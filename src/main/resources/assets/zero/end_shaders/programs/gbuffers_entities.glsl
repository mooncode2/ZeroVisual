#define GBUFFERS_ENTITIES

#include "/lib/common.glsl"

#ifdef FSH

// VSH Data //
in vec4 color;
in vec3 normal;
in vec2 texCoord, lmCoord;
flat in int mat;

#ifdef PBR
in float dist;
in vec3 binormal, tangent;
in vec3 viewVector;
in vec4 vTexCoord, vTexCoordAM;
#endif

// Uniforms //
uniform int currentRenderedItemId;
uniform int isEyeInWater;
uniform int frameCounter;

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
uniform vec4 entityColor;

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

#if defined GENERATED_EMISSION || defined GENERATED_SPECULAR
#include "/lib/pbr/generatedPBR.glsl"
#endif

#ifdef PBR
#if defined PARALLAX || defined SELF_SHADOW
#include "/lib/pbr/parallax.glsl"
#endif
#include "/lib/pbr/complexFresnel.glsl"
#include "/lib/pbr/materialGbuffers.glsl"
#endif

// Main //
void main() {
	vec4 albedo = texture2D(tex, texCoord);
	if (albedo.a < 0.00001) discard;
	albedo *= color;
	albedo.rgb = fmix(albedo.rgb, entityColor.rgb * entityColor.rgb * 2.0, entityColor.a);

	float lightningBolt = float(mat == 1);
	float subsurface = 0.0;
	float emission = 0.0, smoothness = 0.0, metalness = 0.0, f0 = 0.0, ao = 1.0, porosity = 0.5, parallaxShadow = 0.0;
    vec3 fresnel3 = vec3(0.0);

	vec2 lightmap = clamp(lmCoord, vec2(0.0), vec2(1.0));
	vec3 newNormal = normal;
	vec3 screenPos = vec3(gl_FragCoord.xy / vec2(viewWidth, viewHeight), gl_FragCoord.z);
	vec3 viewPos = ToNDC(screenPos);
	vec3 worldPos = ToWorld(viewPos);

    #ifdef PBR
    vec2 newCoord = vTexCoord.st * vTexCoordAM.pq + vTexCoordAM.st;
    mat3 tbnMatrix = mat3(tangent.x, binormal.x, normal.x,
                          tangent.y, binormal.y, normal.y,
                          tangent.z, binormal.z, normal.z);

    getMaterials(smoothness, metalness, f0, emission, subsurface, porosity, ao, newNormal, newCoord, dcdx, dcdy, tbnMatrix);
    fresnel3 = max(fresnel3, getPBRFresnel(albedo.rgb, newNormal, viewPos, smoothness, metalness, f0, ao));
    albedo.rgb *= ao * ao;
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

	if (lightningBolt < 0.5) {
		#if defined GENERATED_EMISSION || defined GENERATED_SPECULAR
		float generatedEmission = emission;
		float generatedSmoothness = 0.0;
		float generatedMetalness = 0.0;
		float generatedSubsurface = subsurface;
		generateIPBR(albedo, worldPos, viewPos, lightmap, generatedEmission, generatedSmoothness, generatedMetalness, generatedSubsurface);
		#ifdef GENERATED_EMISSION
		emission = max(emission, generatedEmission);
		#endif
		#ifdef GENERATED_SPECULAR
		smoothness = max(smoothness, generatedSmoothness);
		metalness = max(metalness, generatedMetalness);
		#endif
		subsurface = max(subsurface, generatedSubsurface);
		if (smoothness > 0.01 && f0 <= 0.0) f0 = 0.04;
		#endif

        #ifdef GENERATED_SPECULAR
        if (smoothness > 0.01) {
            float generatedFresnel = pow(clamp(1.0 + dot(newNormal, normalize(viewPos)), 0.0, 1.0), 5.0);
            vec3 generatedAlbedo = pow(max(albedo.rgb, vec3(0.0)), vec3(2.2));
            vec3 generatedBase = mix(vec3(0.04), max(generatedAlbedo, vec3(0.04)), clamp(metalness, 0.0, 1.0));
            fresnel3 = max(fresnel3, mix(generatedBase, vec3(1.0), generatedFresnel));
        }
        #endif

		vec3 shadow = vec3(0.0);
		gbuffersLighting(color, albedo, screenPos, viewPos, worldPos, newNormal, shadow, lightmap, NoU, NoL, NoE, subsurface, emission, smoothness, metalness, f0, parallaxShadow);
	}

    #if defined PBR || defined GENERATED_SPECULAR
	/* DRAWBUFFERS:0367 */
	gl_FragData[0] = albedo;
	gl_FragData[1] = vec4(clamp(smoothness, 0.0, 0.95), lightmap.y * 0.5, 0.25, 1.0);
    gl_FragData[2] = vec4(encodeNormal(newNormal), float(gl_FragCoord.z < 1.0), 1.0);
    gl_FragData[3] = vec4(fresnel3, 1.0);
    #else
	/* DRAWBUFFERS:03 */
	gl_FragData[0] = albedo;
	gl_FragData[1] = vec4(0.0, 0.0, 0.25, 1.0);
    #endif
}

#endif


//**//**//**//**//**//**//**//**//**//**//**//**//**//**//


#ifdef VSH

// VSH Data //
out vec4 color;
out vec3 normal;
out vec2 texCoord, lmCoord;
flat out int mat;

#ifdef PBR
out float dist;
out vec3 binormal, tangent;
out vec3 viewVector;
out vec4 vTexCoord, vTexCoordAM;
#endif

// Uniforms //
uniform int entityId;

#ifdef PBR
attribute vec4 mc_midTexCoord;
attribute vec4 at_tangent;
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

    mat = int(entityId);

	//Position
	gl_Position = ftransform();
}

#endif