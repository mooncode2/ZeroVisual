package ru.zero.mixin.lunar;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.compat.LunarCompat;
import ru.zero.compat.LunarOverlayRender;
import ru.zero.module.impl.visuals.Crosshair;
import ru.zero.module.impl.visuals.HUD.TargetHUD;
import ru.zero.util.client.HudOverlayHelper;

/**
 * Lunar replacement for {@link ru.zero.mixin.InGameHudMixin}.
 * Crosshair: cancel vanilla in {@code renderCrosshair}, draw custom at RETURN (ichor-safe).
 */
@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public class InGameHudLunarMixin {

   @Shadow
   @Final
   private MinecraftClient client;

   @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true, require = 0)
   private void zero$hideHotbarInMenu(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (LunarCompat.isLunarClient() && LunarOverlayRender.isZeroMenuOpen(this.client)) {
         ci.cancel();
      }
   }

   @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
   private void zero$cancelVanillaCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (!LunarCompat.isLunarClient()) {
         return;
      }

      if (HudOverlayHelper.shouldReplaceVanillaCrosshair()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderCrosshair", at = @At("RETURN"), require = 0)
   private void zero$drawCustomCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (!LunarCompat.isLunarClient()) {
         return;
      }

      if (!HudOverlayHelper.shouldReplaceVanillaCrosshair()) {
         return;
      }

      if (!LunarOverlayRender.canRenderInGameHudOverlay(this.client)) {
         return;
      }

      Crosshair.renderInHudPass(context);
   }

   @Inject(method = { "renderStatusEffectOverlay" }, at = { @At("HEAD") }, cancellable = true, require = 0)
   private void zero$cancelStatusEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (!LunarCompat.isLunarClient()) {
         return;
      }

      if (HudOverlayHelper.shouldUseCustomPotions()) {
         ci.cancel();
      }
   }

   @Inject(method = { "render" }, at = { @At("RETURN") }, require = 0)
   private void zero$renderOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (!LunarCompat.isLunarClient()) {
         return;
      }

      if (!LunarOverlayRender.canRenderInGameHudOverlay(this.client)) {
         return;
      }

      TargetHUD.renderPendingItems(context);
      LunarOverlayRender.render(this.client, context);
   }
}
