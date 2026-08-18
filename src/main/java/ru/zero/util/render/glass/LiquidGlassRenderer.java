package ru.zero.util.render.glass;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.util.math.MatrixStack;
import ru.zero.mixin.glass.DrawContextAccessor;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.glass.zero.ZeroGlassApi;
import ru.zero.util.render.glass.zero.ZeroGlassStyle;

/**
 * Liquid Glass — делегирует в полноценную Zero glass систему
 * (refraction + dispersion + fresnel + blur + glare через GLSL шейдер).
 * Рисуется через ZeroGlassApi → GameRendererMixin composite pass.
 */
@Environment(EnvType.CLIENT)
public final class LiquidGlassRenderer {

   // Scratch: framebuffer-space bbox (minX, minY, maxX, maxY) последнего виджета.
   // Заполняется в toZeroGlassCoords и используется для проверки попадания в clip.
   // Рендер однопоточный, поэтому переиспользуем без аллокаций каждый кадр.
   private static final float[] FB_BBOX = new float[4];
   // Кэш ScreenRect для текущего clip (один clip на много панелей внутри контентной области).
   private static int lastClipHash = 0;
   private static ScreenRect lastClipRect;

   private LiquidGlassRenderer() {
   }

   private static final MatrixStack SHARED_MATRIX = new MatrixStack();

   public static boolean isEnabled() {
      return GuiScreen.clientLiquidGlassSetting != null && GuiScreen.clientLiquidGlassSetting.get();
   }

   public static void drawGlass(Renderer2D r2, float x, float y, float w, float h, float radius, float alpha) {
      if (r2 == null || w <= 0.0F || h <= 0.0F || alpha <= 0.0F) {
         return;
      }
      ZeroGlassStyle style = ZeroGlassStyle.create()
            .tint(0x000000, 0.0F)
            .blurRadius(24)
            .refThickness(20.0F)
            .refFactor(1.4F)
            .refDispersion(7.0F)
            .refFresnelFactor(20.0F)
            .noShadow()
            .opacity(alpha);
      float[] g = toZeroGlassCoords(r2, x, y, w, h, radius);
      boolean pushed = pushRendererClipScissor(r2);
      try {
         ZeroGlassApi.drawBlurredRoundedRect(SHARED_MATRIX, g[0], g[1], g[2], g[3], g[4], style);
      } finally {
         if (pushed) {
            popRendererClipScissor();
         }
      }
   }

   public static void drawGlassPanel(Renderer2D r2, float x, float y, float w, float h, float radius,
         int outlineColor, int fillColor) {
      drawGlassPanel(r2, x, y, w, h, radius, outlineColor, fillColor, 1.0F);
   }

   public static void drawGlassPanel(Renderer2D r2, float x, float y, float w, float h, float radius,
         int outlineColor, int fillColor, float alpha) {
      if (r2 == null || w <= 0.0F || h <= 0.0F || alpha <= 0.001F) {
         return;
      }
      if (!isEnabled()) {
         r2.rectOutline(x, y, w, h, radius, outlineColor, 0.1F);
         r2.rect(x, y, w, h, radius, fillColor);
         return;
      }
      ZeroGlassStyle style = ZeroGlassStyle.create()
            .tint(0x000000, 0.0F)
            .blurRadius(18)
            .refThickness(16.0F)
            .refFactor(1.35F)
            .refDispersion(5.0F)
            .refFresnelFactor(15.0F)
            .opacity(alpha);
      float[] g = toZeroGlassCoords(r2, x, y, w, h, radius);
      boolean pushed = pushRendererClipScissor(r2);
      try {
         ZeroGlassApi.drawBlurredRoundedRect(SHARED_MATRIX, g[0], g[1], g[2], g[3], g[4], style);
      } finally {
         if (pushed) {
            popRendererClipScissor();
         }
      }
   }

   // Zero glass composite игнорирует clip-стек Renderer2D (использует scissorStack
   // DrawContext), поэтому панели под клипом GUI "выпирали" за границы контентной области.
   // Транслируем текущий clip Renderer2D (framebuffer px, Y вниз) в scaled-координаты и
   // кладём на scissorStack DrawContext на время отрисовки виджета.
   // Оптимизация: если виджет полностью внутри clip — scissors не нужен (не выпирает),
   // пропускаем push/pop (это убирает аллокации ScreenRect для большинства панелей).
   private static boolean pushRendererClipScissor(Renderer2D r2) {
      int[] clip = r2.getClipRectFramebuffer();
      if (clip == null) {
         return false;
      }
      float minX = FB_BBOX[0];
      float minY = FB_BBOX[1];
      float maxX = FB_BBOX[2];
      float maxY = FB_BBOX[3];
      if (minX >= clip[0] && minY >= clip[1] && maxX <= clip[0] + clip[2] && maxY <= clip[1] + clip[3]) {
         return false;
      }
      DrawContext context = ZeroGlassApi.current();
      if (context == null) {
         return false;
      }
      float guiScale = guiScaleFactor();
      if (guiScale <= 0.0F) {
         return false;
      }
      int sx = Math.round(clip[0] / guiScale);
      int sy = Math.round(clip[1] / guiScale);
      int sw = Math.round(clip[2] / guiScale);
      int sh = Math.round(clip[3] / guiScale);
      if (sw <= 0 || sh <= 0) {
         return false;
      }
      int hash = clip[0] * 31 + clip[1] * 17 + clip[2] * 7 + clip[3];
      ScreenRect rect;
      if (hash == lastClipHash && lastClipRect != null) {
         rect = lastClipRect;
      } else {
         rect = new ScreenRect(sx, sy, sw, sh);
         lastClipRect = rect;
         lastClipHash = hash;
      }
      DrawContext.ScissorStack stack = ((DrawContextAccessor) context).zero$getScissorStack();
      stack.push(rect);
      return true;
   }

   private static void popRendererClipScissor() {
      DrawContext context = ZeroGlassApi.current();
      if (context == null) {
         return;
      }
      ((DrawContextAccessor) context).zero$getScissorStack().pop();
   }

   // Zero glass внутри ZeroGlassUniforms умножает координаты виджета на
   // GUI scale factor (ожидает Minecraft scaled-координаты). Но Zero рисует HUD и GUI
   // в разных пространствах: GUI — в scaled-пространстве через pushScale(guiScale),
   // а HUD — напрямую в framebuffer-пикселях (см. HotBarHUD/WaterMark). Из-за этого
   // glass получал двойное масштабирование на HUD и микро-смещение на GUI.
   // Решение: берём реальную матрицу трансформации Renderer2D (ту, что применяется
   // к самим элементам), переводим прямоугольник в framebuffer-пиксели и делим на
   // тот же getScaleFactor(), что использует ZeroGlassUniforms — в итоге
   // внутренний multiply возвращает точный framebuffer-пиксель, совпадающий с Zero.
   private static float[] toZeroGlassCoords(Renderer2D r2, float x, float y, float w, float h, float radius) {
      float[] m = r2.getTransformStack().current();
      float ax = transformX(m, x, y);
      float ay = transformY(m, x, y);
      float bx = transformX(m, x + w, y);
      float by = transformY(m, x + w, y);
      float cx = transformX(m, x, y + h);
      float cy = transformY(m, x, y + h);
      float ex = transformX(m, x + w, y + h);
      float ey = transformY(m, x + w, y + h);
      float minX = Math.min(Math.min(ax, bx), Math.min(cx, ex));
      float maxX = Math.max(Math.max(ax, bx), Math.max(cx, ex));
      float minY = Math.min(Math.min(ay, by), Math.min(cy, ey));
      float maxY = Math.max(Math.max(ay, by), Math.max(cy, ey));
      FB_BBOX[0] = minX;
      FB_BBOX[1] = minY;
      FB_BBOX[2] = maxX;
      FB_BBOX[3] = maxY;
      float scaleX = (float) Math.sqrt((double) (m[0] * m[0] + m[3] * m[3]));
      float scaleY = (float) Math.sqrt((double) (m[1] * m[1] + m[4] * m[4]));
      float transformScale = (scaleX + scaleY) * 0.5F;
      float guiScale = guiScaleFactor();
      float inv = guiScale > 0.0F ? 1.0F / guiScale : 1.0F;
      return new float[]{
            minX * inv,
            minY * inv,
            (maxX - minX) * inv,
            (maxY - minY) * inv,
            radius * transformScale * inv
      };
   }

   private static float guiScaleFactor() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.getWindow() != null) {
         float s = (float) client.getWindow().getScaleFactor();
         if (s > 0.0F && Float.isFinite(s)) {
            return s;
         }
      }
      return 1.0F;
   }

   private static float transformX(float[] m, float px, float py) {
      return m != null && m.length >= 6 ? m[0] * px + m[1] * py + m[2] : px;
   }

   private static float transformY(float[] m, float px, float py) {
      return m != null && m.length >= 6 ? m[3] * px + m[4] * py + m[5] : py;
   }
}
