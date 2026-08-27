else if (material2 == 298) {//Raw metal ores
    smoothness = min(0.70, lAlbedo3 * lAlbedo3 * 0.22 + 0.03);
    metalness = max(metalness, 0.55);
}