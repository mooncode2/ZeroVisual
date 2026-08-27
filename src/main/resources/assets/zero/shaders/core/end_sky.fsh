#version 330
// Zero SkyBox - End sky fragment shader
// Ported from Solas by Septonious (End atmosphere: nebula, stars, black hole, flashes)
// Source: shaders/lib/atmosphere/endNebula.glsl + stars.glsl + programs/deferred1.glsl (END)

layout(std140) uniform SkyConfig {
    mat4 CamRotInv;    // view -> world rotation
    vec4 RayParams;    // x: tanHalfFovX, y: tanHalfFovY, z: frameTimeCounter
    vec4 FlashData;    // xyz: flash dir (world), w: endFlashIntensity
    vec4 CamData;      // x: cameraPosition.x, y: cameraPosition.z
    vec4 SunData;      // xyz: sun direction (world), w: sunPathRotation
    vec4 Toggles;      // x: nebula, y: stars, z: blackHole, w: flashes
    vec4 Strengths;    // x: starBrightness, y: nebulaBrightness, z: blackHoleSize, w: flashBrightness
};

uniform sampler2D NoiseTex;

in vec2 texCoord0;

out vec4 fragColor;

// Runtime uniforms unpacked from SkyConfig
float uTime;
float uFlashIntensity;
float uFlashIntensitySqrt;
float uNebula;
float uStars;
float uBlackHole;
float uFlashes;
float uStarBrightness;
float uNebulaBrightness;
float uBlackHoleSize;
float uFlashBrightness;

const float PI = 3.14;
const float END_ANGLE = 0.0;

// End colors (Solas defaults from lib/common.glsl)
const vec3 endLightColSqrt = vec3(195.0, 170.0, 165.0) / 255.0 * 1.45;
const vec3 endLightCol = endLightColSqrt * endLightColSqrt;
const vec3 endAmbientColSqrt = vec3(225.0, 205.0, 195.0) / 255.0 * 0.25;
const vec3 endFlashCol = (vec3(255.0, 140.0, 185.0) / 255.0) * (vec3(255.0, 140.0, 185.0) / 255.0);
const vec3 endNebulaColFirst = (vec3(208.0, 132.0, 44.0) / 255.0 * 2.60) * (vec3(208.0, 132.0, 44.0) / 255.0 * 2.60);
const vec3 endNebulaColSecond = (vec3(32.0, 244.0, 184.0) / 255.0 * 1.60) * (vec3(32.0, 244.0, 184.0) / 255.0 * 1.60);

float fmix(float a, float b, float t) {
    t = min(max(t, 0.0), 1.0);
    return a + t * (b - a);
}

vec3 fmix(vec3 a, vec3 b, vec3 t) {
    t = min(max(t, 0.0), 1.0);
    return a + t * (b - a);
}

vec3 fmix(vec3 a, vec3 b, float t) {
    t = min(max(t, 0.0), 1.0);
    return a + t * (b - a);
}

float pow2(float x) {return x*x;}
float pow3(float x) {return x*x*x;}
float pow4(float x) {return x*x*x*x;}
float pow6(float x) {return x*x*x*x*x*x;}
float pow8(float x) {return x*x*x*x*x*x*x*x;}
float pow10(float x) {return x*x*x*x*x*x*x*x*x*x;}
float pow20(float x) {return x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x;}
float pow24(float x) {return x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x;}
float pow32(float x) {return x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x*x;}

vec2 pow2(vec2 x) {return x*x;}

//**//**//**//**//**// End Nebula (lib/atmosphere/endNebula.glsl) //**//**//**//**//**//

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

    coord = vec2(atan(coord.y, coord.x) + uTime * 0.1, sqrt(coord.x * coord.x + coord.y * coord.y));
    float center = pow8(1.0 - coord.y) * 24.0;
    float spiral = sin((coord.x + sqrt(coord.y) * whirl) * arms) + center - coord.y;

    return clamp(spiral * 0.025, 0.0, 1.0);
}

vec4 getSupernovaAtPos(in vec3 flashPos, in vec3 worldPos) {
    vec2 flashCoord = flashPos.xz / (flashPos.y + length(flashPos));
    vec2 blackHoleCoord = worldPos.xz / (length(worldPos) + worldPos.y) - flashCoord;

    float nebulaNoise = 0.0;
    float nebulaColorMixer = 0.0;
    sampleNebulaNoise(blackHoleCoord, nebulaColorMixer, nebulaNoise);
          nebulaColorMixer = pow4(nebulaColorMixer) * 6.0;

    float endFlashPoint = 1.0 - clamp(length(blackHoleCoord), 0.0, 1.0);
    float animation = uFlashIntensitySqrt * 17.0;
    float visibility = pow(endFlashPoint, 20.0 - animation) * max(1.0 - (1.0 + uFlashIntensity) * pow(endFlashPoint, 24.0 - animation), 0.0);

    return vec4(nebulaNoise, nebulaColorMixer, visibility, endFlashPoint);
}

vec2 rotate2D(vec2 p, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return mat2(c, -s,
                s,  c) * p;
}

void drawEndNebula(inout vec3 color, in vec3 worldPos, in float VoU, in float VoS) {
    bool blackHoleEnabled = uBlackHole > 0.5;

    //Prepare black hole parameters for warping the nebula
    vec3 blackHoleColor = vec3(5.6, 3.2, 0.7) * endLightCol;
    float absVoU = abs(VoU);
    float sqrtabsVoU = sqrt(absVoU);
    float blackHoleSize = uBlackHoleSize;
    float hole = pow(pow4(pow32(VoS)), blackHoleSize);
    float gravityLens = hole;
    float holeSq = hole;
         holeSq *= holeSq;
         holeSq *= holeSq;
    if (!blackHoleEnabled) {
        gravityLens = 0.0;
        holeSq = 0.0;
    }

    vec3 wSunVec = normalize(SunData.xyz);
    vec2 sunCoord = wSunVec.xz / (wSunVec.y + length(wSunVec));
    vec2 blackHoleCoord = worldPos.xz / (length(worldPos) + worldPos.y) - sunCoord;
         blackHoleCoord.y -= blackHoleCoord.x * END_ANGLE;
    float warping = getSpiralWarping(blackHoleCoord);
         blackHoleCoord.x *= 0.75 - absVoU * 0.25;
         blackHoleCoord.y *= 5.0;
         blackHoleCoord.y += pow2(blackHoleCoord.x * 2.25) * sqrtabsVoU;
    if (!blackHoleEnabled) {
        warping = 0.0;
    }

    //Ender Nebula
    if (uNebula > 0.5) {
        vec2 nebulaCoord = worldPos.xz / (length(worldPos.y) + length(worldPos.xyz));
             nebulaCoord += warping * gravityLens;
             nebulaCoord += vec2(CamData.x, CamData.y) * 0.0001;

        float nebulaNoise = 0.0;
        float nebulaColorMixer = 0.0;
        sampleNebulaNoise(nebulaCoord, nebulaColorMixer, nebulaNoise);
              nebulaColorMixer = pow3(nebulaColorMixer) * 4.5;

        float nebulaVisibility = 1.0;
        if (blackHoleEnabled) {
              nebulaVisibility = (0.175 - pow3(VoS) * 0.175) + pow20(VoS) * 0.425;
        }

        vec3 nebula = fmix(endNebulaColFirst,
                          endNebulaColSecond,
                          nebulaColorMixer) * nebulaNoise * nebulaNoise * nebulaVisibility;
        if (blackHoleEnabled) {
             nebula *= 1.0 + blackHoleColor * pow24(VoS) * 0.25;
             nebula *= max(1.0 - pow32(VoS), 0.0);
        }
             nebula *= length(nebula) * uNebulaBrightness;

        color += nebula;
    }

    //Supernova in 1.21+
    if (uFlashes > 0.5 && uFlashIntensity > 0.0) {
        vec4 supernova = getSupernovaAtPos(FlashData.xyz, worldPos);

        vec3 supernovaNebula = fmix(normalize(endFlashCol), normalize(vec3(1.0, 1.8, 3.2)), supernova.y) * 4.0 * supernova.x * supernova.x * supernova.z;
             supernovaNebula *= length(supernovaNebula);
        color += pow32(supernova.a * supernova.a) * endLightColSqrt * uFlashIntensity * 4.0;
        color += supernovaNebula * uFlashIntensitySqrt * uFlashBrightness;
    }

    //Black Hole
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

//**//**//**//**//**// Stars (lib/atmosphere/stars.glsl, END branch) //**//**//**//**//**//

float getStarNoise(vec2 pos) {
    return fract(sin(dot(pos, vec2(12.9898, 4.1414))) * 43758.5453);
}

void drawStars(inout vec3 color, in vec3 worldPos, in float VoU, in float VoL) {
    const float STAR_SIZE = 1.00;
    const float STAR_AMOUNT = 1.60;
    const float STAR_BRIGHTNESS = 1.2;

    float visibility = 1.0;

    if (visibility > 0.05) {
        vec2 planeCoord = worldPos.xz / (length(worldPos.y) + length(worldPos.xyz));
                planeCoord *= 0.8 / STAR_SIZE;
        if (uBlackHole > 0.5) {
            float baseRing = pow10(pow32(VoL));

            planeCoord *= clamp(1.0 - baseRing * 4.0, 0.0, 1.0);
            planeCoord += baseRing;
        }
                planeCoord += vec2(CamData.x, CamData.y) * 0.00001;
                planeCoord += uTime * 0.001;

        float amount = STAR_AMOUNT;

        vec2 planeCoord0 = floor(planeCoord * 500.0 * amount) / (500.0 * amount);
        vec2 planeCoord1 = floor(planeCoord * 1000.0 * amount) / (1000.0 * amount);

        float starNoise = getStarNoise(planeCoord0 + 8.0);
                starNoise*= getStarNoise(planeCoord1 + 14.0);

        float stars = clamp(starNoise - (0.825 - 0.0), 0.0, 1.0);
                stars *= stars * stars * 512.0;
                stars = clamp(stars, 0.0, 16.0);

        if (uBlackHole > 0.5) {
            float hole = pow(pow32(VoL), uBlackHoleSize);

            stars *= 1.0 - hole * hole;
        }

        color = fmix(color, color * (4.0 + pow4(stars)) * visibility * STAR_BRIGHTNESS * uStarBrightness, min(1.0, stars));
    }
}

//**//**//**//**//**// Main //**//**//**//**//**//

void main() {
    //Unpack runtime values
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

    vec2 ndc = texCoord0 * 2.0 - 1.0;
    vec3 viewDir = normalize(vec3(ndc.x * RayParams.x, ndc.y * RayParams.y, -1.0));
    vec3 worldDir = normalize(mat3(CamRotInv) * viewDir);

    float VoU = worldDir.y;
    float VoS = clamp(dot(worldDir, normalize(SunData.xyz)), 0.0, 1.0);

    //Base End atmosphere (deferred1.glsl: endAmbientColSqrt * 0.175)
    vec3 skyColor = endAmbientColSqrt * 0.175;

    if (uNebula > 0.5 || uBlackHole > 0.5 || uFlashes > 0.5) {
        drawEndNebula(skyColor, worldDir, VoU, VoS);
    }

    if (uStars > 0.5) {
        drawStars(skyColor, worldDir, VoU, VoS);
    }

    fragColor = vec4(skyColor, 1.0);
}
