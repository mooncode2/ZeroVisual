package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.Zero;
import ru.zero.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin({InGameOverlayRenderer.class})
public class InGameOverlayRendererMixin {
   @Inject(
      method = {"renderFireOverlay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onRenderFireOverlay(CallbackInfo ci) {
      if (Zero.get != null && Zero.get.manager != null) {
         NoRender noRender = Zero.get.manager.get(NoRender.class);
         if (noRender != null && noRender.enable && NoRender.fire.get()) {
            ci.cancel();
         }
      }
   }
}
