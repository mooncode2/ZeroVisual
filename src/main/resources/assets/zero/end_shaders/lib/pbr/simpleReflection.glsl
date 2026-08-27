void getReflection(inout vec3 color, in vec3 viewPos, in vec3 newNormal, in vec3 fresnel3, in float smoothness, in float skyLightMap) {
    vec4 reflection = vec4(0.0);

    #if REFLECTION >= 2
    #if defined REFLECTION_SPECULAR || defined GENERATED_SPECULAR
    if (smoothness > 0.001) {
        float blueNoiseDither = texture2D(noisetex, gl_FragCoord.xy / 512.0).b;

        #ifdef TAA
        blueNoiseDither = fract(blueNoiseDither + 1.61803398875 * mod(float(frameCounter), 3600.0));
        #endif

        float border = 0.0;
        int maxf = 6;

        #if WATER_NORMALS == 0
        float inc = 1.4;
        #else
        float inc = 1.8;
        #endif

        vec4 reflectPos = Raytrace(depthtex0, viewPos, newNormal, blueNoiseDither, border, maxf, 0.5, 0.1, inc);
        border = clamp(13.333 * (1.0 - border) * (0.9 * smoothness + 0.1), 0.0, 1.0);

        if (reflectPos.z < 1.0 - 1e-5 && border > 0.001) {
            float fovScale = gbufferProjection[1][1] / 1.37;

            #ifdef REFLECTION_ROUGH
            float dist = 0.03125 * pow2(1.0 - smoothness) * reflectPos.a * fovScale;
            float lod = max(log2(viewHeight * max(dist, 1e-5)), 0.0);
            #else
            float lod = 0.0;
            #endif

            if (lod < 1.0) {
                reflection.a = texture2DLod(colortex6, reflectPos.xy, 1.0).b;
                if (reflection.a > 0.001) {
                    reflection.rgb = texture2DLod(colortex0, reflectPos.xy, 1.0).rgb;
                }
            } else {
                for (int i = -2; i <= 2; i++) {
                    for (int j = -2; j <= 2; j++) {
                        vec2 refOffset = vec2(i, j) * exp2(lod - 1.0) / vec2(viewWidth, viewHeight);
                        vec2 refCoord = reflectPos.xy + refOffset;
                        float alpha = texture2DLod(colortex6, refCoord, lod).b;

                        if (alpha > 0.001) {
                            vec3 ssrSample = texture2DLod(colortex0, refCoord, max(lod - 1.0, 0.0)).rgb;
                            reflection.rgb += ssrSample;
                            reflection.a += alpha;
                        }
                    }
                }
                reflection /= 25.0;
            }

            reflection *= reflection.a;
            reflection.a = clamp(reflection.a * 2.0 - 1.0, 0.0, 1.0) * border;
        }
    }
    #endif
    #endif

    vec3 falloff = vec3(0.0);

    if (reflection.a < 1.0 && isEyeInWater == 0) {
        #ifdef OVERWORLD
        if (skyLightMap > 0.95) {
            vec3 viewPosRef = reflect(normalize(viewPos), newNormal);
            vec3 worldPosRef = ToWorld(viewPosRef);
            float atmosphereHardMixFactor = 0.0;
            vec3 reflectedAtmosphere = getAtmosphere(viewPosRef.xyz, worldPosRef.xyz, atmosphereHardMixFactor);

            float skyOcclusion = skyLightMap;
            #if REFLECTION_SKY_FALLOFF > 1
            skyOcclusion = clamp(1.0 - (1.0 - skyOcclusion) * REFLECTION_SKY_FALLOFF, 0.0, 1.0);
            #endif
            skyOcclusion *= skyOcclusion;
            falloff = mix(falloff, reflectedAtmosphere, skyOcclusion);
        }
        #elif defined NETHER
        falloff = netherColSqrt.rgb * 0.25;
        #elif defined END
        falloff = endAmbientColSqrt * 0.25;
        #endif

        #if MC_VERSION >= 11900
        falloff *= 1.0 - darknessFactor;
        #endif

        falloff *= 1.0 - blindFactor;
    }

    vec3 finalReflection = max(mix(falloff, reflection.rgb, reflection.a), vec3(0.0));
    color += finalReflection * fresnel3;
}
