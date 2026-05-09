package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zero.Zero;
import ru.zero.module.impl.visuals.NameTags;
import ru.zero.util.render.capture.EntityFramebufferCaptureManager;

@Environment(EnvType.CLIENT)
@Mixin({EntityRenderer.class})
public abstract class EntityRendererMixin<S extends EntityRenderState> {
   @Inject(
      method = {"renderLabelIfPresent"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void renderLabelIfPresent(S state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo ci) {
      if (Zero.get != null
         && Zero.get.manager != null
         && Zero.get.manager.get(NameTags.class) != null
         && Zero.get.manager.get(NameTags.class).enable) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderLabelIfPresent"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void skipLabelDuringCapture(EntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo ci) {
      if (EntityFramebufferCaptureManager.getInstance().isExecutingCapturePass()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"hasLabel"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void hasLabel(Entity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
      if (Zero.get != null
         && Zero.get.manager != null
         && Zero.get.manager.get(NameTags.class) != null
         && Zero.get.manager.get(NameTags.class).enable) {
         cir.setReturnValue(false);
      }
   }
}
