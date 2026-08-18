package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.Zero;
import ru.zero.module.impl.visuals.NoFluid;
import ru.zero.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
   @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
   private static void onRenderFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
      if (Zero.get != null && Zero.get.manager != null) {
         NoRender noRender = Zero.get.manager.get(NoRender.class);
         if (noRender != null && noRender.enable && NoRender.fire.get()) {
            ci.cancel();
            return;
         }

         if (NoFluid.shouldDisableLavaOverlay()) {
            ci.cancel();
         }
      }
   }

   @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
   private static void zero$cancelUnderwaterOverlay(
      MinecraftClient client,
      MatrixStack matrices,
      VertexConsumerProvider vertexConsumers,
      CallbackInfo ci
   ) {
      if (NoFluid.shouldDisableWaterOverlay()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
   private static void zero$cancelInWallOverlay(Sprite sprite, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
      if (NoFluid.shouldDisableWaterOverlay() || NoFluid.shouldDisableLavaOverlay()) {
         ci.cancel();
      }
   }
}
