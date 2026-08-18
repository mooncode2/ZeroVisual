package ru.zero.mixin.glass;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.util.render.glass.zero.ZeroGlassPipelines;
import ru.zero.util.render.glass.zero.ZeroGlassPrecomputeRuntime;
import ru.zero.util.render.glass.zero.ZeroGlassUniforms;

@Mixin({GameRenderer.class})
public abstract class GameRendererMixin {
   @Shadow
   private MinecraftClient client;

   @Inject(
      method = {"render"},
      at = {@At("HEAD")}
   )
   private void zero$beginGlassFrame(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
      double deltaTicks;
      try {
         deltaTicks = (double)tickCounter.getDynamicDeltaTicks();
      } catch (Throwable var7) {
         deltaTicks = 0.3333333333333333;
      }

      ZeroGlassUniforms.get().beginFrame(deltaTicks / (double)20.0F);
   }

   @Inject(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
         ordinal = 0
      )}
   )
   private void zero$drawGlassUnderHud(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
      ZeroGlassUniforms uniforms = ZeroGlassUniforms.get();
      if (uniforms.getCount() > 0) {
         this.drawGlassComposite(uniforms);
      }

      uniforms.clearWidgets();
   }

   @Inject(
      method = {"renderBlur"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void zero$cancelVanillaBlur(CallbackInfo ci) {
      if (ZeroGlassUniforms.get().getCount() > 0) {
         ci.cancel();
      }
   }

   private void drawGlassComposite(ZeroGlassUniforms uniforms) {
      uniforms.uploadSharedUniforms();
      uniforms.uploadWidgetInfo();
      List<Integer> radii = uniforms.getUsedBlurRadiiOrdered();
      ZeroGlassPrecomputeRuntime.get().setRequestedRadii(radii);
      ZeroGlassPrecomputeRuntime.get().setBboxByRadius(uniforms.getBboxByRadius());
      ZeroGlassPrecomputeRuntime.get().run();
      Framebuffer mainFramebuffer = this.client.getFramebuffer();
      GpuSampler linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
      GpuBuffer quadVB = ZeroGlassPrecomputeRuntime.get().getQuadVBO();
      RenderSystem.ShapeIndexBuffer quadIdxInfo = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
      GpuBuffer quadIB = quadIdxInfo.getIndexBuffer(6);
      RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "zero liquid glass pass", mainFramebuffer.getColorAttachmentView(), OptionalInt.empty(), mainFramebuffer.useDepthAttachment ? mainFramebuffer.getDepthAttachmentView() : null, OptionalDouble.empty());

      try {
         RenderPipeline pipeline = ZeroGlassPipelines.getGuiPipeline();
         pass.setPipeline(pipeline);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("SamplerInfo", uniforms.getSamplerInfoBuffer());
         pass.setUniform("CustomUniforms", uniforms.getCustomUniformsBuffer());
         pass.setUniform("WidgetInfo", uniforms.getWidgetInfoBuffer());
         pass.setUniform("BgConfig", uniforms.getBgConfigBuffer());
         pass.bindTexture("Sampler0", mainFramebuffer.getColorAttachmentView(), linearSampler);

         int[] scissor = uniforms.getCompositeScissor();
         if (scissor != null && scissor[2] > 0 && scissor[3] > 0) {
            pass.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
         }

         for(int i = 0; i < 5; ++i) {
            String var10000;
            switch (i) {
               case 0 -> var10000 = "Sampler1";
               case 1 -> var10000 = "Sampler2";
               case 2 -> var10000 = "Sampler3";
               case 3 -> var10000 = "Sampler4";
               default -> var10000 = "Sampler5";
            }

            String samplerName = var10000;
            if (i < radii.size()) {
               int radius = (Integer)radii.get(i);
               if (radius <= 0) {
                  pass.bindTexture(samplerName, mainFramebuffer.getColorAttachmentView(), linearSampler);
               } else {
                  pass.bindTexture(samplerName, ZeroGlassPrecomputeRuntime.get().getBlurredViewForRadius(radius), linearSampler);
               }
            } else if (!radii.isEmpty()) {
               int radius = (Integer)radii.getFirst();
               if (radius <= 0) {
                  pass.bindTexture(samplerName, mainFramebuffer.getColorAttachmentView(), linearSampler);
               } else {
                  pass.bindTexture(samplerName, ZeroGlassPrecomputeRuntime.get().getBlurredViewForRadius(radius), linearSampler);
               }
            } else {
               pass.bindTexture(samplerName, mainFramebuffer.getColorAttachmentView(), linearSampler);
            }
         }

         pass.setVertexBuffer(0, quadVB);
         pass.setIndexBuffer(quadIB, quadIdxInfo.getIndexType());
         pass.drawIndexed(0, 0, 6, 1);
      } catch (Throwable var15) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var14) {
               var15.addSuppressed(var14);
            }
         }

         throw var15;
      }

      if (pass != null) {
         pass.close();
      }

   }
}
