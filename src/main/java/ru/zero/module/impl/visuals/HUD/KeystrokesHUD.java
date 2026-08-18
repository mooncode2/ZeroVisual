package ru.zero.module.impl.visuals.HUD;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import ru.zero.module.impl.utils.Optimizer;
import ru.zero.ui.draggable.DraggableManager;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.keyboard.ScaledResolution;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
public final class KeystrokesHUD {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final float GAP = 3.0F;
   private static final float RADIUS = 8.0F;

   private KeystrokesHUD() {
   }

   public static void render(
      Renderer2D renderer,
      boolean blurEnabled,
      float blurRadius,
      boolean showCps,
      float keySize,
      boolean showShadow,
      float panelAlpha,
      double wPress,
      double aPress,
      double sPress,
      double dPress,
      double spacePress,
      double lmbPress,
      double rmbPress,
      int leftCps
   ) {
      if (panelAlpha <= 0.001F) {
         return;
      }

      float rowW = keySize * 3.0F + GAP * 2.0F;
      float spaceH = keySize * 0.68F;
      float mouseH = keySize * 0.88F;
      float cpsH = keySize * 0.76F;
      float mouseW = (rowW - GAP) * 0.5F;
      float totalHeight = keySize + GAP + keySize + GAP + spaceH + GAP + mouseH + (showCps ? GAP + cpsH : 0.0F);
      float totalWidth = rowW;

      ScaledResolution sr = new ScaledResolution(mc);
      float preferredX = sr.getWidth() - totalWidth - 12.0F;
      float preferredY = sr.getHeight() / 2.0F - totalHeight / 2.0F;
      DraggableManager.DragSession session = DraggableManager.getInstance().beginDrag("keystrokes", preferredX, preferredY, totalWidth, totalHeight);
      float x = session.positionX();
      float y = session.positionY();
      float alpha = panelAlpha;
      HudEditor.registerRect(x, y, totalWidth, totalHeight);

      float wX = x + (rowW - keySize) * 0.5F;
      float wY = y;
      float aX = x;
      float aY = y + keySize + GAP;
      float sX = x + keySize + GAP;
      float dX = x + (keySize + GAP) * 2.0F;
      float spaceY = aY + keySize + GAP;
      float mouseY = spaceY + spaceH + GAP;
      float lmbX = x;
      float rmbX = x + mouseW + GAP;
      float cpsY = mouseY + mouseH + GAP;

      drawKey(renderer, wX, wY, keySize, keySize, "W", wPress, blurEnabled, blurRadius, showShadow, alpha, false);
      drawKey(renderer, aX, aY, keySize, keySize, "A", aPress, blurEnabled, blurRadius, showShadow, alpha, false);
      drawKey(renderer, sX, aY, keySize, keySize, "S", sPress, blurEnabled, blurRadius, showShadow, alpha, false);
      drawKey(renderer, dX, aY, keySize, keySize, "D", dPress, blurEnabled, blurRadius, showShadow, alpha, false);
      drawKey(renderer, x, spaceY, rowW, spaceH, null, spacePress, blurEnabled, blurRadius, showShadow, alpha, true);
      drawKey(renderer, lmbX, mouseY, mouseW, mouseH, "LMB", lmbPress, blurEnabled, blurRadius, showShadow, alpha, false);
      drawKey(renderer, rmbX, mouseY, mouseW, mouseH, "RMB", rmbPress, blurEnabled, blurRadius, showShadow, alpha, false);

      if (showCps) {
         String cpsText = leftCps + " CPS";
         drawKey(renderer, x, cpsY, rowW, cpsH, cpsText, 0.0, blurEnabled, blurRadius, showShadow, alpha, false);
      }

      DraggableManager.getInstance().endDrag(session);
   }

   private static void drawKey(
      Renderer2D renderer,
      float x,
      float y,
      float width,
      float height,
      String label,
      double pressAnim,
      boolean blurEnabled,
      float blurRadius,
      boolean showShadow,
      float panelAlpha,
      boolean spaceBar
   ) {
      float press = (float)Math.max(0.0, Math.min(1.0, pressAnim));
      float scale = 1.0F - press * 0.04F;
      float centerX = x + width * 0.5F;
      float centerY = y + height * 0.5F;
      renderer.pushTranslation(centerX, centerY);
      renderer.pushScale(scale, scale, 0.0F);
      renderer.pushTranslation(-centerX, -centerY);

      drawKeyBackground(renderer, x, y, width, height, panelAlpha, press, blurEnabled, blurRadius, showShadow);

      if (label != null && !label.isEmpty()) {
         float fontSize = resolveFontSize(label, height);
         int textColor = ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), panelAlpha);
         int accentColor = ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), panelAlpha);
         float pressMix = label.length() == 1 ? 0.7F : 0.65F;
         int color = ColorUtil.overCol(textColor, accentColor, press * pressMix);
         drawCenteredLabel(renderer, label, centerX, centerY, fontSize, color);
      } else if (spaceBar) {
         float lineW = width * 0.5F;
         float lineH = Math.max(1.5F, height * 0.1F);
         renderer.rect(
            centerX - lineW * 0.5F,
            centerY - lineH * 0.5F,
            lineW,
            lineH,
            lineH * 0.5F,
            ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), panelAlpha * (0.45F + press * 0.55F))
         );
      }

      renderer.popTransform();
      renderer.popScale();
      renderer.popTransform();
   }

   private static void drawKeyBackground(
      Renderer2D renderer,
      float x,
      float y,
      float width,
      float height,
      float alpha,
      float press,
      boolean blurEnabled,
      float blurRadius,
      boolean showShadow
   ) {
      if (showShadow && Optimizer.shouldRenderHudShadow()) {
         renderer.shadow(x, y, width, height, RADIUS, 6.0F, 0.35F, ColorUtil.getColor(0, alpha * (0.08F + press * 0.1F)));
      }

      int pressedFill = ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), alpha * press * 0.22F);

      if (ru.zero.util.render.glass.LiquidGlassRenderer.isEnabled()) {
         ru.zero.util.render.glass.LiquidGlassRenderer.drawGlass(renderer, x, y, width, height, RADIUS, alpha);
         if (press > 0.01F) {
            renderer.rect(x, y, width, height, RADIUS, pressedFill);
         }
         return;
      }

       boolean renderBlur = blurEnabled;
       if (renderBlur) {
          float optimizedBlur = Optimizer.optimizeHudBlur(blurRadius);
          renderer.prepareBlur(optimizedBlur);
          renderer.blur(x, y, width, height, RADIUS, alpha);
       }

       int outline = ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), alpha * (0.08F + press * 0.14F));
       // Без блюра клавиши должны оставаться видимыми на ярком фоне, поэтому фон
       // делаем плотнее, а с блюром — полупрозрачным, чтобы размытие просвечивало.
       float baseOpacity = renderBlur ? 0.62F : 0.9F;
       int background = ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), alpha * (baseOpacity + press * 0.08F));

       renderer.rectOutline(x - 0.5F, y - 0.5F, width + 1.0F, height + 1.0F, RADIUS, outline, 0.75F);
       renderer.rect(x, y, width, height, RADIUS, background);
      if (press > 0.01F) {
         renderer.rect(x, y, width, height, RADIUS, pressedFill);
      }
   }

   private static float resolveFontSize(String label, float height) {
      if (label.length() == 1) {
         return Math.min(40.0F, Math.max(30.0F, height * 1.1F));
      }

      return Math.min(34.0F, Math.max(26.0F, height * 1.0F));
   }

   private static void drawCenteredLabel(Renderer2D renderer, String label, float centerX, float centerY, float fontSize, int color) {
      int codepoint = label.codePointAt(0);
      float renderSize = fontSize * 0.5F;
      float baselineOffset = FontRegistry.centeredBaselineOffset(FontRegistry.TBANKSANS, codepoint, renderSize);
      float baselineY = centerY + baselineOffset;
      renderer.text(FontRegistry.TBANKSANS, centerX, baselineY, fontSize, label, color, "c");
   }
}
