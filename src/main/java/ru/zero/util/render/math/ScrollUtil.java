package ru.zero.util.render.math;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.Window;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import ru.zero.util.render.core.Renderer2D;

@Environment(EnvType.CLIENT)
public class ScrollUtil {
   public static MinecraftClient mc = MinecraftClient.getInstance();
   private static ScaledResolution sr;
   private static Window mw;
   private float target;
   private float scroll;
   private float max;
   private float speed = 8.0F;
   private boolean enabled;
   float barHeight;

   public static ScaledResolution getScaledResolution() {
      if (sr == null) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.getWindow() != null) {
            sr = new ScaledResolution(client);
         }
      }

      return sr;
   }

   public static Window getWindow() {
      if (mw == null) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null) {
            mw = client.getWindow();
         }
      }

      return mw;
   }

   public ScrollUtil() {
      this.setEnabled(true);
   }

   public void update() {
      this.scroll = this.lerp(this.scroll, this.target, this.speed / 100.0F);
   }

   public void handleScroll(double scrollY) {
      if (this.enabled) {
         float wheel = (float) scrollY * (this.speed * 10.0F);
         float stretch = 0.0F;
         this.target = Math.min(Math.max(this.target + wheel / 2.0F, this.max - stretch), stretch);
      }
   }

   @SuppressWarnings("unchecked")
   public <T extends Number> T lerp(T input, T target, double step) {
      double start = input.doubleValue();
      double end = target.doubleValue();
      double result = start + step * (end - start);
      if (input instanceof Integer) {
         return (T) Integer.valueOf((int) Math.round(result));
      } else if (input instanceof Double) {
         return (T) Double.valueOf(result);
      } else if (input instanceof Float) {
         return (T) Float.valueOf((float) result);
      } else if (input instanceof Long) {
         return (T) Long.valueOf(Math.round(result));
      } else if (input instanceof Short) {
         return (T) Short.valueOf((short) Math.round(result));
      } else if (input instanceof Byte) {
         return (T) Byte.valueOf((byte) Math.round(result));
      } else {
         throw new IllegalArgumentException("Unsupported type: " + input.getClass().getSimpleName());
      }
   }

   public static void enable() {
      GL11.glEnable(3089);
   }

   public static void disable() {
      GL11.glDisable(3089);
   }

   public static void scissor(Window window, double x, double y, double width, double height) {
      if (x + width != x && y + height != y && !(x < 0.0) && !(y + height < 0.0)) {
         double scaleFactor = window.getScaleFactor();
         GL11.glScissor(
               (int) Math.round(x * scaleFactor),
               (int) Math.round((window.getScaledHeight() - (y + height)) * scaleFactor),
               (int) Math.round(width * scaleFactor),
               (int) Math.round(height * scaleFactor));
      }
   }

   public void reset() {
      this.scroll = 0.0F;
      this.target = 0.0F;
   }

   public void setMax(float max, float height) {
      this.max = -max + height;
   }

   public void render(Renderer2D renderer2D, float x, float y, float width, float height, float alpha) {
      if (!(this.getMax() >= 0.0F)) {
         float percentage = this.getMax() != 0.0F ? this.getScroll() / this.getMax() : 0.0F;
         float targetBarHeight = height - this.getMax() / (this.getMax() - height) * height;
         this.barHeight = MathHelper.interpolate(targetBarHeight, this.barHeight, 0.9F);
         boolean allowed = this.barHeight < height && this.barHeight > 0.0F;
         if (allowed) {
            float scrollY = y + height * percentage - this.barHeight * percentage;
            int mainColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1),
                  (int) MathHelper.clamp(255.0F * alpha, 0.0F, 255.0F));
            int mainColor20 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1),
                  (int) MathHelper.clamp(20.0F * alpha, 0.0F, 20.0F));
            renderer2D.rect(x, y, width, height, mainColor20);
            renderer2D.rect(x, scrollY, width, this.barHeight, 1.0F, mainColor);
         }
      }
   }

   public float getTarget() {
      return this.target;
   }

   public void setTarget(float target) {
      this.target = target;
   }

   public float getScroll() {
      return this.scroll;
   }

   public void setScroll(float scroll) {
      this.scroll = scroll;
   }

   public float getMax() {
      return this.max;
   }

   public void setMax(float max) {
      this.max = max;
   }

   public float getSpeed() {
      return this.speed;
   }

   public void setSpeed(float speed) {
      this.speed = speed;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }
}
