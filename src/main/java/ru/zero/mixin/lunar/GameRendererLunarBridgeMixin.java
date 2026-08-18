package ru.zero.mixin.lunar;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import ru.zero.Zero;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.compat.LunarCompat;
import ru.zero.compat.LunarOverlayRender;
import ru.zero.module.impl.utils.Zoom;
import ru.zero.module.impl.visuals.AspectRation;
import ru.zero.module.impl.visuals.BlockOverlay;

/**
 * Subset of {@link ru.zero.mixin.GameRendererMixin} on Lunar (zoom, aspect, block-outline suppression).
 * World rendering uses {@link ru.zero.mixin.WorldRendererMixin} with correct matrices.
 */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererLunarBridgeMixin {

   @Shadow
   public abstract float getFarPlaneDistance();

   @Inject(method = "renderWorld", at = @At("HEAD"), require = 0)
   private void zero$beginLunarOverlayFrame(RenderTickCounter tickCounter, CallbackInfo ci) {
      if (LunarCompat.isLunarClient()) {
         LunarOverlayRender.beginFrame();
      }
   }

   @Inject(method = { "getBasicProjectionMatrix" }, at = { @At("HEAD") }, cancellable = true, require = 0)
   private void zero$aspectProjection(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
      if (!LunarCompat.isLunarClient()) {
         return;
      }

      Matrix4f matrix4f = new Matrix4f();
      cir.cancel();
      float aspect = AspectRation.getProjectionAspect();
      cir.setReturnValue(matrix4f.perspective(fovDegrees * (float) (Math.PI / 180.0), aspect, 0.05F, this.getFarPlaneDistance()));
   }

   @ModifyReturnValue(method = { "getFov" }, at = { @At("RETURN") }, require = 0)
   private float zero$zoom(float original, Camera camera, float tickDelta, boolean changingFov) {
      if (!LunarCompat.isLunarClient() || Zero.get == null || Zero.get.manager == null) {
         return original;
      }

      Zoom zoom = Zero.get.manager.get(Zoom.class);
      if (zoom != null) {
         return zoom.modifyFov(original, tickDelta, changingFov);
      }
      return original;
   }

   @ModifyArgs(
         method = "renderWorld",
         at = @At(
               value = "INVOKE",
               target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"
         ),
         require = 0
   )
   private void zero$suppressVanillaBlockOutline(Args args) {
      if (!LunarCompat.isLunarClient()) {
         return;
      }

      if (!(boolean) args.get(2)) {
         return;
      }

      if (Zero.get == null || Zero.get.manager == null) {
         return;
      }

      BlockOverlay overlay = Zero.get.manager.get(BlockOverlay.class);
      if (overlay != null && overlay.enable && BlockOverlay.replaceVanilla.get()) {
         args.set(2, false);
      }
   }
}
