package ru.zero.ui.gui.component.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.module.impl.utils.Optimizer;
import ru.zero.ui.gui.theme.MinecraftTheme;
import ru.zero.util.render.core.Renderer2D;

@Environment(EnvType.CLIENT)
public class GuiRenderBackground extends GuiScreen {
    public static void renderBackground(Renderer2D renderer2D, MatrixStack pose, float mainAlpha) {
        boolean vanillaStyle = GuiScreen.isVanillaStyle();
        int outlineColor = vanillaStyle
            ? Renderer2D.ColorUtil.replAlpha(MinecraftTheme.OUTLINE, 255)
            : Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(20.4F * mainAlpha));
        int backGroundOneColor = vanillaStyle
            ? Renderer2D.ColorUtil.replAlpha(MinecraftTheme.PANEL, (int)(235.0F * mainAlpha))
            : Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), (int)(178.5F * mainAlpha));
        float radius = vanillaStyle ? 0.0F : 6.5F;
        float outlineThickness = vanillaStyle ? 1.0F : 0.5F;
        if (ru.zero.util.render.glass.LiquidGlassRenderer.isEnabled()) {
            renderer2D.rectOutline(GuiScreen.x, GuiScreen.y, GuiScreen.width, GuiScreen.height, radius, outlineColor, outlineThickness);
            ru.zero.util.render.glass.LiquidGlassRenderer.drawGlassPanel(renderer2D, GuiScreen.x, GuiScreen.y, GuiScreen.width, GuiScreen.height, radius, outlineColor, backGroundOneColor, mainAlpha);
            return;
        }
        renderer2D.rectOutline(GuiScreen.x, GuiScreen.y, GuiScreen.width, GuiScreen.height, radius, outlineColor, outlineThickness);
        boolean blurEnabled = GuiScreen.clientBlurSetting.get();
        if (mainAlpha > 0.1 && blurEnabled) {
            float blurRadius = Optimizer.optimizeGuiBlur(45.0F);
            if (mainAlpha < 0.35F) {
                blurRadius = Math.min(blurRadius, 18.0F);
            }

            renderer2D.prepareBlurRegion(GuiScreen.x, GuiScreen.y, GuiScreen.width, GuiScreen.height, blurRadius);
            renderer2D.blurRegion(GuiScreen.x, GuiScreen.y, GuiScreen.width, GuiScreen.height, radius, mainAlpha);
        }

        int fillColor = blurEnabled
            ? backGroundOneColor
            : Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), (int)(228.0F * mainAlpha));
        renderer2D.rect(GuiScreen.x, GuiScreen.y, GuiScreen.width, GuiScreen.height, radius, fillColor);
    }
}
