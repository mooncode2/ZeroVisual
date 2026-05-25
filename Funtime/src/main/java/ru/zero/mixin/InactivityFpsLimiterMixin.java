package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Environment(EnvType.CLIENT)
@Mixin({InactivityFpsLimiter.class})
public abstract class InactivityFpsLimiterMixin {
   @Shadow
   private int maxFps;

   @ModifyConstant(
      method = {"update"},
      constant = {@Constant(
         intValue = 60
      )}
   )
   private int replaceMenuFps(int original) {
      return this.maxFps;
   }
}
