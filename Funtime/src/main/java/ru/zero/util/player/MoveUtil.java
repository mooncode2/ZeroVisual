package ru.zero.util.player;
import net.minecraft.client.MinecraftClient;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.InputUtil;
import ru.zero.event.player.EventInput;
import ru.zero.util.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class MoveUtil implements IMinecraft {
   public static double direction(float rotationYaw, float moveForward, float moveStrafing) {
      if (moveForward < 0.0F) {
         rotationYaw += 180.0F;
      }

      float forward = 1.0F;
      if (moveForward < 0.0F) {
         forward = -0.5F;
      }

      if (moveForward > 0.0F) {
         forward = 0.5F;
      }

      if (moveStrafing > 0.0F) {
         rotationYaw -= 90.0F * forward;
      }

      if (moveStrafing < 0.0F) {
         rotationYaw += 90.0F * forward;
      }

      return Math.toRadians(rotationYaw);
   }

   public static boolean isMoving() {
      return mc.options.forwardKey.isPressed()
         || mc.options.backKey.isPressed()
         || mc.options.leftKey.isPressed()
         || mc.options.rightKey.isPressed();
   }

   public static double[] calculateDirection(double distance) {
      float[] movement = getMovementFromKeys();
      return calculateDirection(movement[0], movement[1], distance);
   }

   public static double[] calculateDirection(float forward, float sideways, double distance) {
      float yaw = mc.player.getYaw();
      if (forward != 0.0F) {
         if (sideways > 0.0F) {
            yaw += forward > 0.0F ? -45.0F : 45.0F;
         } else if (sideways < 0.0F) {
            yaw += forward > 0.0F ? 45.0F : -45.0F;
         }

         sideways = 0.0F;
         forward = forward > 0.0F ? 1.0F : -1.0F;
      }

      double sinYaw = Math.sin(Math.toRadians(yaw + 90.0F));
      double cosYaw = Math.cos(Math.toRadians(yaw + 90.0F));
      double xMovement = forward * distance * cosYaw + sideways * distance * sinYaw;
      double zMovement = forward * distance * sinYaw - sideways * distance * cosYaw;
      return new double[]{xMovement, zMovement};
   }

   public static void fixMovement(EventInput event, float yaw) {
      float forward = event.getForward();
      float strafe = event.getStrafe();
      double angle = MathHelper.wrapDegrees(Math.toDegrees(direction(mc.player.isFlyingVehicle() ? mc.player.getYaw() : yaw, forward, strafe)));
      if (forward != 0.0F || strafe != 0.0F) {
         float closestForward = 0.0F;
         float closestStrafe = 0.0F;
         float closestDifference = Float.MAX_VALUE;

         for (float predictedForward = -1.0F; predictedForward <= 1.0F; predictedForward++) {
            for (float predictedStrafe = -1.0F; predictedStrafe <= 1.0F; predictedStrafe++) {
               if (predictedStrafe != 0.0F || predictedForward != 0.0F) {
                  double predictedAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(mc.player.getYaw(), predictedForward, predictedStrafe)));
                  double difference = Math.abs(angle - predictedAngle);
                  if (difference < closestDifference) {
                     closestDifference = (float)difference;
                     closestForward = predictedForward;
                     closestStrafe = predictedStrafe;
                  }
               }
            }
         }

         event.setForward(closestForward);
         event.setStrafe(closestStrafe);
      }
   }

   public static float[] getMovementFromKeys() {
      float forward = 0.0F;
      float strafe = 0.0F;
      long handle = mc.getWindow().getHandle();
      if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), mc.options.forwardKey.getDefaultKey().getCode())) {
         forward++;
      }

      if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), mc.options.backKey.getDefaultKey().getCode())) {
         forward--;
      }

      if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), mc.options.leftKey.getDefaultKey().getCode())) {
         strafe++;
      }

      if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), mc.options.rightKey.getDefaultKey().getCode())) {
         strafe--;
      }

      return new float[]{forward, strafe};
   }

   public static void fixMovement(float cameraYaw) {
      float[] movement = getMovementFromKeys();
      float forward = movement[0];
      float strafe = movement[1];
      if (forward != 0.0F || strafe != 0.0F) {
         double angle = MathHelper.wrapDegrees(
            Math.toDegrees(direction(mc.player.isFlyingVehicle() ? mc.player.getYaw() : cameraYaw, forward, strafe))
         );
         float closestForward = 0.0F;
         float closestStrafe = 0.0F;
         float closestDifference = Float.MAX_VALUE;

         for (float predictedForward = -1.0F; predictedForward <= 1.0F; predictedForward++) {
            for (float predictedStrafe = -1.0F; predictedStrafe <= 1.0F; predictedStrafe++) {
               if (predictedStrafe != 0.0F || predictedForward != 0.0F) {
                  double predictedAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(mc.player.getYaw(), predictedForward, predictedStrafe)));
                  double difference = Math.abs(angle - predictedAngle);
                  if (difference < closestDifference) {
                     closestDifference = (float)difference;
                     closestForward = predictedForward;
                     closestStrafe = predictedStrafe;
                  }
               }
            }
         }

         mc.options.forwardKey.setPressed(closestForward > 0.0F);
         mc.options.backKey.setPressed(closestForward < 0.0F);
         mc.options.leftKey.setPressed(closestStrafe > 0.0F);
         mc.options.rightKey.setPressed(closestStrafe < 0.0F);
      }
   }

   public static float[] getSilentMove(float forward, float strafe, float playerYaw, float cameraYaw) {
      double desiredAngle = direction(cameraYaw, forward, strafe);
      double playerRad = Math.toRadians(playerYaw);
      double relativeAngle = desiredAngle - playerRad;
      float newForward = (float)Math.cos(relativeAngle);
      float newStrafe = (float)Math.sin(relativeAngle);
      float maxMag = Math.max(Math.abs(forward), Math.abs(strafe));
      float currentMag = (float)Math.hypot(newForward, newStrafe);
      if (currentMag != 0.0F) {
         newForward = newForward / currentMag * maxMag;
         newStrafe = newStrafe / currentMag * maxMag;
      }

      return new float[]{newForward, newStrafe};
   }

   @Generated
   private MoveUtil() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
