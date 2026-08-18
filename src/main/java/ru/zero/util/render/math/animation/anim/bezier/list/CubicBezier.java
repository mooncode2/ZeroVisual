package ru.zero.util.render.math.animation.anim.bezier.list;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.util.render.math.animation.anim.bezier.Bezier;
import ru.zero.util.render.math.animation.anim.bezier.Point;

@Environment(EnvType.CLIENT)
public class CubicBezier extends Bezier {
   @Override
   public double getValue(double t) {
      double dt = 1.0 - t;
      double dt2 = dt * dt;
      double t2 = t * t;
      double y2 = this.getPoint2().getY();
      double y3 = this.getPoint3().getY();
      return 3.0 * y2 * dt2 * t + 3.0 * y3 * dt * t2 + t2 * t;
   }

   @Environment(EnvType.CLIENT)
   public static class Builder {
      private CubicBezier bezier = new CubicBezier();

      public Builder(CubicBezier bezier) {
         this.bezier = bezier;
      }

      public Builder() {
      }

      public CubicBezier.Builder setPoint2(Point point) {
         this.bezier.setPoint2(point);
         return this;
      }

      public CubicBezier.Builder setPoint3(Point point) {
         this.bezier.setPoint3(point);
         return this;
      }

      public CubicBezier build() {
         return this.bezier;
      }
   }
}
