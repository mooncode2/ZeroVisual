package ru.zero.util.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Arm;

@Environment(EnvType.CLIENT)
public class MaceColorRenderer {

   /**
    * Проверяет, что контекст отображения соответствует булаве в основной руке от первого лица.
    */
   public static boolean isMainHandFirstPerson(ItemDisplayContext displayContext) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null || !displayContext.isFirstPerson()) {
         return false;
      }

      Arm mainArm = mc.player.getMainArm();
      ItemDisplayContext mainHandContext = mainArm == Arm.RIGHT
         ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
         : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

      return displayContext == mainHandContext;
   }

   /**
    * Создает VertexConsumer, который заменяет цвет каждой вершины на заданный.
    */
   public static VertexConsumer createColoredConsumer(VertexConsumer parent, int color) {
      return new ColoredVertexConsumer(parent, color);
   }

   /**
    * VertexConsumer, заменяющий цвет каждой вершины на заданный.
    */
   private static class ColoredVertexConsumer implements VertexConsumer {
      private final VertexConsumer parent;
      private final int color;

      public ColoredVertexConsumer(VertexConsumer parent, int color) {
         this.parent = parent;
         this.color = color;
      }

      private VertexConsumer applyColor() {
         int argb = this.color;
         int r = (argb >> 16) & 0xFF;
         int g = (argb >> 8) & 0xFF;
         int b = argb & 0xFF;
         int a = (argb >> 24) & 0xFF;
         this.parent.color(r, g, b, a);
         return this;
      }

      @Override
      public VertexConsumer vertex(float x, float y, float z) {
         this.parent.vertex(x, y, z);
         return this;
      }

      @Override
      public VertexConsumer color(int red, int green, int blue, int alpha) {
         return this.applyColor();
      }

      @Override
      public VertexConsumer color(int packedColor) {
         return this.applyColor();
      }

      @Override
      public VertexConsumer color(float red, float green, float blue, float alpha) {
         return this.applyColor();
      }

      @Override
      public VertexConsumer texture(float u, float v) {
         this.parent.texture(u, v);
         return this;
      }

      @Override
      public VertexConsumer overlay(int u, int v) {
         this.parent.overlay(u, v);
         return this;
      }

      @Override
      public VertexConsumer light(int u, int v) {
         this.parent.light(u, v);
         return this;
      }

      @Override
      public VertexConsumer normal(float x, float y, float z) {
         this.parent.normal(x, y, z);
         return this;
      }

      @Override
      public VertexConsumer lineWidth(float width) {
         this.parent.lineWidth(width);
         return this;
      }
   }
}
