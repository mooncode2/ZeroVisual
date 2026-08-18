package ru.zero.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import ru.zero.Zero;
import ru.zero.module.impl.utils.Zoom;
import ru.zero.module.impl.visuals.AspectRation;
import ru.zero.module.impl.visuals.BlockOverlay;
import ru.zero.module.impl.visuals.HUD.InformationHUD;
import ru.zero.util.other.Mathf;

@Environment(EnvType.CLIENT)
@Mixin({GameRenderer.class})
public abstract class GameRendererMixin {
   @Shadow
   public abstract float getFarPlaneDistance();

   @Inject(
      method = {"getBasicProjectionMatrix"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void getBasicProjectionMatrix(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
      Matrix4f matrix4f = new Matrix4f();
      cir.cancel();
      float aspect = AspectRation.getProjectionAspect();
      cir.setReturnValue(matrix4f.perspective(fovDegrees * (float) (Math.PI / 180.0), aspect, 0.05F, this.getFarPlaneDistance()));
   }

   @ModifyReturnValue(method = { "getFov" }, at = { @At("RETURN") })
   private float zero$applyZoom(float original, Camera camera, float tickDelta, boolean changingFov) {
      if (Zero.get != null && Zero.get.manager != null) {
         Zoom zoom = Zero.get.manager.get(Zoom.class);
         if (zoom != null) {
            return zoom.modifyFov(original, tickDelta, changingFov);
         }
      }
      return original;
   }

   @ModifyArgs(
      method = "renderWorld",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"
      )
   )
   private void zero$overrideRenderBlockOutline(Args args) {
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

   @Inject(
      method = {"renderWorld"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
         shift = Shift.AFTER
      )}
   )
   private void renderWorld(RenderTickCounter renderTickCounter, CallbackInfo ci) {
      if (InformationHUD.mc.player != null && InformationHUD.mc.world != null) {
         Camera camera = InformationHUD.mc.gameRenderer.getCamera();
         MatrixStack matrixStack = new MatrixStack();
         RenderSystem.getModelViewStack().pushMatrix().mul(matrixStack.peek().getPositionMatrix());
         matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
         matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
         float tickDelta = InformationHUD.mc.getRenderTickCounter().getTickProgress(true);
         float fov = ((GameRendererAccessor)InformationHUD.mc.gameRenderer).invokeGetFov(camera, tickDelta, true);
         Mathf.lastProjMat.set(InformationHUD.mc.gameRenderer.getBasicProjectionMatrix(fov));
         Mathf.lastModMat.set(RenderSystem.getModelViewMatrix());
         Mathf.lastWorldSpaceMatrix.set(matrixStack.peek().getPositionMatrix());
         RenderSystem.getModelViewStack().popMatrix();
      }
   }

}
