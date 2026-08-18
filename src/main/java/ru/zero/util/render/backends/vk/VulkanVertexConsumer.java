package ru.zero.util.render.backends.vk;

import java.nio.FloatBuffer;
import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class VulkanVertexConsumer implements VertexConsumer {
   public static final int LINES = 0;
   public static final int QUADS = 1;

   private static final int STRIDE = 9;

   private final VulkanWorldRenderer world;
   private int drawMode;
   private int glTexture;
   private int texWidth;
   private int texHeight;
   private float[] vertexData = new float[64 * STRIDE];
   private int vertexCount;
   private final float[] current = new float[STRIDE];
   private boolean hasPending;
   private boolean textured;

   public VulkanVertexConsumer(VulkanWorldRenderer world, int drawMode) {
      this(world, drawMode, 0, 0, 0);
   }

   public VulkanVertexConsumer(VulkanWorldRenderer world, int drawMode, int glTexture, int texWidth, int texHeight) {
      this.world = world;
      this.drawMode = drawMode;
      this.glTexture = glTexture;
      this.texWidth = texWidth;
      this.texHeight = texHeight;
      this.current[3] = 1.0F;
      this.current[4] = 1.0F;
      this.current[5] = 1.0F;
      this.current[6] = 1.0F;
   }

   @Override
   public VertexConsumer vertex(float x, float y, float z) {
      if (this.hasPending) {
         this.emit();
      }
      this.current[0] = x;
      this.current[1] = y;
      this.current[2] = z;
      this.hasPending = true;
      return this;
   }

   @Override
   public VertexConsumer color(int r, int g, int b, int a) {
      this.current[3] = r / 255.0F;
      this.current[4] = g / 255.0F;
      this.current[5] = b / 255.0F;
      this.current[6] = a / 255.0F;
      return this;
   }

   @Override
   public VertexConsumer color(int argb) {
      this.current[3] = ((argb >> 16) & 0xFF) / 255.0F;
      this.current[4] = ((argb >> 8) & 0xFF) / 255.0F;
      this.current[5] = (argb & 0xFF) / 255.0F;
      this.current[6] = ((argb >> 24) & 0xFF) / 255.0F;
      return this;
   }

   @Override
   public VertexConsumer color(float r, float g, float b, float a) {
      this.current[3] = r;
      this.current[4] = g;
      this.current[5] = b;
      this.current[6] = a;
      return this;
   }

   @Override
   public VertexConsumer texture(float u, float v) {
      this.current[7] = u;
      this.current[8] = v;
      this.textured = true;
      return this;
   }

   @Override
   public VertexConsumer overlay(int u, int v) {
      return this;
   }

   @Override
   public VertexConsumer overlay(int uv) {
      return this;
   }

   @Override
   public VertexConsumer light(int u, int v) {
      return this;
   }

   @Override
   public VertexConsumer light(int uv) {
      return this;
   }

   @Override
   public VertexConsumer normal(float x, float y, float z) {
      return this;
   }

   @Override
   public VertexConsumer lineWidth(float width) {
      return this;
   }

   public void next() {
      if (this.hasPending) {
         this.emit();
      }
   }

   private void ensureCapacity(int neededVerts) {
      int neededFloats = neededVerts * STRIDE;
      if (neededFloats > this.vertexData.length) {
         int newLen = this.vertexData.length;
         while (newLen < neededFloats) {
            newLen *= 2;
         }
         this.vertexData = Arrays.copyOf(this.vertexData, newLen);
      }
   }

   private void emit() {
      this.ensureCapacity(this.vertexCount + 1);
      System.arraycopy(this.current, 0, this.vertexData, this.vertexCount * STRIDE, STRIDE);
      this.vertexCount++;
      this.hasPending = false;
      this.current[7] = 0.0F;
      this.current[8] = 0.0F;
   }

   public void flushTo(VulkanTextureManager textures, int glTexture, int texWidth, int texHeight) {
      if (this.hasPending) {
         this.emit();
      }
      if (this.vertexCount == 0) {
         return;
      }
      int effectiveGl = glTexture != 0 ? glTexture : this.glTexture;
      int effectiveW = texWidth != 0 ? texWidth : this.texWidth;
      int effectiveH = texHeight != 0 ? texHeight : this.texHeight;
      float[] data = this.vertexData;
      int count = this.vertexCount;
      if (this.textured && effectiveGl != 0 && textures != null) {
         long vkTex = this.world.importGlTexture(effectiveGl, effectiveW, effectiveH, textures);
         int handle = (int) vkTex;
         FloatBuffer dst = this.world.triTexVerts();
         int triCount = count * 3 / 4 * 2;
         int base = this.world.reserveTexturedVertices(triCount, effectiveGl, handle);
         int idx = base;
         for (int q = 0; q + 3 < count; q += 4) {
            int o0 = q * STRIDE;
            int o1 = (q + 1) * STRIDE;
            int o2 = (q + 2) * STRIDE;
            int o3 = (q + 3) * STRIDE;
            idx = putVert(dst, idx, data, o0);
            idx = putVert(dst, idx, data, o1);
            idx = putVert(dst, idx, data, o2);
            idx = putVert(dst, idx, data, o0);
            idx = putVert(dst, idx, data, o2);
            idx = putVert(dst, idx, data, o3);
         }
      } else if (this.drawMode == LINES) {
         FloatBuffer dst = this.world.lineVerts();
         int base = this.world.reserveLineVertices(count);
         int idx = base;
         for (int v = 0; v < count; v++) {
            idx = putVert(dst, idx, data, v * STRIDE);
         }
      } else {
         FloatBuffer dst = this.world.triColorVerts();
         int base = this.world.reserveTriColorVertices(count * 3 / 4 * 2);
         int idx = base;
         for (int q = 0; q + 3 < count; q += 4) {
            int o0 = q * STRIDE;
            int o1 = (q + 1) * STRIDE;
            int o2 = (q + 2) * STRIDE;
            int o3 = (q + 3) * STRIDE;
            idx = putVert(dst, idx, data, o0);
            idx = putVert(dst, idx, data, o1);
            idx = putVert(dst, idx, data, o2);
            idx = putVert(dst, idx, data, o0);
            idx = putVert(dst, idx, data, o2);
            idx = putVert(dst, idx, data, o3);
         }
      }
      this.vertexCount = 0;
   }

   private static int putVert(FloatBuffer dst, int idx, float[] data, int off) {
      dst.put(idx, data[off]);
      dst.put(idx + 1, data[off + 1]);
      dst.put(idx + 2, data[off + 2]);
      dst.put(idx + 3, data[off + 3]);
      dst.put(idx + 4, data[off + 4]);
      dst.put(idx + 5, data[off + 5]);
      dst.put(idx + 6, data[off + 6]);
      dst.put(idx + 7, data[off + 7]);
      dst.put(idx + 8, data[off + 8]);
      return idx + 9;
   }

   public boolean isTextured() {
      return this.textured;
   }

   public void reset(int drawMode, int glTexture, int texWidth, int texHeight) {
      this.drawMode = drawMode;
      this.glTexture = glTexture;
      this.texWidth = texWidth;
      this.texHeight = texHeight;
      this.vertexCount = 0;
      this.hasPending = false;
      this.textured = false;
      this.current[3] = 1.0F;
      this.current[4] = 1.0F;
      this.current[5] = 1.0F;
      this.current[6] = 1.0F;
      this.current[7] = 0.0F;
      this.current[8] = 0.0F;
   }
}
