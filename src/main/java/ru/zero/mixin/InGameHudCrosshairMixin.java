package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.util.client.HudOverlayHelper;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public abstract class InGameHudCrosshairMixin {

   @Shadow
   private void renderCrosshair(DrawContext context, RenderTickCounter tickCounter) {
   }

   @Redirect(
         method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
         at = @At(
               value = "INVOKE",
               target = "Lnet/minecraft/client/gui/hud/InGameHud;renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"
         ),
         require = 0
   )
   private void zero$redirectRenderCrosshair(InGameHud instance, DrawContext context, RenderTickCounter tickCounter) {
      if (!HudOverlayHelper.shouldReplaceVanillaCrosshair()) {
         this.renderCrosshair(context, tickCounter);
      }
   }

   @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
   private void zero$cancelVanillaCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (HudOverlayHelper.shouldReplaceVanillaCrosshair()) {
         ci.cancel();
      }
   }
}
