package ru.zero.util.render.math;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;

@Environment(EnvType.CLIENT)
public class ScaleHelper {
   private MinecraftClient mc = MinecraftClient.getInstance();
   public static float size = 2.0F;

   public static void scale_pre() {
      ScaledResolution scaledRes = new ScaledResolution(MinecraftClient.getInstance());
      double scale = ScaledResolution.getScaleFactor() / Math.pow(ScaledResolution.getScaleFactor(), 2.0);
      GL11.glPushMatrix();
      GL11.glScaled(scale * size, scale * size, scale * size);
   }

   public static void scale_post() {
      GL11.glScaled(size, size, size);
      GL11.glPopMatrix();
   }

   public static void scaleStart(float x, float y, float scale) {
      MatrixStack poseStack = new MatrixStack();
      poseStack.push();
      poseStack.translate(x, y, 0.0F);
      poseStack.scale(scale, scale, 1.0F);
      poseStack.translate(-x, -y, 0.0F);
   }

   public static void scaleEnd() {
      MatrixStack poseStack = new MatrixStack();
      poseStack.pop();
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

   public static void scaleNonMatrix(float x, float y, float scale) {
      MatrixStack poseStack = new MatrixStack();
      poseStack.translate(x, y, 0.0F);
      poseStack.scale(scale, scale, 1.0F);
      poseStack.translate(-x, -y, 0.0F);
   }
}
