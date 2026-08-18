#version 150

uniform sampler2D DiffuseSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Params.xy = direction (1,0)/(0,1), Params.z = radius (texels).
// Weights[i] хранит нормализованный вес гауссианы для смещения i.
layout(std140) uniform Config {
    vec4 Params;
    float Weights[193];
};

in vec2 texCoord;
out vec4 fragColor;

// Линейно-семплированный гауссов blur: за одну билинейную выборку GPU складывает
// два соседних текселя с аппаратным весом, поэтому число texture() вызовов ~вдвое
// меньше при идентичном результате. Центр берём отдельно, пары (2i-1, 2i) объединяем
// в одну выборку между ними.
void main() {
    int radius = int(Params.z + 0.5);
    radius = clamp(radius, 0, 192);

    vec2 delta = Params.xy / OutSize;

    vec4 sum = texture(DiffuseSampler, texCoord) * Weights[0];

    // Обрабатываем пары смещений (2i-1, 2i). Для каждой пары вес = w1+w2,
    // а точка выборки смещена в их взвешенный центр — билинейная фильтрация
    // возвращает w1*t1 + w2*t2 одним чтением.
    for (int i = 1; i <= 96; ++i) {
        int i1 = 2 * i - 1;
        int i2 = 2 * i;
        if (i1 > radius) break;

        float w1 = Weights[i1];
        float w2 = (i2 <= radius) ? Weights[i2] : 0.0;
        float w = w1 + w2;
        if (w <= 0.0) continue;

        float offset = (float(i1) * w1 + float(i2) * w2) / w;
        vec2 offs = delta * offset;
        sum += texture(DiffuseSampler, texCoord + offs) * w;
        sum += texture(DiffuseSampler, texCoord - offs) * w;
    }

    fragColor = sum;
}
