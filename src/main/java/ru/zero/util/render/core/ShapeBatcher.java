package ru.zero.util.render.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.util.render.backends.gl.GlBackend;

@Environment(EnvType.CLIENT)
final class ShapeBatcher {
   private final GlBackend backend;

   ShapeBatcher(GlBackend backend) {
      this.backend = backend;
   }

   void enqueueRect(
      float x, float y, float w, float h, float roundTopLeft, float roundTopRight, float roundBottomRight, float roundBottomLeft, int color, float[] transform
   ) {
      this.backend.enqueueRect(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft, color, transform);
   }

   void enqueueRectOutline(
      float x,
      float y,
      float w,
      float h,
      float roundTopLeft,
      float roundTopRight,
      float roundBottomRight,
      float roundBottomLeft,
      int color,
      float thickness,
      float[] transform
   ) {
      this.backend.enqueueRectOutline(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft, color, thickness, transform);
   }

   void enqueueGradient(
      float x,
      float y,
      float w,
      float h,
      float roundTopLeft,
      float roundTopRight,
      float roundBottomRight,
      float roundBottomLeft,
      int c00,
      int c10,
      int c11,
      int c01,
      float[] transform
   ) {
      this.backend.enqueueGradient(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft, c00, c10, c11, c01, transform);
   }

   void enqueueCircle(float cx, float cy, float radius, float startDeg, float pct, int color, float[] transform) {
      this.backend.enqueueCircle(cx, cy, radius, startDeg, pct, color, transform);
   }

   void flush() {
      this.backend.flush();
   }
}
