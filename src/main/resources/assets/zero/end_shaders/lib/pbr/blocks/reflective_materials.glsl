else if (material2 == 301) {// Iron, Gold, Emerald, Diamond, Copper & Plates
    smoothness = min(0.88, 0.08 + lAlbedo3 * 0.55);
    metalness = max(metalness, 0.75);
} else if (material2 == 299) {
    smoothness = pow3(lAlbedo) * 0.65;
    subsurface = 0.6;
}