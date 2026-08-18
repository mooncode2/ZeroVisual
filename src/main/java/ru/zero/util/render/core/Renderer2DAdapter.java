package ru.zero.util.render.core;

import ru.zero.util.render.text.FontObject;
import ru.zero.util.render.text.TextRenderer;

public class Renderer2DAdapter implements Renderer2DInterface {
    private final Renderer2D renderer;
    
    public Renderer2DAdapter(Renderer2D renderer) {
        this.renderer = renderer;
    }
    
    @Override
    public void rect(float x, float y, float width, float height, float radius, int color) {
        renderer.rect(x, y, width, height, radius, color);
    }
    
    @Override
    public void text(FontObject font, float x, float y, float scale, String text, int color) {
        renderer.text(font, x, y, scale, text, color);
    }
    
    @Override
    public void rectOutline(float x, float y, float width, float height, float radius, float lineWidth, int color) {
        renderer.rectOutline(x, y, width, height, radius, color, lineWidth);
    }
    
    @Override
    public void texture(float x, float y, float width, float height, float u, float v, float textureWidth, float textureHeight, int color) {
        // Implement texture method using available Renderer2D methods
        // This is a placeholder implementation
        renderer.drawRgbaTextureWithUV((int)textureWidth, x, y, width, height, u, v, textureWidth, textureHeight);
    }
    
    @Override
    public TextRenderer.TextMetrics measureText(FontObject font, String text, float scale) {
        return renderer.measureText(font, text, scale);
    }
}