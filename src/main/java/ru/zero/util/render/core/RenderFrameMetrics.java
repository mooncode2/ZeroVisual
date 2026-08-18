package ru.zero.util.render.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class RenderFrameMetrics {
   private static final RenderFrameMetrics INSTANCE = new RenderFrameMetrics();
   private long frameStartNanos = 0L;
   private int currentDrawCalls = 0;
   private int currentTriangles = 0;
   private volatile RenderFrameMetrics.FrameMetricsSnapshot lastSnapshot = new RenderFrameMetrics.FrameMetricsSnapshot(0L, 0, 0);

   private RenderFrameMetrics() {
   }

   public static RenderFrameMetrics getInstance() {
      return INSTANCE;
   }

   public void beginFrame(int width, int height) {
      if (width > 0 && height > 0) {
         this.frameStartNanos = System.nanoTime();
         this.currentDrawCalls = 0;
         this.currentTriangles = 0;
      }
   }

   public void recordDrawCall(int triangleCount) {
      if (triangleCount < 0) {
         triangleCount = 0;
      }

      this.currentDrawCalls++;
      this.currentTriangles += triangleCount;
   }

   public void endFrame() {
      long now = System.nanoTime();
      long duration = this.frameStartNanos > 0L ? Math.max(0L, now - this.frameStartNanos) : 0L;
      this.lastSnapshot = new RenderFrameMetrics.FrameMetricsSnapshot(duration, this.currentDrawCalls, this.currentTriangles);
      this.frameStartNanos = now;
      this.currentDrawCalls = 0;
      this.currentTriangles = 0;
   }

   public RenderFrameMetrics.FrameMetricsSnapshot snapshot() {
      return this.lastSnapshot;
   }

   @Environment(EnvType.CLIENT)
   public record FrameMetricsSnapshot(long frameDurationNanos, int drawCalls, int triangles) {
      public FrameMetricsSnapshot(long frameDurationNanos, int drawCalls, int triangles) {
         frameDurationNanos = Math.max(0L, frameDurationNanos);
         drawCalls = Math.max(0, drawCalls);
         triangles = Math.max(0, triangles);
         this.frameDurationNanos = frameDurationNanos;
         this.drawCalls = drawCalls;
         this.triangles = triangles;
      }

      public double frameTimeMillis() {
         return this.frameDurationNanos / 1000000.0;
      }

      public double framesPerSecond() {
         return this.frameDurationNanos > 0L ? 1.0E9 / this.frameDurationNanos : 0.0;
      }
   }
}
