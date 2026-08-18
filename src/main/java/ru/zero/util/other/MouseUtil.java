package ru.zero.util.other;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class MouseUtil {
   private static final double[] SCRATCH_X = new double[1];
   private static final double[] SCRATCH_Y = new double[1];

   private MouseUtil() {
   }

   public static MousePosition getMousePos() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.getWindow() == null) {
         return new MousePosition(-1, -1);
      }

      double[] mouseX = SCRATCH_X;
      double[] mouseY = SCRATCH_Y;
      GLFW.glfwGetCursorPos(client.getWindow().getHandle(), mouseX, mouseY);

      if (client.mouse != null) {
         client.mouse.unlockCursor();
      }

      return new MousePosition((int) mouseX[0], (int) mouseY[0]);
   }

   public record MousePosition(int x, int y) {
   }
}