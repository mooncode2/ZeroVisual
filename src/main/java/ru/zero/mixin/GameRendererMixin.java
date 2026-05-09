package ru.zero.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zero.Zero;
import ru.zero.module.impl.visuals.AspectRation;
import ru.zero.module.impl.visuals.CustomWorld;
import ru.zero.module.impl.visuals.HUD.InformationHUD;
import ru.zero.util.other.Mathf;

@Environment(EnvType.CLIENT)
@Mixin({GameRenderer.class})
public abstract class GameRendererMixin {
   @Shadow
   @Final
   private FogRenderer fogRenderer;

   @Shadow
   public abstract float getFarPlaneDistance();

   @Shadow
   public abstract float getSkyDarkness(float var1);

   @Inject(
      method = {"getBasicProjectionMatrix"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void getBasicProjectionMatrix(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
      Matrix4f matrix4f = new Matrix4f();
      cir.cancel();
      float aspect = (float)MinecraftClient.getInstance().getWindow().getFramebufferWidth() / MinecraftClient.getInstance().getWindow().getFramebufferHeight()
         + AspectRation.getAspectRation();
      cir.setReturnValue(matrix4f.perspective(fovDegrees * (float) (Math.PI / 180.0), aspect, 0.05F, this.getFarPlaneDistance()));
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

   @Redirect(
      method = {"renderWorld"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;"
      )
   )
   private Vector4f leet$applyFog(
      FogRenderer instance, Camera camera, int viewDistance, RenderTickCounter tickCounter, float skyDarkness, ClientWorld world
   ) {
      return Zero.get.manager.getModule(CustomWorld.class).enable && CustomWorld.useFog.get()
         ? this.fogRenderer
            .applyFog(
               camera,
               (int)(MinecraftClient.getInstance().options.getClampedViewDistance() * CustomWorld.fogDistance.get() * 5.0F),
               tickCounter,
               10.0F,
               MinecraftClient.getInstance().world
            )
         : this.fogRenderer
            .applyFog(
               camera, MinecraftClient.getInstance().options.getClampedViewDistance(), tickCounter, this.getSkyDarkness(skyDarkness), MinecraftClient.getInstance().world
            );
   }
}
