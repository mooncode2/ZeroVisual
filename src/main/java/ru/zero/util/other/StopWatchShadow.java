package ru.zero.util.other;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class StopWatchShadow {
   public long lastMS = System.currentTimeMillis();

   public StopWatchShadow() {
      this.reset();
   }

   public void reset() {
      this.lastMS = System.currentTimeMillis();
   }

   public boolean isReached(long time) {
      return System.currentTimeMillis() - this.lastMS > time;
   }

   public void setLastMS(long newValue) {
      this.lastMS = System.currentTimeMillis() + newValue;
   }

   public void setTime(long time) {
      this.lastMS = time;
   }

   public long getTime() {
      return System.currentTimeMillis() - this.lastMS;
   }

   public boolean isRunning() {
      return System.currentTimeMillis() - this.lastMS <= 0L;
   }

   public boolean hasTimeElapsed(long l) {
      return System.currentTimeMillis() - this.lastMS > l;
   }

   public boolean hasTimeElapsed() {
      return this.lastMS < System.currentTimeMillis();
   }

   public boolean hasTimeElapsed(long time, boolean reset) {
      boolean elapsed = System.currentTimeMillis() - this.lastMS >= time;
      if (elapsed && reset) {
         this.reset();
      }

      return elapsed;
   }

   @Generated
   public long getLastMS() {
      return this.lastMS;
   }
}
