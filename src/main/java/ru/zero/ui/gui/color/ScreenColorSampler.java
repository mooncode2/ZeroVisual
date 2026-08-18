package ru.zero.ui.gui.color;

import java.awt.Color;
import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;

@Environment(EnvType.CLIENT)
public final class ScreenColorSampler {
   private ScreenColorSampler() {
   }

   public static Color sample(int mouseX, int mouseY) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.getWindow() == null) {
         return Color.WHITE;
      }

      double scale = mc.getWindow().getScaleFactor();
      int framebufferHeight = mc.getWindow().getFramebufferHeight();
      int x = (int) Math.floor(mouseX * scale);
      int y = (int) Math.floor(framebufferHeight - mouseY * scale);
      if (x < 0 || y < 0) {
         return Color.WHITE;
      }

      ByteBuffer buffer = ByteBuffer.allocateDirect(4);
      GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
      int r = buffer.get(0) & 0xFF;
      int g = buffer.get(1) & 0xFF;
      int b = buffer.get(2) & 0xFF;
      int a = buffer.get(3) & 0xFF;
      return new Color(r, g, b, a);
   }
}
