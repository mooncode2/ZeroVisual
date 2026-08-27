package ru.zero.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.FramePass;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.event.EventManager;
import ru.zero.event.render.WorldRenderEvent;
import ru.zero.util.render.capture.EntityFramebufferCaptureManager;
import ru.zero.util.render.sky.EndSkyboxRenderer;

@Environment(EnvType.CLIENT)
@Mixin({ WorldRenderer.class })
public class WorldRendererMixin {
   @Inject(method = { "renderSky" }, at = { @At("HEAD") }, cancellable = true)
   private void zero$replaceVanillaSky(FrameGraphBuilder frameGraph, Camera camera, GpuBufferSlice fog, CallbackInfo ci) {
      if (!EndSkyboxRenderer.shouldReplaceSky()) {
         return;
      }

      //Повторяем ванильные проверки видимости неба
      CameraSubmersionType submersion = camera.getSubmersionType();
      if (submersion == CameraSubmersionType.POWDER_SNOW || submersion == CameraSubmersionType.LAVA) {
         return;
      }

      Entity focusedEntity = camera.getFocusedEntity();
      if (focusedEntity instanceof LivingEntity living
            && (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS))) {
         return;
      }

      //Создаём свой sky-пасс вместо ванильного: тот же transfer главного фреймбуфера
      DefaultFramebufferSet framebuffers = ((WorldRendererAccessor) (Object) this).zero$getFramebufferSet();
      FramePass pass = frameGraph.createPass("zero_sky");
      framebuffers.mainFramebuffer = pass.transfer(framebuffers.mainFramebuffer);
      pass.setRenderer(EndSkyboxRenderer::drawSkyPass);
      ci.cancel();
   }

   @Inject(method = { "render" }, at = { @At("HEAD") })
   private void beginEntityCapture(
         ObjectAllocator allocator,
         RenderTickCounter tickCounter,
         boolean renderBlockOutline,
         Camera camera,
         Matrix4f positionMatrix,
         Matrix4f basicProjectionMatrix,
         Matrix4f projectionMatrix,
         GpuBufferSlice fog,
         Vector4f fogColor,
         boolean shouldRenderSky,
         CallbackInfo ci) {
      EntityFramebufferCaptureManager captureManager = EntityFramebufferCaptureManager.getInstance();
      if (captureManager.isEnabled()) {
         captureManager.beginFrame((WorldRenderer) (Object) this, tickCounter, camera);
      }
   }

   @Inject(method = { "render" }, at = { @At("RETURN") })
   private void publishWorldRenderEvent(
         ObjectAllocator allocator,
         RenderTickCounter tickCounter,
         boolean renderBlockOutline,
         Camera camera,
         Matrix4f positionMatrix,
         Matrix4f basicProjectionMatrix,
         Matrix4f projectionMatrix,
         GpuBufferSlice fog,
         Vector4f fogColor,
         boolean shouldRenderSky,
         CallbackInfo ci) {
      EntityFramebufferCaptureManager captureManager = EntityFramebufferCaptureManager.getInstance();
      if (captureManager.isEnabled()) {
         captureManager.endFrame();
      }

      if (!EventManager.hasListeners(WorldRenderEvent.class)) {
         return;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.world == null || client.player == null) {
         return;
      }

      GameRenderer gameRenderer = client.gameRenderer;
      if (camera == null || gameRenderer == null) {
         return;
      }

      ru.zero.util.render.world.WorldRenderer worldRenderer = null;

      try {
         worldRenderer = ru.zero.util.render.world.WorldRenderer.begin(
               client, tickCounter, camera, positionMatrix, projectionMatrix);

         try {
            EventManager.call(
                  new WorldRenderEvent(client, gameRenderer, worldRenderer, worldRenderer.tickDelta()));
         } finally {
            if (worldRenderer != null) {
               try {
                  worldRenderer.flush();
               } finally {
                  worldRenderer.close();
               }
            }
         }
      } catch (RuntimeException e) {
         System.err.println("[Zero] World render event failed: " + e.getMessage());
         e.printStackTrace();
      }
   }
}
