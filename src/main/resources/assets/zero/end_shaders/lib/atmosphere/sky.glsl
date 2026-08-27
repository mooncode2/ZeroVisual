vec3 getAtmosphere(vec3 viewPos, vec3 worldPos, out float atmosphereHardMixFactor) {
    float spaceFactor = min(max(cameraPosition.y, 0.0) / KARMAN_LINE, 1.0);
    float spaceFactorHalf = min(max(cameraPosition.y, 0.0) / (KARMAN_LINE * 0.5), 1.0);
    vec3 skyTint = fmix(vec3(1.0, 0.75 + timeBrightness * 0.25, 0.7 + timeBrightness * 0.3) * normalize(skyColor + 0.000001), vec3(0.35, 0.6, 1.0) * (0.35 + timeBrightnessSqrt * 0.35), spaceFactor);
         skyTint = pow(skyTint, vec3(1.0 - wetness * 0.5));
    vec3 daySkyColor = skyTint * fmix(vec3(1.0), biomeColor, isSpecificBiome * (1.0 - spaceFactor)) * fmix(vec3(1.0), weatherCol, wetness * (1.0 - spaceFactor));

    vec3 nWorldPos = normalize(worldPos);
    vec3 nViewPos = normalize(viewPos);
    float VoS = dot(nViewPos, sunVec);
    float VoM = dot(nViewPos, -sunVec);
    float VoSPositive = VoS * 0.5 + 0.5;
    float VoSClamped = clamp(VoS, 0.0, 1.0);
    float VoMClamped = clamp(VoM, 0.0, 1.0);
    float VoS2 = VoSClamped * VoSClamped;
    float VoM2 = VoMClamped * VoMClamped;
    float sunVis2 = sunVisibility * sunVisibility;
    float sunInv2 = 1.0 - sunVis2;
    float VoS_sv = VoS * sunVisibility;
    float oneMinVoS  = 1.0 - VoSClamped;
    float heightClamped = clamp(nWorldPos.y + spaceFactor * 0.55, 0.0, 1.0);

    float greenBandElevation = nWorldPos.y - 0.1;
    float greenBand = exp(-(greenBandElevation * greenBandElevation) / 0.025) * 0.2 * sunInv2 * oneMinVoS;

    vec3 scatteringColor = vec3(
        1.0 + VoS2 * (0.5 - timeBrightnessSqrt * 0.5) * sunVisibility,
        0.35 + greenBand + sunVisibility * 0.1 + timeBrightness * 0.2,
        0.0 + timeBrightnessSqrt * 0.1
    ) * (1.0 + VoS2 * sunVisibility);

    float scattering = pow(clamp(1.0 - nWorldPos.y, 0.0, 1.0), fmix(3.0 - VoS * 1.5, 1.0, spaceFactor)) * (0.5 - timeBrightnessSqrt * 0.3) * (1.0 - wetness * 0.5);
          scattering *= sqrt(clamp(1.0 + nWorldPos.y, 0.0, 1.0));

    daySkyColor = fmix(daySkyColor, scatteringColor, scattering * SUNRISE_SUNSET_INTENSITY);
    vec3 nightSky = fmix(lightNight, lightNight * weatherCol, wetness * 0.75);
         nightSky = pow(nightSky * (1.0 - wetness * 0.75), vec3(1.0 - wetness * 0.5));

    vec3 atmosphere = fmix(daySkyColor, nightSky * 0.5, moonVisibility);

    float heightPositive = max(nWorldPos.y * (1.0 - spaceFactor * 0.5) + spaceFactor * 0.5, 0.0);
    float density = clamp((1.0 - heightPositive * (0.65 + moonVisibility * moonVisibility * 1.5 * (1.0 - wetness))), 0.0, 1.0) + moonVisibility * 0.2;
          density = mix(density, clamp(pow5(1.0 - heightPositive * 2.0) * 32.0, 0.0, 1.0), spaceFactor);

    atmosphereHardMixFactor = spaceFactorHalf * density;
    atmosphere *= density;

    //Fade atmosphere to dark gray underground
    float caveSkyFactor = max(caveFactor, smoothstep(0.05, 0.55, nWorldPos.y));
    atmosphere = fmix(caveMinLightCol * (1.0 - isCaveBiome) + caveBiomeColor, atmosphere, caveSkyFactor);

    return atmosphere;
}
