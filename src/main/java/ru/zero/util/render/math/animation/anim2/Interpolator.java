package ru.zero.util.render.math.animation.anim2;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class Interpolator {
   public static double lerp(double start, double end, double step) {
      return start + step * (end - start);
   }

   public static float lerp(float start, float end, float step) {
      return (float) ((double) start + (double) step * ((double) end - (double) start));
   }

   @SuppressWarnings("unchecked")
   public static <T extends Number> T lerp(T input, T target, double step) {
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
}
