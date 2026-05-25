package ru.zero.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class SoundHelper {
   private static final double TO_DEGREES = 180.0 / Math.PI;

   public static Vec2f calculate(Vec3d from, Vec3d to) {
      Vec3d diff = to.subtract(from);
      double distance = Math.hypot(diff.x, diff.z);
      float yaw = (float)(MathHelper.atan2(diff.z, diff.x) * (180.0 / Math.PI)) - 90.0F;
      float pitch = (float)(-(MathHelper.atan2(diff.y, distance) * (180.0 / Math.PI)));
      return new Vec2f(yaw, pitch);
   }

   public static float getAngleDifference(float dir, float yaw) {
      float f = Math.abs(yaw - dir) % 360.0F;
      return f > 180.0F ? 360.0F - f : f;
   }

   public static double easeInOutExpo(double x) {
      if (x != 0.0 && x != 1.0) {
         return x < 0.5 ? Math.pow(2.0, 20.0 * x - 10.0) / 2.0 : (2.0 - Math.pow(2.0, -20.0 * x + 10.0)) / 2.0;
      } else {
         return x;
      }
   }
}
