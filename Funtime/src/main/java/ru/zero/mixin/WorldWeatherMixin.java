package ru.zero.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.zero.module.impl.utils.Optimizer;
import ru.zero.module.impl.visuals.CustomWorld;

@Environment(EnvType.CLIENT)
@Mixin(targets = {"net.minecraft.class_1937"})
public abstract class WorldWeatherMixin {
   @ModifyReturnValue(method = {"method_8430(F)F"}, at = {@At("RETURN")})
   private float night$alwaysClear_rainGradient(float original) {
      return (CustomWorld.isEnabled && CustomWorld.alwaysClear.get()) || Optimizer.shouldDisableWeather() ? 0.0F : original;
   }

   @ModifyReturnValue(method = {"method_8478(F)F"}, at = {@At("RETURN")})
   private float night$alwaysClear_thunderGradient(float original) {
      return (CustomWorld.isEnabled && CustomWorld.alwaysClear.get()) || Optimizer.shouldDisableWeather() ? 0.0F : original;
   }
}
