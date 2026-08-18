package ru.zero.util.render.glass.zero;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2f;
import ru.zero.mixin.glass.DrawContextAccessor;

public final class ZeroGlassApi {
   private static final ThreadLocal<DrawContext> CURRENT = new ThreadLocal<>();

   private ZeroGlassApi() {
   }

   public static void begin(DrawContext context) {
      CURRENT.set(context);
   }

   public static void end() {
      CURRENT.remove();
   }

   public static DrawContext current() {
      return CURRENT.get();
   }

   public static boolean drawBlurredRoundedRect(MatrixStack matrices, float x, float y, float width, float height, float radius, ZeroGlassStyle style) {
      DrawContext context = current();
      if (context != null && !(width <= 0.0F) && !(height <= 0.0F)) {
         ScreenRect scissor = ((DrawContextAccessor)context).zero$getScissorStack().peekLast();

         int rx = Math.round(x);
         int ry = Math.round(y);
         float clampedRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
         ZeroGlassElementRenderState element = new ZeroGlassElementRenderState(rx, ry, rx + Math.round(width), ry + Math.round(height), clampedRadius, style, new Matrix3x2f(context.getMatrices()), scissor, 0.0F, 0.0F);
         ZeroGlassUniforms.get().addWidget(element);
         ((DrawContextAccessor)context).zero$getState().addSpecialElement(element);
         ZeroGlassUniforms.get().tryApplyBlur(context);
         return true;
      } else {
         return false;
      }
   }
}
