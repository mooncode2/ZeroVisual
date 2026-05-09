package ru.zero.util.color;

import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;
import ru.zero.util.other.Mathf;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.animation.anim2.Interpolator;

@Environment(EnvType.CLIENT)
public final class ColorUtil {
   private static final long CACHE_EXPIRATION_TIME = 60000L;
   private static final ConcurrentHashMap<ColorUtil.ColorKey, ColorUtil.CacheEntry> colorCache = new ConcurrentHashMap<>();
   private static final ScheduledExecutorService cacheCleaner = Executors.newScheduledThreadPool(1);
   private static final DelayQueue<ColorUtil.CacheEntry> cleanupQueue = new DelayQueue<>();
   public static final int RED = getColor(255, 0, 0);
   public static final int GREEN = getColor(0, 255, 0);
   public static final int BLUE = getColor(0, 0, 255);
   public static final int YELLOW = getColor(255, 255, 0);
   public static final int WHITE = getColor(255);
   public static final int BLACK = getColor(0);

   public static int getOverallColorFrom(int color1, int color2, float percentTo2) {
      int finalRed = Mathf.lerp(color1 >> 16 & 0xFF, color2 >> 16 & 0xFF, percentTo2);
      int finalGreen = Mathf.lerp(color1 >> 8 & 0xFF, color2 >> 8 & 0xFF, percentTo2);
      int finalBlue = Mathf.lerp(color1 & 0xFF, color2 & 0xFF, percentTo2);
      int finalAlpha = Mathf.lerp(color1 >> 24 & 0xFF, color2 >> 24 & 0xFF, percentTo2);
      return getColor(finalRed, finalGreen, finalBlue, finalAlpha);
   }

   public static int reAlphaInt(int color, int alpha) {
      return MathHelper.clamp(alpha, 0, 255) << 24 | color & 16777215;
   }

   public static int interpolateInt(int oldValue, int newValue, double interpolationValue) {
      return interpolateD(oldValue, newValue, (float)interpolationValue).intValue();
   }

   public static Double interpolateD(double oldValue, double newValue, double interpolationValue) {
      return oldValue + (newValue - oldValue) * interpolationValue;
   }

   public static int getAlpha(int packedColor) {
      return packedColor >>> 24;
   }

   public static int getRed(int packedColor) {
      return packedColor >> 16 & 0xFF;
   }

   public static int getGreen(int packedColor) {
      return packedColor >> 8 & 0xFF;
   }

   public static int getBlue(int packedColor) {
      return packedColor & 0xFF;
   }

   public static int darker(int color, int factor) {
      int red = getRed(color);
      int green = getGreen(color);
      int blue = getBlue(color);
      red = Math.max(0, red - factor);
      green = Math.max(0, green - factor);
      blue = Math.max(0, blue - factor);
      return 0xFF000000 | red << 16 | green << 8 | blue;
   }

   public static int glColor(int color) {
      float alpha = (color >> 24 & 0xFF) / 255.0F;
      float red = (color >> 16 & 0xFF) / 255.0F;
      float green = (color >> 8 & 0xFF) / 255.0F;
      float blue = (color & 0xFF) / 255.0F;
      GL11.glColor4f(red, green, blue, alpha);
      return color;
   }

   public static int skyRainbow(int speed, int index) {
      double angle = (int)((System.currentTimeMillis() / speed + index) % 360L);
      double var4;
      return Color.getHSBColor((var4 = angle % 360.0) / 360.0 < 0.5 ? -((float)(var4 / 360.0)) : (float)(var4 / 360.0), 0.5F, 1.0F).hashCode();
   }

   public static int fadeBetween(float speed, int offset, int color1, int color2) {
      long time = System.currentTimeMillis() + offset;
      double factor = (Math.sin(time * 0.001 * speed) + 1.0) / 2.0;
      int r1 = color1 >> 16 & 0xFF;
      int g1 = color1 >> 8 & 0xFF;
      int b1 = color1 & 0xFF;
      int r2 = color2 >> 16 & 0xFF;
      int g2 = color2 >> 8 & 0xFF;
      int b2 = color2 & 0xFF;
      int r = (int)(r1 + (r2 - r1) * factor);
      int g = (int)(g1 + (g2 - g1) * factor);
      int b = (int)(b1 + (b2 - b1) * factor);
      return r << 16 | g << 8 | b;
   }

   public static int fadeBetween(int from, int to, float fraction) {
      fraction = clamp01(fraction);
      int a1 = from >> 24 & 0xFF;
      int r1 = from >> 16 & 0xFF;
      int g1 = from >> 8 & 0xFF;
      int b1 = from & 0xFF;
      int a2 = to >> 24 & 0xFF;
      int r2 = to >> 16 & 0xFF;
      int g2 = to >> 8 & 0xFF;
      int b2 = to & 0xFF;
      int a = (int)(a1 + (a2 - a1) * fraction);
      int r = (int)(r1 + (r2 - r1) * fraction);
      int g = (int)(g1 + (g2 - g1) * fraction);
      int b = (int)(b1 + (b2 - b1) * fraction);
      return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
   }

   private static float clamp01(float v) {
      if (v < 0.0F) {
         return 0.0F;
      } else {
         return v > 1.0F ? 1.0F : v;
      }
   }

   public static float[] rgba(int color) {
      return new float[]{(color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, (color >> 24 & 0xFF) / 255.0F};
   }

   public static int[] rgbas(int color) {
      return new int[]{
         (int)((color >> 16 & 0xFF) / 255.0F), (int)((color >> 8 & 0xFF) / 255.0F), (int)((color & 0xFF) / 255.0F), (int)((color >> 24 & 0xFF) / 255.0F)
      };
   }

   public static int red(int c) {
      return c >> 16 & 0xFF;
   }

   public static int green(int c) {
      return c >> 8 & 0xFF;
   }

   public static int blue(int c) {
      return c & 0xFF;
   }

   public static int alpha(int c) {
      return c >> 24 & 0xFF;
   }

   public static float redf(int c) {
      return red(c) / 255.0F;
   }

   public static float greenf(int c) {
      return green(c) / 255.0F;
   }

   public static float bluef(int c) {
      return blue(c) / 255.0F;
   }

   public static float alphaf(int c) {
      return alpha(c) / 255.0F;
   }

   public static int[] getRGBA(int c) {
      return new int[]{red(c), green(c), blue(c), alpha(c)};
   }

   public static int[] getRGB(int c) {
      return new int[]{red(c), green(c), blue(c)};
   }

   public static float[] getRGBAf(int c) {
      return new float[]{redf(c), greenf(c), bluef(c), alphaf(c)};
   }

   public static float[] getRGBf(int c) {
      return new float[]{redf(c), greenf(c), bluef(c)};
   }

   public static int getColor(float red, float green, float blue, float alpha) {
      return getColor(Math.round(red * 255.0F), Math.round(green * 255.0F), Math.round(blue * 255.0F), Math.round(alpha * 255.0F));
   }

   public static int getColor(int red, int green, int blue, float alpha) {
      return getColor(red, green, blue, Math.round(alpha * 255.0F));
   }

   public static int getColor(float red, float green, float blue) {
      return getColor(red, green, blue, 1.0F);
   }

   public static int getColor(int brightness, int alpha) {
      return getColor(brightness, brightness, brightness, alpha);
   }

   public static int getColor(int brightness, float alpha) {
      return getColor(brightness, Math.round(alpha * 255.0F));
   }

   public static int getColor(int brightness) {
      return getColor(brightness, brightness, brightness);
   }

   public static int replAlpha(int color, int alpha) {
      return getColor(red(color), green(color), blue(color), alpha);
   }

   public static int replAlpha(int color, float alpha) {
      return getColor(red(color), green(color), blue(color), alpha);
   }

   public static int multAlpha(int color, float percent01) {
      return getColor(red(color), green(color), blue(color), Math.round(alpha(color) * percent01));
   }

   public static int toGray(int color, float percent01) {
      int r = red(color);
      int g = green(color);
      int b = blue(color);
      int a = alpha(color);
      int target = 128;
      r = Math.round(r + (target - r) * percent01);
      g = Math.round(g + (target - g) * percent01);
      b = Math.round(b + (target - b) * percent01);
      float darkFactor = percent01 / 2.0F;
      r = Math.round(r * darkFactor);
      g = Math.round(g * darkFactor);
      b = Math.round(b * darkFactor);
      return getColor(r, g, b, a);
   }

   public static int multDark(int color, float percent01) {
      return getColor(Math.round(red(color) * percent01), Math.round(green(color) * percent01), Math.round(blue(color) * percent01), alpha(color));
   }

   public static int multBright(int color, float percent01) {
      return getColor(
         Math.min(255, Math.round(red(color) / percent01)),
         Math.min(255, Math.round(green(color) / percent01)),
         Math.min(255, Math.round(blue(color) / percent01)),
         alpha(color)
      );
   }

   public static int overCol(int color1, int color2, float percent01) {
      float percent = MathHelper.clamp(percent01, 0.0F, 1.0F);
      return getColor(
         Interpolator.lerp(red(color1), red(color2), percent),
         Interpolator.lerp(green(color1), green(color2), percent),
         Interpolator.lerp(blue(color1), blue(color2), percent),
         Interpolator.lerp(alpha(color1), alpha(color2), percent)
      );
   }

   public static int overCol(int color1, int color2) {
      return overCol(color1, color2, 0.5F);
   }

   public static int[] genGradientForText(int color1, int color2, int length) {
      int[] gradient = new int[length];

      for (int i = 0; i < length; i++) {
         float pc = (float)i / (length - 1);
         gradient[i] = overCol(color1, color2, pc);
      }

      return gradient;
   }

   public static int interpolate(int color1, int color2, double amount) {
      amount = (float)MathHelper.clamp(amount, 0.0, 1.0);
      return getColor(
         Interpolator.lerp(red(color1), red(color2), amount),
         Interpolator.lerp(green(color1), green(color2), amount),
         Interpolator.lerp(blue(color1), blue(color2), amount),
         Interpolator.lerp(alpha(color1), alpha(color2), amount)
      );
   }

   public static int rainbow(int speed, int index, float saturation, float brightness, float opacity) {
      int angle = (int)((System.currentTimeMillis() / speed + index) % 360L);
      float hue = angle / 360.0F;
      int color = Color.HSBtoRGB(hue, saturation, brightness);
      return getColor(red(color), green(color), blue(color), Math.round(opacity * 255.0F));
   }

   public static int fade(int speed, int index, int first, int second) {
      int angle = (int)((System.currentTimeMillis() / speed + index) % 360L);
      angle = angle >= 180 ? 360 - angle : angle;
      return overCol(first, second, angle / 180.0F);
   }

   public static int fade(int index) {
      return fade(10, index, fade(), multDark(fade(), 0.5F));
   }

   public static int fade() {
      return Renderer2D.ColorUtil.getClientColor();
   }

   public static int gradient(int start, int end, int index, int speed) {
      int angle = (int)((System.currentTimeMillis() / speed + index) % 360L);
      angle = (angle > 180 ? 360 - angle : angle) + 180;
      int color = interpolate(start, end, MathHelper.clamp(angle / 180.0F - 1.0F, 0.0F, 1.0F));
      float[] hs = rgba(color);
      float[] hsb = Color.RGBtoHSB((int)(hs[0] * 255.0F), (int)(hs[1] * 255.0F), (int)(hs[2] * 255.0F), null);
      hsb[1] *= 1.5F;
      hsb[1] = Math.min(hsb[1], 1.0F);
      return Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
   }

   public static int getColor(int red, int green, int blue, int alpha) {
      ColorUtil.ColorKey key = new ColorUtil.ColorKey(red, green, blue, alpha);
      ColorUtil.CacheEntry cacheEntry = colorCache.computeIfAbsent(key, k -> {
         ColorUtil.CacheEntry newEntry = new ColorUtil.CacheEntry(k, computeColor(red, green, blue, alpha), 60000L);
         cleanupQueue.offer(newEntry);
         return newEntry;
      });
      return cacheEntry.getColor();
   }

   public static int getColor(int red, int green, int blue) {
      return getColor(red, green, blue, 255);
   }

   private static int computeColor(int red, int green, int blue, int alpha) {
      return MathHelper.clamp(alpha, 0, 255) << 24
         | MathHelper.clamp(red, 0, 255) << 16
         | MathHelper.clamp(green, 0, 255) << 8
         | MathHelper.clamp(blue, 0, 255);
   }

   private static String generateKey(int red, int green, int blue, int alpha) {
      return red + "," + green + "," + blue + "," + alpha;
   }

   public static void shutdownCacheCleaner() {
      cacheCleaner.shutdown();
   }

   @Generated
   private ColorUtil() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }

   static {
      cacheCleaner.scheduleWithFixedDelay(() -> {
         for (ColorUtil.CacheEntry entry = cleanupQueue.poll(); entry != null; entry = cleanupQueue.poll()) {
            if (entry.isExpired()) {
               colorCache.remove(entry.getKey());
            }
         }
      }, 0L, 1L, TimeUnit.SECONDS);
   }

   @Environment(EnvType.CLIENT)
   private static class CacheEntry implements Delayed {
      private final ColorUtil.ColorKey key;
      private final int color;
      private final long expirationTime;

      CacheEntry(ColorUtil.ColorKey key, int color, long ttl) {
         this.key = key;
         this.color = color;
         this.expirationTime = System.currentTimeMillis() + ttl;
      }

      @Override
      public long getDelay(TimeUnit unit) {
         long delay = this.expirationTime - System.currentTimeMillis();
         return unit.convert(delay, TimeUnit.MILLISECONDS);
      }

      public int compareTo(Delayed other) {
         return other instanceof ColorUtil.CacheEntry ? Long.compare(this.expirationTime, ((ColorUtil.CacheEntry)other).expirationTime) : 0;
      }

      public boolean isExpired() {
         return System.currentTimeMillis() > this.expirationTime;
      }

      @Generated
      public ColorUtil.ColorKey getKey() {
         return this.key;
      }

      @Generated
      public int getColor() {
         return this.color;
      }

      @Generated
      public long getExpirationTime() {
         return this.expirationTime;
      }
   }

   @Environment(EnvType.CLIENT)
   private static class ColorKey {
      final int red;
      final int green;
      final int blue;
      final int alpha;

      @Generated
      public int getRed() {
         return this.red;
      }

      @Generated
      public int getGreen() {
         return this.green;
      }

      @Generated
      public int getBlue() {
         return this.blue;
      }

      @Generated
      public int getAlpha() {
         return this.alpha;
      }

      @Generated
      public ColorKey(int red, int green, int blue, int alpha) {
         this.red = red;
         this.green = green;
         this.blue = blue;
         this.alpha = alpha;
      }

      @Generated
      @Override
      public boolean equals(Object o) {
         if (o == this) {
            return true;
         } else if (!(o instanceof ColorUtil.ColorKey other)) {
            return false;
         } else if (!other.canEqual(this)) {
            return false;
         } else if (this.getRed() != other.getRed()) {
            return false;
         } else if (this.getGreen() != other.getGreen()) {
            return false;
         } else {
            return this.getBlue() != other.getBlue() ? false : this.getAlpha() == other.getAlpha();
         }
      }

      @Generated
      protected boolean canEqual(Object other) {
         return other instanceof ColorUtil.ColorKey;
      }

      @Generated
      @Override
      public int hashCode() {
         int PRIME = 59;
         int result = 1;
         result = result * 59 + this.getRed();
         result = result * 59 + this.getGreen();
         result = result * 59 + this.getBlue();
         return result * 59 + this.getAlpha();
      }
   }
}
