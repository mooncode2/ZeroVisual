package ru.zero.util.render.core;

import java.util.ArrayDeque;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TransformStack {
   private final ArrayDeque<float[]> stack = new ArrayDeque<>();
   private final ArrayDeque<float[]> free = new ArrayDeque<>();

   public TransformStack() {
      this.pushIdentity();
   }

   private float[] acquire() {
      float[] m = this.free.poll();
      if (m == null) {
         m = new float[9];
      }
      return m;
   }

   public void clear() {
      while (!this.stack.isEmpty()) {
         this.free.offer(this.stack.pop());
      }
      this.pushIdentity();
   }

   public void pushIdentity() {
      float[] m = this.acquire();
      m[0] = 1.0F; m[1] = 0.0F; m[2] = 0.0F;
      m[3] = 0.0F; m[4] = 1.0F; m[5] = 0.0F;
      m[6] = 0.0F; m[7] = 0.0F; m[8] = 1.0F;
      this.stack.push(m);
   }

   public void pushRotation(float degrees) {
      float rad = (float)Math.toRadians(degrees);
      float c = (float)Math.cos(rad);
      float s = (float)Math.sin(rad);
      float[] r = this.acquire();
      r[0] = c; r[1] = -s; r[2] = 0.0F;
      r[3] = s; r[4] = c;  r[5] = 0.0F;
      r[6] = 0.0F; r[7] = 0.0F; r[8] = 1.0F;
      float[] top = this.stack.peek();
      this.stack.push(mulInto(top, r, this.acquire()));
   }

   public void pushTranslation(float tx, float ty) {
      float[] t = this.acquire();
      t[0] = 1.0F; t[1] = 0.0F; t[2] = tx;
      t[3] = 0.0F; t[4] = 1.0F; t[5] = ty;
      t[6] = 0.0F; t[7] = 0.0F; t[8] = 1.0F;
      float[] top = this.stack.peek();
      this.stack.push(mulInto(top, t, this.acquire()));
   }

   public void pushTranslationInv(float tx, float ty) {
      this.pushTranslation(-tx, -ty);
   }

   public void pushScale(float sx, float sy, float originX, float originY) {
      float translateX = originX - originX * sx;
      float translateY = originY - originY * sy;
      float[] s = this.acquire();
      s[0] = sx; s[1] = 0.0F; s[2] = translateX;
      s[3] = 0.0F; s[4] = sy; s[5] = translateY;
      s[6] = 0.0F; s[7] = 0.0F; s[8] = 1.0F;
      float[] top = this.stack.peek();
      this.stack.push(mulInto(top, s, this.acquire()));
   }

   public void pushScale(float scale, float originX, float originY) {
      this.pushScale(scale, scale, originX, originY);
   }

   public void replaceTop(float[] matrix) {
      if (matrix == null) {
         throw new IllegalArgumentException("matrix must not be null");
      } else if (matrix.length != 9) {
         throw new IllegalArgumentException("matrix must have length 9");
      } else {
         for (float value : matrix) {
            if (!Float.isFinite(value)) {
               throw new IllegalArgumentException("matrix entries must be finite");
            }
         }

         if (this.stack.isEmpty()) {
            throw new IllegalStateException("cannot replace top matrix on an empty stack");
         } else {
            float[] top = this.stack.pop();
            System.arraycopy(matrix, 0, top, 0, 9);
            this.stack.push(top);
         }
      }
   }

   public void pop() {
      if (this.stack.size() > 1) {
         this.free.offer(this.stack.pop());
      }
   }

   public void popN(int count) {
      for (int i = 0; i < count; i++) {
         if (this.stack.size() > 1) {
            this.free.offer(this.stack.pop());
         }
      }
   }

   public float[] current() {
      return this.stack.peek();
   }

   private static float[] mulInto(float[] a, float[] b, float[] out) {
      out[0] = a[0] * b[0] + a[1] * b[3] + a[2] * b[6];
      out[1] = a[0] * b[1] + a[1] * b[4] + a[2] * b[7];
      out[2] = a[0] * b[2] + a[1] * b[5] + a[2] * b[8];
      out[3] = a[3] * b[0] + a[4] * b[3] + a[5] * b[6];
      out[4] = a[3] * b[1] + a[4] * b[4] + a[5] * b[7];
      out[5] = a[3] * b[2] + a[4] * b[5] + a[5] * b[8];
      out[6] = a[6] * b[0] + a[7] * b[3] + a[8] * b[6];
      out[7] = a[6] * b[1] + a[7] * b[4] + a[8] * b[7];
      out[8] = a[6] * b[2] + a[7] * b[5] + a[8] * b[8];
      return out;
   }
}
