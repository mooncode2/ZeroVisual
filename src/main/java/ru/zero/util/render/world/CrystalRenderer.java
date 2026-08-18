package ru.zero.util.render.world;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class CrystalRenderer {
   private static final Vector3f[] VERTICES = new Vector3f[] {
         new Vector3f(0.0F, 1.5F, 0.0F),
         new Vector3f(0.0F, -1.5F, 0.0F),
         new Vector3f(1.0F, 0.0F, 0.0F),
         new Vector3f(-1.0F, 0.0F, 0.0F),
         new Vector3f(0.0F, 0.0F, 1.0F),
         new Vector3f(0.0F, 0.0F, -1.0F)
   };
   private static final int[][] FACES = new int[][] {
         { 0, 2, 4 }, { 0, 4, 3 }, { 0, 3, 5 }, { 0, 5, 2 },
         { 1, 4, 2 }, { 1, 3, 4 }, { 1, 5, 3 }, { 1, 2, 5 }
   };
   private static final float[] FACE_BRIGHTNESS = new float[] {
         1.0F, 0.8F, 0.6F, 0.9F, 0.7F, 0.5F, 0.4F, 0.6F
   };

   private CrystalRenderer() {
   }

   public static void render(
         MatrixStack matrices,
         VertexConsumer buffer,
         float x,
         float y,
         float z,
         float size,
         int rgbaColor
   ) {
      matrices.push();
      matrices.translate(x, y, z);
      matrices.scale(size, size, size);
      Matrix4f matrix = matrices.peek().getPositionMatrix();

      for (int i = 0; i < FACES.length; i++) {
         int[] face = FACES[i];
         float brightness = FACE_BRIGHTNESS[i];
         Vector3f v1 = VERTICES[face[0]];
         Vector3f v2 = VERTICES[face[1]];
         Vector3f v3 = VERTICES[face[2]];
         int shaded = applyBrightness(rgbaColor, brightness);
         int r = shaded >> 16 & 0xFF;
         int g = shaded >> 8 & 0xFF;
         int b = shaded & 0xFF;
         int a = shaded >> 24 & 0xFF;
         buffer.vertex(matrix, v1.x, v1.y, v1.z).color(r, g, b, a);
         finalizeVertex(buffer);
         buffer.vertex(matrix, v2.x, v2.y, v2.z).color(r, g, b, a);
         finalizeVertex(buffer);
         buffer.vertex(matrix, v3.x, v3.y, v3.z).color(r, g, b, a);
         finalizeVertex(buffer);
      }

      matrices.pop();
   }

   private static void finalizeVertex(VertexConsumer vertex) {
      try {
         Method nextMethod = vertex.getClass().getMethod("next");
         nextMethod.invoke(vertex);
      } catch (NoSuchMethodException ignored) {
      } catch (IllegalAccessException e) {
         throw new IllegalStateException("Unable to access vertex finalization method", e);
      } catch (InvocationTargetException e) {
         Throwable cause = e.getCause();
         if (cause instanceof RuntimeException runtime) {
            throw runtime;
         }

         if (cause instanceof Error error) {
            throw error;
         }

         throw new IllegalStateException("Vertex finalization failed", cause);
      }
   }

   private static int applyBrightness(int color, float brightness) {
      int alpha = color >> 24 & 0xFF;
      int red = Math.min(255, Math.max(0, (int) ((color >> 16 & 0xFF) * brightness)));
      int green = Math.min(255, Math.max(0, (int) ((color >> 8 & 0xFF) * brightness)));
      int blue = Math.min(255, Math.max(0, (int) ((color & 0xFF) * brightness)));
      return alpha << 24 | red << 16 | green << 8 | blue;
   }
}
