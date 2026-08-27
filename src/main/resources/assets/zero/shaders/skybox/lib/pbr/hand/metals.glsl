float generatedMetalPixel = 1.0;
float generatedWoodHandle = float(albedo.r > albedo.g * 1.08 && albedo.g > albedo.b * 1.18 && albedo.r < 0.62 && albedo.g < 0.48 && albedo.b < 0.32);
generatedMetalPixel *= 1.0 - generatedWoodHandle;

if (currentRenderedItemId == 1502) { // Iron tools, armor and ingots
    smoothness = max(smoothness, 0.62 * generatedMetalPixel);
} else if (currentRenderedItemId == 1503) { // Gold tools, armor and ingots
    smoothness = max(smoothness, (pow24(lAlbedo) * pow4(lAlbedo)) * 0.78 * generatedMetalPixel);
} else if (currentRenderedItemId == 1504) { // Diamond / emerald tools, armor and gems
    smoothness = max(smoothness, pow20(lAlbedo) * 0.82 * generatedMetalPixel);
} else if (currentRenderedItemId == 1506) { // Copper tools, armor and ingots
    smoothness = max(smoothness, 0.58 * generatedMetalPixel);
} else if (currentRenderedItemId == 1507) { // Iron blocks and utility metals
    smoothness = max(smoothness, min(0.78, 0.10 + lAlbedo * 0.50) * generatedMetalPixel);
} else if (currentRenderedItemId == 1508) { // Gold blocks and gold-like metal items
    smoothness = max(smoothness, min(0.88, 0.16 + lAlbedo * 0.56) * generatedMetalPixel);
} else if (currentRenderedItemId == 1509) { // Diamond and emerald blocks
    smoothness = max(smoothness, min(0.84, 0.12 + lAlbedo * 0.54) * generatedMetalPixel);
} else if (currentRenderedItemId == 1510) { // Copper blocks
    smoothness = max(smoothness, min(0.86, 0.08 + lAlbedo * 0.58) * generatedMetalPixel);
} else if (currentRenderedItemId == 1511) { // Raw metal blocks
    smoothness = max(smoothness, min(0.62, 0.05 + lAlbedo * 0.30) * generatedMetalPixel);
} else if (currentRenderedItemId == 1512) { // Netherite
    smoothness = max(smoothness, min(0.72, 0.12 + lAlbedo * 0.38) * generatedMetalPixel);
}
