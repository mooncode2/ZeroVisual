#version 330

#define DEBUG_UBO 1

layout(std140) uniform SkyConfig {
    mat4 CamRotInv;
    vec4 RayParams;
    vec4 FlashData;
    vec4 CamData;
    vec4 SunData;
    vec4 Toggles;
    vec4 Strengths;
};

uniform sampler2D NoiseTex;

in vec2 texCoord0;

out vec4 fragColor;

float uTime;
float uFlashIntensity;
float uFlashIntensitySqrt;
float uNebula;
float uStars;
float uBlackHole;
float uFlashes;
float uDisk;
float uStarBrightness;
float uNebulaBrightness;
float uBlackHoleSize;
float uFlashBrightness;
vec3 uCameraPos;
vec3 uAtmosphereColor;

const float PI = 3.14159265358979;
const float END_ANGLE = 0.0;
const float STAR_BRIGHTNESS = 1.2;
const float STAR_AMOUNT = 1.6;
const float STAR_SIZE = 1.0;

const float END_DISK_HEIGHT = -60.0;
const float END_DISK_THICKNESS = 12.0;
const float END_DISK_TOP = END_DISK_HEIGHT + END_DISK_THICKNESS * 10.0;
const float END_DISK_AMOUNT = 9.0;
const float END_DISK_OPACITY = 1.0;

const vec3 END_LIGHT_COL_SQRT = vec3(195.0, 170.0, 165.0) / 255.0 * 1.45;
const vec3 END_LIGHT_COL = END_LIGHT_COL_SQRT * END_LIGHT_COL_SQRT;
const vec3 END_AMBIENT_COL_SQRT = vec3(225.0, 205.0, 195.0) / 255.0 * 0.25;
const vec3 END_FLASH_COL = (vec3(255.0, 140.0, 185.0) / 255.0) * (vec3(255.0, 140.0, 185.0) / 255.0);
const vec3 END_NEBULA_COL_FIRST = (vec3(208.0, 132.0, 44.0) / 255.0 * 2.60) * (vec3(208.0, 132.0, 44.0) / 255.0 * 2.60);
const vec3 END_NEBULA_COL_SECOND = (vec3(32.0, 244.0, 184.0) / 255.0 * 1.60) * (vec3(32.0, 244.0, 184.0) / 255.0 * 1.60);

float fmix(float a, float b, float t) {
    return a + clamp(t, 0.0, 1.0) * (b - a);
}

vec3 fmix(vec3 a, vec3 b, float t) {
    return a + clamp(t, 0.0, 1.0) * (b - a);
}

float pow2(float x) { return x * x; }
float pow3(float x) { return x * x * x; }
float pow4(float x) { return x * x * x * x; }
float pow8(float x) { return x * x * x * x * x * x * x * x; }
float pow10(float x) { return pow8(x) * x * x; }
float pow20(float x) { return pow10(x) * pow10(x); }
float pow24(float x) { return pow8(x) * pow8(x) * pow8(x); }
float pow32(float x) { return pow8(x) * pow8(x) * pow8(x) * pow8(x); }

float Bayer2(vec2 a) {
    a = floor(a);
    return fract(dot(a, vec2(0.5, a.y * 0.75)));
}

#define Bayer4(a) (Bayer2(0.5 * (a)) * 0.25 + Bayer2(a))
#define Bayer8(a) (Bayer4(0.5 * (a)) * 0.25 + Bayer2(a))

void sampleNebulaNoise(vec2 coord, inout float colorMixer, inout float noise) {
    colorMixer = texture(NoiseTex, coord * 0.25).r;
    noise = texture(NoiseTex, coord * 0.50).r;
    noise *= colorMixer;
    noise *= texture(NoiseTex, coord * 0.125).r;
    noise *= 2.0 + noise * 20.0;
}

float getSpiralWarping(vec2 coord) {
    float whirl = -15.0;
    float arms = 15.0;

    if (dot(coord, coord) < 0.0000001) {
        return 0.0;
    }

    coord = vec2(atan(coord.y, coord.x) + uTime * 0.1, sqrt(coord.x * coord.x + coord.y * coord.y));
    float center = pow8(1.0 - coord.y) * 24.0;
    float spiral = sin((coord.x + sqrt(coord.y) * whirl) * arms) + center - coord.y;

    return clamp(spiral * 0.025, 0.0, 1.0);
}

vec4 getSupernovaAtPos(vec3 flashPos, vec3 worldPos) {
    vec2 flashCoord = flashPos.xz / max(flashPos.y + length(flashPos), 0.00001);
    vec2 blackHoleCoord = worldPos.xz / max(length(worldPos) + worldPos.y, 0.00001) - flashCoord;

    float nebulaNoise = 0.0;
    float nebulaColorMixer = 0.0;
    sampleNebulaNoise(blackHoleCoord, nebulaColorMixer, nebulaNoise);
    nebulaColorMixer = pow4(nebulaColorMixer) * 6.0;

    float endFlashPoint = 1.0 - clamp(length(blackHoleCoord), 0.0, 1.0);
    float animation = uFlashIntensitySqrt * 17.0;
    float flashPoint = max(endFlashPoint, 0.0001);
    float visibility = pow(flashPoint, max(20.0 - animation, 0.01)) * max(1.0 - (1.0 + uFlashIntensity) * pow(flashPoint, max(24.0 - animation, 0.01)), 0.0);

    return vec4(nebulaNoise, nebulaColorMixer, visibility, endFlashPoint);
}

vec2 rotate2D(vec2 p, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return mat2(c, -s, s, c) * p;
}

void drawEndNebula(inout vec3 color, vec3 worldPos, float VoU, float VoS) {
    bool blackHoleEnabled = uBlackHole > 0.5;

    vec3 blackHoleColor = vec3(5.6, 3.2, 0.7) * END_LIGHT_COL;
    float absVoU = abs(VoU);
    float sqrtabsVoU = sqrt(absVoU);
    float blackHoleSize = uBlackHoleSize;
    float hole = pow(pow4(pow32(VoS)), blackHoleSize);
    float gravityLens = hole;
    float holeSq = hole;
    holeSq *= holeSq;
    holeSq *= holeSq;

    if (!blackHoleEnabled) {
        hole = 0.0;
        holeSq = 0.0;
        gravityLens = 0.0;
    }

    vec3 wSunVec = normalize(SunData.xyz);
    vec2 sunCoord = wSunVec.xz / max(wSunVec.y + length(wSunVec), 0.00001);
    vec2 blackHoleCoord = worldPos.xz / max(length(worldPos) + worldPos.y, 0.00001) - sunCoord;
    blackHoleCoord.y -= blackHoleCoord.x * END_ANGLE;
    float warping = getSpiralWarping(blackHoleCoord);
    blackHoleCoord.x *= 0.75 - absVoU * 0.25;
    blackHoleCoord.y *= 5.0;
    blackHoleCoord.y += pow2(blackHoleCoord.x * 2.25) * sqrtabsVoU;

    if (!blackHoleEnabled) {
        warping = 0.0;
    }

    if (uNebula > 0.5) {
        vec2 nebulaCoord = worldPos.xz / (length(worldPos.y) + length(worldPos.xyz));
        nebulaCoord += warping * gravityLens;
        nebulaCoord += uCameraPos.xz * 0.0001;

        float nebulaNoise = 0.0;
        float nebulaColorMixer = 0.0;
        sampleNebulaNoise(nebulaCoord, nebulaColorMixer, nebulaNoise);
        nebulaColorMixer = pow3(nebulaColorMixer) * 4.5;

        float nebulaVisibility = 1.0;
        if (blackHoleEnabled) {
            nebulaVisibility = (0.175 - pow3(VoS) * 0.175) + pow20(VoS) * 0.425;
        }

        vec3 nebula = fmix(END_NEBULA_COL_FIRST, END_NEBULA_COL_SECOND, nebulaColorMixer) * nebulaNoise * nebulaNoise * nebulaVisibility;
        if (blackHoleEnabled) {
            nebula *= 1.0 + blackHoleColor * pow24(VoS) * 0.25;
            nebula *= max(1.0 - pow32(VoS), 0.0);
        }
        nebula *= length(nebula) * uNebulaBrightness;

        color += nebula;
    }

    if (uFlashes > 0.5 && uFlashIntensity > 0.0) {
        vec4 supernova = getSupernovaAtPos(FlashData.xyz, worldPos);

        vec3 supernovaNebula = fmix(normalize(END_FLASH_COL), normalize(vec3(1.0, 1.8, 3.2)), supernova.y) * 4.0 * supernova.x * supernova.x * supernova.z;
        supernovaNebula *= length(supernovaNebula);
        color += pow32(supernova.a * supernova.a) * END_LIGHT_COL_SQRT * uFlashIntensity * 4.0;
        color += supernovaNebula * uFlashIntensitySqrt * uFlashBrightness;
    }

    if (blackHoleEnabled) {
        float photonRing = pow2(holeSq * 3.0);
        photonRing *= float(photonRing > 0.2) * (1.0 - 6.0 * holeSq) * 64.0;
        photonRing = max(photonRing, 0.0);
        float holeClamped = clamp(holeSq * 8.0, 0.0, 1.0);

        float torus = 1.0 - clamp(length(blackHoleCoord), 0.0, 1.0);
        torus = pow(pow(torus * torus, 1.0 + (180.0 - abs(SunData.w)) / 8.0 * (0.5 + 0.5 * sqrtabsVoU)), sqrt(blackHoleSize) * 1.5);

        vec2 noiseCoord = blackHoleCoord - holeClamped * holeClamped;
        noiseCoord = rotate2D(noiseCoord, PI);
        noiseCoord -= vec2(uTime * 0.025, 0.0);
        noiseCoord.y *= 0.33;
        noiseCoord *= 2.0;

        float blackHoleNoise = texture(NoiseTex, noiseCoord).r;

        color += fmix(blackHoleColor, vec3(4.0 + holeClamped * holeClamped * 2.0), holeClamped * holeClamped) * holeClamped * holeClamped * 3.0 * blackHoleNoise;
        color *= 1.0 - holeClamped;
        color += vec3(photonRing);
        color += fmix(blackHoleColor, vec3(2.0 + torus * 6.0), pow(torus, 0.33)) * torus * pow2(1.0 - torus * 0.65) * blackHoleNoise * 3.0;
    }
}

float getStarNoise(vec2 pos) {
    return fract(sin(dot(pos, vec2(12.9898, 4.1414))) * 43758.5453);
}

void drawStars(inout vec3 color, vec3 worldPos, float VoU, float VoL) {
    float visibility = 1.0;

    if (visibility > 0.05) {
        vec2 planeCoord = worldPos.xz / (length(worldPos.y) + length(worldPos.xyz));
        planeCoord *= 0.8 / STAR_SIZE;
        if (uBlackHole > 0.5) {
            float baseRing = pow10(pow32(VoL));

            planeCoord *= clamp(1.0 - baseRing * 4.0, 0.0, 1.0);
            planeCoord += baseRing;
        }
        planeCoord += uCameraPos.xz * 0.00001;
        planeCoord += uTime * 0.001;

        vec2 planeCoord0 = floor(planeCoord * 500.0 * STAR_AMOUNT) / (500.0 * STAR_AMOUNT);
        vec2 planeCoord1 = floor(planeCoord * 1000.0 * STAR_AMOUNT) / (1000.0 * STAR_AMOUNT);

        float starNoise = getStarNoise(planeCoord0 + 8.0);
        starNoise *= getStarNoise(planeCoord1 + 14.0);

        float stars = clamp(starNoise - 0.825, 0.0, 1.0);
        stars *= stars * stars * 512.0;
        stars = clamp(stars, 0.0, 16.0);

        if (uBlackHole > 0.5) {
            float hole = pow(pow32(VoL), uBlackHoleSize);
            stars *= 1.0 - hole * hole;
        }

        color = fmix(color, color * (4.0 + pow4(stars)) * visibility * STAR_BRIGHTNESS * uStarBrightness, min(1.0, stars));
    }
}

float getProtoplanetaryDisk(vec2 coord) {
    float whirl = -5.0;
    float arms = 5.0;

    if (dot(coord, coord) < 0.0000001) {
        return 0.0;
    }

    coord = vec2(atan(coord.y, coord.x) + uTime * 0.01, sqrt(coord.x * coord.x + coord.y * coord.y));
    float center = pow4(1.0 - coord.y) * 1.0;
    float spiral = sin((coord.x + sqrt(coord.y) * whirl) * arms) + center - coord.y;

    return spiral;
}

void getEndCloudSample(vec2 rayPos, vec2 wind, float attenuation, inout float noise) {
    rayPos *= 0.00025;

    float worleyNoise = (1.0 - texture(NoiseTex, rayPos.xy + wind * 0.5).g) * 0.5 + 0.25;
    float perlinNoise = texture(NoiseTex, rayPos.xy + wind * 0.5).r;
    float noiseBase = perlinNoise * 0.5 + worleyNoise * 0.5;

    float detailZ = floor(attenuation * END_DISK_THICKNESS) * 0.05;
    float noiseDetailA = texture(NoiseTex, rayPos - wind + detailZ).b;
    float noiseDetailB = texture(NoiseTex, rayPos - wind + detailZ + 0.05).b;
    float noiseDetail = mix(noiseDetailA, noiseDetailB, fract(attenuation * END_DISK_THICKNESS));

    float noiseCoverage = abs(attenuation - 0.125) * (attenuation > 0.125 ? 1.14 : 7.0);
    noiseCoverage *= noiseCoverage * 7.0;

    noise = mix(noiseBase, noiseDetail, 0.025 * float(0.0 < noiseBase)) * 22.0 - noiseCoverage;
    noise = max(noise - END_DISK_AMOUNT - 1.0 + getProtoplanetaryDisk(rayPos) * 2.0, 0.0);
    noise /= sqrt(noise * noise + 0.25);
}

void drawEndDisk(inout vec3 color, vec3 worldDir, vec3 atmosphereColor, float dither) {
    if (uDisk < 0.5) {
        return;
    }

    vec3 rayDir = normalize(worldDir);
    if (abs(rayDir.y) < 0.00001) {
        return;
    }

    float lowerPlane = (END_DISK_HEIGHT - uCameraPos.y) / rayDir.y;
    float upperPlane = (END_DISK_TOP - uCameraPos.y) / rayDir.y;
    float minDist = max(min(lowerPlane, upperPlane), 0.0);
    float maxDist = max(lowerPlane, upperPlane);

    if (maxDist <= 0.0) {
        return;
    }

    float planeDifference = maxDist - minDist;
    float rayLength = 72.0 / (rayDir.y * rayDir.y * 6.0 + 1.0);
    vec3 startPos = uCameraPos + minDist * rayDir;
    vec3 sampleStep = rayDir * rayLength;
    int sampleCount = int(min(planeDifference / rayLength, 32.0) + dither);

    if (sampleCount <= 0) {
        return;
    }

    float VoS = clamp(dot(rayDir, normalize(SunData.xyz)), 0.0, 1.0);
    float halfVoLSqrt = VoS * 0.5 + 0.5;
    float scattering = pow8(halfVoLSqrt);

    vec3 worldLightVec = normalize(SunData.xyz);
    worldLightVec.xz *= 32.0;

    vec3 rayPos = startPos + sampleStep * dither;
    float cloud = 0.0;
    float cloudAlpha = 0.0;
    float cloudLighting = 0.0;
    vec2 wind = vec2(uTime * 0.005, sin(uTime * 0.1) * 0.01) * 0.1;

    for (int i = 0; i < sampleCount; i++) {
        if (0.99 < cloudAlpha) {
            break;
        }

        float attenuation = smoothstep(END_DISK_HEIGHT, END_DISK_TOP, rayPos.y);
        float noise = 0.0;
        float lightingNoise = 0.0;

        getEndCloudSample(rayPos.xz, wind, attenuation, noise);
        getEndCloudSample(rayPos.xz + worldLightVec.xz, wind, attenuation, lightingNoise);

        float powder = 1.0 - 0.925 * exp(-pow(noise, 1.0 + noise * 7.0));
        float directionalScattering = 1.0 - exp(-2.0 * (noise - lightingNoise * 0.9));
        float sampleLighting = clamp((0.125 + attenuation * 0.875) * powder * directionalScattering * 2.0, 0.0, 1.0);

        cloudLighting = fmix(cloudLighting, sampleLighting, noise * (1.0 - cloud * cloud));
        cloud = fmix(cloud, 1.0, noise);

        float rayDistance = length((rayPos - uCameraPos).xz) * 0.1;
        noise *= pow8(1.0 - smoothstep(8.0, 4000.0, rayDistance));
        cloudAlpha = fmix(cloudAlpha, 1.0, noise);

        rayPos += sampleStep;
    }

    vec3 cloudColor = vec3(0.95, 1.0, 0.5) * END_LIGHT_COL;
    cloudColor *= cloudLighting * (1.0 + scattering);

    vec3 diskColor = pow(max(cloudColor, 0.0), vec3(1.0 / 2.2));
    diskColor = mix(diskColor, atmosphereColor, 0.4);
    color = fmix(color, diskColor, clamp(cloudAlpha * END_DISK_OPACITY, 0.0, 1.0));
}

void main() {
#if DEBUG_UBO
    if (texCoord0.x < 0.5) {
        fragColor = vec4(texCoord0, 0.0, 1.0);
    } else {
        fragColor = vec4(Toggles.x, Toggles.y, Toggles.z, 1.0);
    }
    return;
#endif

    uTime = RayParams.z;
    uFlashIntensity = FlashData.w;
    uFlashIntensitySqrt = sqrt(uFlashIntensity);
    uNebula = Toggles.x;
    uStars = Toggles.y;
    uBlackHole = Toggles.z;
    uFlashes = Toggles.w;
    uStarBrightness = Strengths.x;
    uNebulaBrightness = Strengths.y;
    uBlackHoleSize = Strengths.z;
    uFlashBrightness = Strengths.w;
    uDisk = CamData.w;
    uCameraPos = CamData.xyz;
    uAtmosphereColor = END_AMBIENT_COL_SQRT * 0.175;

    vec2 ndc = texCoord0 * 2.0 - 1.0;
    vec3 viewDir = normalize(vec3(ndc.x * RayParams.x, ndc.y * RayParams.y, -1.0));
    vec3 worldDir = normalize(mat3(CamRotInv) * viewDir);

    float VoU = worldDir.y;
    float VoS = clamp(dot(worldDir, normalize(SunData.xyz)), 0.0, 1.0);

    vec3 skyColor = uAtmosphereColor * (1.0 + Bayer8(gl_FragCoord.xy) / 64.0);

    if (uNebula > 0.5 || uBlackHole > 0.5 || uFlashes > 0.5) {
        drawEndNebula(skyColor, worldDir, VoU, VoS);
    }

    if (uStars > 0.5) {
        drawStars(skyColor, worldDir, VoU, VoS);
    }

    drawEndDisk(skyColor, worldDir, uAtmosphereColor, Bayer8(gl_FragCoord.xy));

    fragColor = vec4(skyColor, 1.0);
}
