package ru.zero.util.render.math.animation.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.util.render.math.animation.Animation;
import ru.zero.util.render.math.animation.Direction;

@Environment(EnvType.CLIENT)
public class EaseBackIn extends Animation {
   private final float easeAmount;

   public EaseBackIn(int ms, double endPoint, float easeAmount) {
      super(ms, endPoint);
      this.easeAmount = easeAmount;
   }

   public EaseBackIn(int ms, double endPoint, float easeAmount, Direction direction) {
      super(ms, endPoint, direction);
      this.easeAmount = easeAmount;
   }

   @Override
   protected boolean correctOutput() {
      return true;
   }

   @Override
   protected double getEquation(double x) {
      double x1 = x / this.duration;
      float shrink = this.easeAmount + 1.0F;
      double xm = x1 - 1.0;
      return Math.max(0.0, 1.0 + shrink * xm * xm * xm + this.easeAmount * xm * xm);
   }
}
