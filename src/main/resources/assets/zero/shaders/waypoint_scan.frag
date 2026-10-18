#version 330

in vec2 in_uv;
in vec4 in_color;
out vec4 out_color;

uniform sampler2D depth_texture_in;
uniform mat4 inv_mvp;
uniform float u_time;
uniform float waves[64];

const float CORE_IN = 0.10;
const float CORE_OUT = 0.24;
const float HOT_IN = 0.03;
const float HOT_OUT = 0.11;
const float GLOW_LEN = 1.1;
const float GLOW_STRENGTH = 0.55;
const float TRAIL_STRENGTH = 0.32;
const float HOT_MIX = 0.7;

void main() {
    float rawDepth = texture(depth_texture_in, in_uv).r;
    if (rawDepth >= 0.99999) {
        discard;
    }

    vec4 clip = vec4(in_uv * 2.0 - 1.0, rawDepth * 2.0 - 1.0, 1.0);
    vec4 wp = inv_mvp * clip;
    vec3 pos = wp.xyz / wp.w;

    vec3 total = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        int base = i * 8;
        float fade = waves[base + 4];
        if (fade <= 0.001) {
            continue;
        }

        vec3 center = vec3(waves[base], waves[base + 1], waves[base + 2]);
        float radius = waves[base + 3];
        vec3 rel = pos - center;
        float d3 = length(rel);
        float az = atan(rel.z, rel.x);
        float el = atan(rel.y, max(length(rel.xz), 1e-4));
        float amp = 0.4 + 0.03 * radius;
        float wob = (0.30 * sin(az * 6.0 + u_time * 2.6)
                + 0.14 * sin(az * 11.0 - u_time * 3.4)
                + 0.10 * sin(el * 7.0 + u_time * 2.0)) * amp;
        float d = d3 - (radius + wob);
        float dist = abs(d);

        float core = 1.0 - smoothstep(CORE_IN, CORE_OUT, dist);
        float hot = 1.0 - smoothstep(HOT_IN, HOT_OUT, dist);
        float glow = exp(-dist / GLOW_LEN) * GLOW_STRENGTH;
        float trail = 0.0;
        float behind = -d - CORE_OUT;
        if (behind > 0.0) {
            trail = (1.0 - clamp(behind / (0.4 * radius + 1.0), 0.0, 1.0)) * TRAIL_STRENGTH;
        }

        vec3 waveColor = vec3(waves[base + 5], waves[base + 6], waves[base + 7]);
        vec3 hotColor = mix(waveColor, vec3(1.0), HOT_MIX);
        total += fade * (waveColor * (core + glow + trail) + hotColor * hot);
    }

    if (dot(total, total) < 1e-6) {
        discard;
    }
    out_color = vec4(total, 1.0) * in_color;
}