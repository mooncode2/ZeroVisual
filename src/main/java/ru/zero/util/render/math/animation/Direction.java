package ru.zero.util.render.math.animation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum Direction {
   FORWARDS,
   BACKWARDS;

   public Direction opposite() {
      return this == FORWARDS ? BACKWARDS : FORWARDS;
   }
}
