package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.Zero;
import ru.zero.module.impl.visuals.ItemPhysics;

@Environment(EnvType.CLIENT)
@Mixin(ItemEntityRenderer.class)
public class ItemPhysicsMixin {

   @Inject(
      method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
      at = @At("RETURN")
   )
   private void fixUniqueOffset(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
      if (!itemPhysicsEnabled()) {
         return;
      }
      state.uniqueOffset = 0.0F;
   }

   @Redirect(
      method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V",
         ordinal = 0
      )
   )
   private void killBob(
      MatrixStack matrices,
      float x,
      float y,
      float z,
      ItemEntityRenderState state,
      MatrixStack matrices2,
      OrderedRenderCommandQueue queue,
      CameraRenderState cameraState
   ) {
      if (!itemPhysicsEnabled()) {
         matrices.translate(x, y, z);
         return;
      }
      matrices.translate(x, 0.0F, z);
   }

   @Redirect(
      method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V"
      )
   )
   private void replaceRotation(
      MatrixStack matrices,
      Quaternionfc rotation,
      ItemEntityRenderState state,
      MatrixStack matrices2,
      OrderedRenderCommandQueue queue,
      CameraRenderState cameraState
   ) {
      if (!itemPhysicsEnabled()) {
         matrices.multiply(rotation);
         return;
      }
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
   }

   private static boolean itemPhysicsEnabled() {
      if (Zero.get == null || Zero.get.manager == null) {
         return false;
      }
      ItemPhysics module = Zero.get.manager.get(ItemPhysics.class);
      return module != null && module.enable;
   }
}
