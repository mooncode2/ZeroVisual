package ru.zero.ui.gui.component.render;

import java.awt.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.module.impl.utils.Optimizer;
import ru.zero.util.render.core.Renderer2D;

@Environment(EnvType.CLIENT)
public class GuiRenderBackground extends GuiScreen {
   public static void renderBackground(Renderer2D renderer2D, MatrixStack pose, float mainAlpha) {
      boolean vanillaStyle = GuiScreen.isVanillaStyle();
      int outlineColor = vanillaStyle
         ? Renderer2D.ColorUtil.replAlpha(new Color(90, 90, 90).getRGB(), (int)(210.0F * mainAlpha))
         : Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(20.4F * mainAlpha));
      int backGroundOneColor = vanillaStyle
         ? Renderer2D.ColorUtil.replAlpha(new Color(44, 44, 44).getRGB(), (int)(225.0F * mainAlpha))
         : Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), (int)(178.5F * mainAlpha));
      float radius = vanillaStyle ? 1.25F : 6.5F;
      if (ru.zero.util.render.glass.LiquidGlassRenderer.isEnabled() && !vanillaStyle) {
         renderer2D.rectOutline(GuiScreen.x, GuiScreen.y, GuiScreen.width, GuiScreen.height, radius, outlineColor, 0.5F);
         ru.zero.util.render.glass.LiquidGlassRenderer.drawGlassPanel(renderer2D, GuiScreen.x, GuiScreen.y, GuiScreen.width, GuiScreen.height, radius, outlineColor, backGroundOneColor, mainAlpha);
         return;
      }
      renderer2D.rectOutline(GuiScreen.x, GuiScreen.y, GuiScreen.width, GuiScreen.height, radius, outlineColor, vanillaStyle ? 1.0F : 0.5F);
      boolean blurEnabled = GuiScreen.clientBlurSetting.get();
      if (!vanillaStyle && mainAlpha > 0.1 && blurEnabled) {
         float blurRadius = Optimizer.optimizeGuiBlur(45.0F);
         if (mainAlpha < 0.35F) {
            blurRadius = Math.min(blurRadius, 18.0F);
         }

         renderer2D.prepareBlurRegion(GuiScreen.x, GuiScreen.y, GuiScreen.width, GuiScreen.height, blurRadius);
         renderer2D.blurRegion(GuiScreen.x, GuiScreen.y, GuiScreen.width, GuiScreen.height, radius, mainAlpha);
      }

      int fillColor = blurEnabled
         ? backGroundOneColor
         : (vanillaStyle ? backGroundOneColor
            : Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), (int)(228.0F * mainAlpha)));
      renderer2D.rect(GuiScreen.x, GuiScreen.y, GuiScreen.width, GuiScreen.height, radius, fillColor);
   }
}
