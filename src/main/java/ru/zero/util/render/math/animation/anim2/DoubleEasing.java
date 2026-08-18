package ru.zero.util.render.math.animation.anim2;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@FunctionalInterface
public interface DoubleEasing {
   double ease(double x);
}
