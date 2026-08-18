package ru.zero.util.render.math.animation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Time-based animation that interpolates from 0 to {@code endPoint} over {@code duration} ms.
 * Supports direction reversal (forwards/backwards) and produces a smooth eased output
 * via the abstract {@link #getEquation(double)} which receives the raw 0..1 progress.
 */
@Environment(EnvType.CLIENT)
public abstract class Animation {
   private long startTime;
   protected int duration;
   protected double endPoint;
   protected Direction direction;

   public Animation(int ms, double endPoint) {
      this.duration = ms;
      this.endPoint = endPoint;
      this.direction = Direction.FORWARDS;
      this.startTime = System.currentTimeMillis();
   }

   public Animation(int ms, double endPoint, Direction direction) {
      this.duration = ms;
      this.endPoint = endPoint;
      this.direction = direction;
      this.startTime = System.currentTimeMillis();
   }

   public boolean finished(Direction direction) {
      return this.isDone() && this.direction.equals(direction);
   }

   public double getLinearOutput() {
      return Math.min(1.0, this.getProgress()) * this.endPoint;
   }

   public double getEndPoint() {
      return this.endPoint;
   }

   public void setEndPoint(double endPoint) {
      this.endPoint = endPoint;
   }

   public void reset() {
      this.startTime = System.currentTimeMillis();
   }

   public boolean isDone() {
      return this.getProgress() >= 1.0;
   }

   public void changeDirection() {
      this.setDirection(this.direction.opposite());
   }

   public Direction getDirection() {
      return this.direction;
   }

   public void setDirection(Direction direction) {
      if (this.direction != direction) {
         // Preserve the current output value when reversing: compute how much time
         // remains and back-date the start so the animation continues from the
         // current position rather than snapping.
         double currentProgress = this.getProgress();
         if (currentProgress > 0.0 && currentProgress < 1.0) {
            double remaining = 1.0 - currentProgress;
            this.startTime = System.currentTimeMillis() - (long)(remaining * this.duration);
         } else if (direction == Direction.BACKWARDS && currentProgress <= 0.0) {
            // Reversing a freshly created animation (output 0) to BACKWARDS must keep it
            // at output 0 instead of jumping to the endpoint; finish it immediately.
            this.startTime = System.currentTimeMillis() - this.duration;
         } else {
            this.startTime = System.currentTimeMillis();
         }
         this.direction = direction;
      }
   }

   public void setDuration(int duration) {
      this.duration = duration;
   }

   protected boolean correctOutput() {
      return false;
   }

   public long getTimePassed() {
      return System.currentTimeMillis() - this.startTime;
   }

   private double getProgress() {
      if (this.duration <= 0) {
         return 1.0;
      }
      return Math.max(0.0, Math.min(1.0, (double) this.getTimePassed() / (double) this.duration));
   }

   public float getOutput() {
      long timePassed = this.getTimePassed();
      if (this.direction == Direction.FORWARDS) {
         if (this.isDone()) {
            return (float) this.endPoint;
         }
         return (float) (this.getEquation(timePassed) * this.endPoint);
      } else if (this.isDone()) {
         return 0.0F;
      } else if (this.correctOutput()) {
         double revTime = Math.min((long) this.duration, Math.max(0L, this.duration - timePassed));
         return (float) (this.getEquation(revTime) * this.endPoint);
      } else {
         return (float) ((1.0 - this.getEquation(timePassed)) * this.endPoint);
      }
   }

   protected abstract double getEquation(double progress);
}