package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.util.render.HitColorOverlay;

@Environment(EnvType.CLIENT)
@Mixin(OverlayTexture.class)
public class OverlayTextureMixin {
   @Inject(method = "<init>", at = @At("TAIL"))
   private void zero$captureVanillaOverlay(CallbackInfo ci) {
      HitColorOverlay.captureVanilla((OverlayTexture) (Object) this);
   }
}
