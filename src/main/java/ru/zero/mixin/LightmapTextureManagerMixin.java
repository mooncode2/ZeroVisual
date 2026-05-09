package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.zero.Zero;
import ru.zero.module.impl.visuals.NightVision;

@Environment(EnvType.CLIENT)
@Mixin({LightmapTextureManager.class})
public class LightmapTextureManagerMixin {
   @Redirect(
      method = {"update"},
      at = @At(
         value = "INVOKE",
         target = "Ljava/lang/Double;floatValue()F",
         ordinal = 1
      )
   )
   private float night$getGammaValue(Double instance) {
      if (Zero.get != null && Zero.get.manager != null) {
         NightVision module = Zero.get.manager.get(NightVision.class);
         if (module != null && module.enable && module.mode.is("Р“Р°РјРјР°")) {
            return 200.0F;
         }
      }

      return instance.floatValue();
   }
}
