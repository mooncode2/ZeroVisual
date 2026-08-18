package ru.zero.util.render.core;

import ru.zero.util.render.text.FontObject;
import ru.zero.util.render.text.TextRenderer;

public interface Renderer2DInterface {
    void rect(float x, float y, float width, float height, float radius, int color);
    void text(FontObject font, float x, float y, float scale, String text, int color);
    void rectOutline(float x, float y, float width, float height, float radius, float lineWidth, int color);
    void texture(float x, float y, float width, float height, float u, float v, float textureWidth, float textureHeight, int color);
    TextRenderer.TextMetrics measureText(FontObject font, String text, float scale);
}