package ru.zero.sound.filters;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class FilterLowPass {
   public float gain = 1.0F;
   public float gainHF = 1.0F;
   private static final float MIN_GAIN = 0.0F;
   private static final float MAX_GAIN = 1.0F;
   private static final float MIN_GAIN_HF = 0.0F;
   private static final float MAX_GAIN_HF = 1.0F;

   public void checkParameters() {
      this.gain = MathHelper.clamp(this.gain, 0.0F, 1.0F);
      this.gainHF = MathHelper.clamp(this.gainHF, 0.0F, 1.0F);
   }

   public void reset() {
      this.gain = 1.0F;
      this.gainHF = 1.0F;
   }
}
