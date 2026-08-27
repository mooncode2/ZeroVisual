void getDynamicWeather(inout float speed, inout float amount, inout float thickness, inout float density, inout float height, inout float scale) {
	#ifdef VC_DYNAMIC_WEATHER
	float day = float(worldDay) + timeAngle;
    float sinDay05 = sin(day * 0.5);
    float cosDay075 = cos(day * 0.75);
    float cosDay15 = cos(day * 1.5);
    float sinDay2 = sin(day * 2.0);
    float waveFunction = sinDay05 * cosDay075 + sinDay2 * 0.25 - cosDay15 * 0.75;

    amount += waveFunction * (0.5 + cosDay075 * 0.5) * 0.5 + moonVisibility * 0.25;
    height += waveFunction * sinDay2 * 75.0;
    scale += waveFunction * cosDay075 - moonVisibility * 0.25;
    thickness += waveFunction * waveFunction * cosDay15;
    density += waveFunction * sinDay05;
	#endif

	#if MC_VERSION >= 12104
    amount -= isPaleGarden;
	#endif

    amount += 0.25;
}

float CloudLocalTop(float noiseBase) {
	float localTop = clamp((noiseBase - 0.48) * 2.15, 0.0, 1.0);
	      localTop = localTop * localTop * (3.0 - 2.0 * localTop);

	return mix(0.70, 1.08, localTop);
}

float CloudVerticalCoverage(float sampleAltitude, float noiseBase) {
	float localTop = CloudLocalTop(noiseBase);

	float bottomPenalty = (1.0 - smoothstep(0.00, 0.18, sampleAltitude)) * 0.35;
	float topPenalty = smoothstep(localTop - 0.20, localTop + 0.12, sampleAltitude) * 0.85;
	float planeGuard = smoothstep(0.94, 1.0, sampleAltitude) * 0.90;

	return bottomPenalty + max(topPenalty, planeGuard);
}

float CloudHeightDensity(float sampleAltitude, float noiseBase) {
	float localTop = CloudLocalTop(noiseBase);

	float bottomFade = smoothstep(0.00, 0.18, sampleAltitude);
	float bodyFade = 0.82 + smoothstep(0.16, 0.58, sampleAltitude) * 0.18;
	float topFade = 1.0 - smoothstep(localTop - 0.20, localTop + 0.14, sampleAltitude) * 0.45;
	float planeFade = 1.0 - smoothstep(0.965, 1.0, sampleAltitude);

	return clamp((0.30 + bottomFade * 0.70) * bodyFade * topFade * planeFade, 0.0, 1.08);
}

float cloudSampleBase(vec2 coord) {
	float perlinBase = texture2D(noisetex, coord * 0.5 + vec2(0.17, -0.11)).r * 0.6;
	      perlinBase += texture2D(noisetex, coord * 1.5 + vec2(-0.07, 0.19)).r * 0.4;
		  perlinBase = perlinBase * 0.9 + pow3(perlinBase) * 0.4;

	return clamp((perlinBase - 0.35) * 1.4 + 0.5, 0.0, 1.0);
}

float CloudCoverageDefault(float sampleAltitude, float amount) {
	float noiseCoverage = abs(sampleAltitude - 0.125);

	noiseCoverage *= sampleAltitude > 0.125 ? (2.5 - amount * 0.1) : 8.0;
	noiseCoverage = noiseCoverage * noiseCoverage * 4.0;

	return noiseCoverage;
}

float CloudApplyDensity(float noise, float density) {
	noise *= density * 0.125;
	noise *= (1.0 - 0.25 * wetness);
	noise = noise / sqrt(noise * noise + 0.5);

	return noise;
}

float CloudCombineDefault(float noiseBase, float noiseCoverage, float amount, float density) {
	float noise = noiseBase * 21.0;

	noise = fmix(noise - noiseCoverage, 21.0 - noiseCoverage * 2.5, 0.2 * wetness);
	noise = max(noise - amount, 0.0);

	noise = CloudApplyDensity(noise, density);

	return noise;
}

float CloudShadowSample(vec2 coord, vec2 wind, float sampleAltitude, float amount, float density) {
	coord *= 0.0025;

	vec2 baseCoord = coord * 0.5 + wind * 2.0;

	float noiseBase = cloudSampleBase(baseCoord);
	float noiseCoverage = CloudCoverageDefault(sampleAltitude, amount);
	      noiseCoverage += CloudVerticalCoverage(sampleAltitude, noiseBase);

	float noise = CloudCombineDefault(noiseBase, noiseCoverage, amount, density);
	      noise *= CloudHeightDensity(sampleAltitude, noiseBase);

	return noise;
}

void getCloudShadow(vec2 coord, vec2 wind, float amount, float density, inout float noise) {
	float lowerCloud = CloudShadowSample(coord, wind, 0.20, amount, density);
	float midCloud = CloudShadowSample(coord, wind, 0.45, amount, density);
	float upperCloud = CloudShadowSample(coord, wind, 0.70, amount, density);
	float topCloud = CloudShadowSample(coord, wind, 0.88, amount, density);

	noise = lowerCloud * 0.18 + midCloud * 0.34 + upperCloud * 0.32 + topCloud * 0.16;

    #ifndef COMPOSITE_0
	noise = clamp(exp(-2.0 * noise), 0.0, 1.0);
    #else
    noise = clamp(exp(-3.5 * noise), 0.0, 1.0);
    #endif
    noise = fmix(1.0, noise, shadowFade);
}
