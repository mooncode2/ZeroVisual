package ru.zero.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.render.fog.FogModifier;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zero.Zero;
import ru.zero.module.impl.utils.Optimizer;
import ru.zero.module.impl.visuals.CustomWorld;
import ru.zero.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin({FogRenderer.class})
public abstract class FogRendererMixin {
   @Shadow
   @Final
   private MappableRingBuffer fogBuffer;
   @Shadow
   @Final
   private static List<FogModifier> FOG_MODIFIERS;

   @Shadow
   protected abstract void applyFog(ByteBuffer var1, int var2, Vector4f var3, float var4, float var5, float var6, float var7, float var8, float var9);

   @Shadow
   protected abstract Vector4f getFogColor(Camera var1, float var2, ClientWorld var3, int var4, float var5);

   @Shadow
   protected abstract CameraSubmersionType getCameraSubmersionType(Camera var1);

   @Inject(
      method = {"getFogColor"},
      at = {@At("TAIL")},
      cancellable = true
   )
   private void night$getFogColor(
      Camera camera, float tickProgress, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir
   ) {
      if (Zero.get.manager.getModule(CustomWorld.class).enable && CustomWorld.useFog.get()) {
         Color c = CustomWorld.getResolvedFogColor();
         cir.setReturnValue(new Vector4f(c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F, c.getAlpha() / 255.0F));
      }
   }

   @Inject(
      method = {"applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void night$applyFog(
      Camera camera, int viewDistance, RenderTickCounter tickCounter, float skyDarkness, ClientWorld world, CallbackInfoReturnable<Vector4f> cir
   ) {
      if (Optimizer.shouldDisableFog()) {
         cir.cancel();
         float f = tickCounter.getTickProgress(false);
         Vector4f vector4f = this.getFogColor(camera, f, world, viewDistance, skyDarkness);
         float far = Math.max(1024.0F, viewDistance * 64.0F);
         MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.fogBuffer.getBlocking(), false, true);

         try {
            this.applyFog(mappedView.data(), 0, vector4f, far, far, far, far, far, far);
         } catch (Throwable var24) {
            if (mappedView != null) {
               try {
                  mappedView.close();
               } catch (Throwable var23) {
                  var24.addSuppressed(var23);
               }
            }

            throw var24;
         }

         if (mappedView != null) {
            mappedView.close();
         }

         cir.setReturnValue(vector4f);
         return;
      }

      if (Zero.get != null && Zero.get.manager != null) {
         NoRender noRender = Zero.get.manager.get(NoRender.class);
         if (noRender != null && noRender.enable && NoRender.fog.get()) {
            cir.cancel();
            float f = tickCounter.getTickProgress(false);
            Vector4f vector4f = this.getFogColor(camera, f, world, viewDistance, skyDarkness);
            float far = Math.max(1024.0F, viewDistance * 64.0F);
            MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.fogBuffer.getBlocking(), false, true);

            try {
               this.applyFog(mappedView.data(), 0, vector4f, far, far, far, far, far, far);
            } catch (Throwable var22) {
               if (mappedView != null) {
                  try {
                     mappedView.close();
                  } catch (Throwable var21) {
                     var22.addSuppressed(var21);
                  }
               }

               throw var22;
            }

            if (mappedView != null) {
               mappedView.close();
            }

            cir.setReturnValue(vector4f);
            return;
         }
      }

      if (CustomWorld.useFog.get() && Zero.get.manager.getModule(CustomWorld.class).enable) {
         cir.cancel();
         float fac = CustomWorld.fogDistance.get();
         float f = tickCounter.getTickProgress(false);
         Vector4f vector4f = this.getFogColor(camera, f, world, viewDistance, skyDarkness);
         float g = viewDistance * 16;
         CameraSubmersionType cameraSubmersionType = this.getCameraSubmersionType(camera);
         Entity entity = camera.getFocusedEntity();
         FogData fogData = new FogData();

         for (FogModifier fogModifier : FOG_MODIFIERS) {
            if (fogModifier.shouldApply(cameraSubmersionType, entity)) {
               fogModifier.applyStartEndModifier(fogData, camera, world, g, tickCounter);
               break;
            }
         }

         float h = MathHelper.clamp(g / 10.0F, 4.0F, 64.0F);
         fogData.renderDistanceStart = g - h;
         fogData.renderDistanceEnd = g;
         MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.fogBuffer.getBlocking(), false, true);

         try {
            this.applyFog(
               mappedView.data(),
               0,
               vector4f,
               fogData.environmentalStart * fac,
               fogData.environmentalEnd * fac,
               fogData.renderDistanceStart * fac,
               fogData.renderDistanceEnd * fac,
               fogData.skyEnd * fac,
               fogData.cloudEnd * fac
            );
         } catch (Throwable var20) {
            if (mappedView != null) {
               try {
                  mappedView.close();
               } catch (Throwable var19) {
                  var20.addSuppressed(var19);
               }
            }

            throw var20;
         }

         if (mappedView != null) {
            mappedView.close();
         }

         cir.setReturnValue(vector4f);
      }
   }
}
