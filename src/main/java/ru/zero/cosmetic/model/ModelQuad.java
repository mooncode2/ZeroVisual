package ru.zero.cosmetic.model;

/** A textured quad in model space produced by {@link ModelPose#flatten(BedrockModel)}. */
public final class ModelQuad {
   public final float[][] pos;
   public final float[][] uv;

   public ModelQuad(int corners) {
      this.pos = new float[corners][3];
      this.uv = new float[corners][2];
   }

   public float depth() {
      float sum = 0.0F;
      for (float[] point : this.pos) {
         sum += point[2];
      }

      return sum / this.pos.length;
   }
}
