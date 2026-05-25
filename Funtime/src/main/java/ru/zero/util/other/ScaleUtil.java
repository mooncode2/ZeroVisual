package ru.zero.util.other;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.ScaledResolution;

@Environment(EnvType.CLIENT)
public class ScaleUtil {
   public static float size = 2.0F;

   public static void scale_pre(Renderer2D r2) {
      ScaledResolution scaledRes = new ScaledResolution(MinecraftClient.getInstance());
      float scale = (float)(ScaledResolution.getScaleFactor() / Math.pow(ScaledResolution.getScaleFactor(), 2.0));
      r2.pushScale(scale * size, scale * size, scale * size);
   }

   public static void scale_post(Renderer2D r2) {
      r2.pushScale(size, size, size);
   }

   public static int calc(int value) {
      ScaledResolution rs = new ScaledResolution(MinecraftClient.getInstance());
      return (int)(value * ScaledResolution.getScaleFactor() / size);
   }

   public static int calc(float value) {
      ScaledResolution rs = new ScaledResolution(MinecraftClient.getInstance());
      return (int)(value * ScaledResolution.getScaleFactor() / size);
   }

   public static float[] calc(float mouseX, float mouseY) {
      ScaledResolution rs = new ScaledResolution(MinecraftClient.getInstance());
      mouseX = mouseX * ScaledResolution.getScaleFactor() / size;
      mouseY = mouseY * ScaledResolution.getScaleFactor() / size;
      return new float[]{mouseX, mouseY};
   }
}
