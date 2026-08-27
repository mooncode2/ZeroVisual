vec3 nvec3(vec4 pos) {
    return pos.xyz / pos.w;
}

vec4 nvec4(vec3 pos) {
    return vec4(pos.xyz, 1.0);
}

float cdist(vec2 coord) {
    return max(abs(coord.x - 0.5), abs(coord.y - 0.5)) * 1.85;
}

#if WATER_NORMALS == 0
#if REFLECTION_MODE == 0
float errMult = 1.0;
#elif REFLECTION_MODE == 1
float errMult = 1.8;
#else
float errMult = 2.2;
#endif
#else
#if REFLECTION_MODE == 0
float errMult = 1.0;
#elif REFLECTION_MODE == 1
float errMult = 1.3;
#else
float errMult = 1.6;
#endif
#endif

vec4 Raytrace(sampler2D depthtex, vec3 viewPos, vec3 normal, float dither, out float border,
              int refinementSteps, float stepSize, float refinementMult, float stepMult) {
    vec3 pos = vec3(0.0);
    float dist = 0.0;

    #ifdef TAA
    dither = fract(dither + frameCounter * 0.61803398875);
    #endif

    vec3 start = viewPos + normal * 0.075;
    vec3 rayStep = stepSize * reflect(normalize(viewPos), normalize(normal));
    vec3 rayOffset = rayStep;
    viewPos += rayStep;

    int refinedSamples = 0;

    for (int i = 0; i < 30; i++) {
        pos = nvec3(gbufferProjection * nvec4(viewPos)) * 0.5 + 0.5;
        if (pos.x < -0.05 || pos.x > 1.05 || pos.y < -0.05 || pos.y > 1.05) break;

        float sampleDepth = texture2D(depthtex, pos.xy).r;
        vec3 hitViewPos = nvec3(gbufferProjectionInverse * nvec4(vec3(pos.xy, sampleDepth) * 2.0 - 1.0));

        #if REFLECTION_LOD == 1
        #ifdef VOXY
        if (sampleDepth >= 1.0) {
            sampleDepth = texture2D(vxDepthTexOpaque, pos.xy).r;
            hitViewPos = nvec3(vxProjInv * nvec4(vec3(pos.xy, sampleDepth) * 2.0 - 1.0));
        }
        #endif
        #ifdef DISTANT_HORIZONS
        if (sampleDepth >= 1.0) {
            sampleDepth = texture2D(dhDepthTex1, pos.xy).r;
            hitViewPos = nvec3(dhProjectionInverse * nvec4(vec3(pos.xy, sampleDepth) * 2.0 - 1.0));
        }
        #endif
        #endif

        dist = abs(dot(normalize(start - hitViewPos), normal));

        float error = length(viewPos - hitViewPos);
        float thickness = length(rayStep) * pow(length(rayOffset), 0.1) * errMult;
        if (error < thickness) {
            refinedSamples++;
            if (refinedSamples >= refinementSteps) break;
            rayOffset -= rayStep;
            rayStep *= refinementMult;
        }

        rayStep *= stepMult;
        rayOffset += rayStep;
        viewPos = start + rayOffset;
    }

    border = cdist(pos.st);

    return vec4(pos, dist);
}

vec3 Raytrace(sampler2D depthtex, vec3 viewPos, vec3 normal, float dither,
              int refinementSteps, float stepSize, float refinementMult, float stepMult,
              out float border, out vec2 cdistOut) {
    vec4 pos = Raytrace(depthtex, viewPos, normal, dither, border, refinementSteps, stepSize, refinementMult, stepMult);
    cdistOut = abs(pos.xy - 0.5) / vec2(0.6, 0.55);
    return pos.xyz;
}
