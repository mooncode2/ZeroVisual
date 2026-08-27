#define COMPOSITE_3

// Settings //
#include "/lib/common.glsl"

#ifdef FSH

// VSH Data //
in vec2 texCoord;

// Uniforms //
uniform sampler2D colortex0;

#ifdef REFRACTION
uniform int isEyeInWater;
uniform float aspectRatio;
uniform sampler2D colortex3;
uniform sampler2D depthtex0, depthtex1;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
#endif

// Includes //
#ifdef REFRACTION
#include "/lib/util/ToView.glsl"
#include "/lib/util/encode.glsl"
#endif

// Main //
void main() {
    vec4 color = texture2D(colortex0, texCoord);

    #ifdef REFRACTION
    float z0 = texture2D(depthtex0, texCoord).r;
    float z1 = texture2D(depthtex1, texCoord).r;

    if (z1 > z0) {
        vec3 distort = texture2D(colortex3, texCoord).rgb;

        if (distort.xy != vec2(0.0)) {
            vec3 viewPos = ToView(vec3(texCoord, z0));
            float fovScale = gbufferProjection[1][1] / 1.37;

            distort = decodeNormal(distort.xy) * REFRACTION_STRENGTH * (1.0 + length(viewPos.y) * float(isEyeInWater == 1));
            distort.xy *= vec2(1.0 / aspectRatio, 1.0) * fovScale / max(length(viewPos.xyz), 8.0);

            vec2 newCoord = clamp(texCoord + distort.xy, 0.0, 1.0);
            float distortMask = texture2D(colortex3, newCoord).b;
            float water = float(distortMask > 0.79 && distortMask < 0.81);

            if (water > 0.0 && z0 > 0.56) {
                z0 = texture2D(depthtex0, newCoord).r;
                z1 = texture2D(depthtex1, newCoord).r;
                color.rgb = texture2D(colortex0, newCoord).rgb;
            }
        }
    }
    #endif

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = color;
}

#endif


//**//**//**//**//**//**//**//**//**//**//**//**//**//**//


#ifdef VSH

// VSH Data //
out vec2 texCoord;

// Main //
void main() {
    texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;

    //Position
    gl_Position = ftransform();
}

#endif
