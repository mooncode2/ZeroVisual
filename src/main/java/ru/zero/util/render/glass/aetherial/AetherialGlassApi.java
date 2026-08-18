package ru.zero.util.render.glass.aetherial;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2f;
import ru.zero.mixin.glass.DrawContextAccessor;

public final class AetherialGlassApi {
   private static final ThreadLocal<DrawContext> CURRENT = new ThreadLocal<>();
   private static final ThreadLocal<ScreenRect> CLIP = new ThreadLocal<>();

   private AetherialGlassApi() {
   }

   public static void begin(DrawContext context) {
      CURRENT.set(context);
   }

   public static void end() {
      CURRENT.remove();
      CLIP.remove();
   }

   public static DrawContext current() {
      return CURRENT.get();
   }

   public static void setClip(ScreenRect rect) {
      CLIP.set(rect);
   }

   public static void clearClip() {
      CLIP.remove();
   }

   public static boolean drawBlurredRoundedRect(MatrixStack matrices, float x, float y, float width, float height, float radius, AetherialGlassStyle style) {
      DrawContext context = current();
      if (context != null && !(width <= 0.0F) && !(height <= 0.0F)) {
         ScreenRect clip = CLIP.get();
         ScreenRect scissor;
         if (clip != null) {
            scissor = clip;
         } else {
            scissor = ((DrawContextAccessor)context).zero$getScissorStack().peekLast();
         }

         int rx = Math.round(x);
         int ry = Math.round(y);
         float clampedRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
         AetherialGlassElementRenderState element = new AetherialGlassElementRenderState(rx, ry, rx + Math.round(width), ry + Math.round(height), clampedRadius, style, new Matrix3x2f(context.getMatrices()), scissor, 0.0F, 0.0F);
         AetherialGlassUniforms.get().addWidget(element);
         ((DrawContextAccessor)context).zero$getState().addSpecialElement(element);
         return true;
      } else {
         return false;
      }
   }
}
