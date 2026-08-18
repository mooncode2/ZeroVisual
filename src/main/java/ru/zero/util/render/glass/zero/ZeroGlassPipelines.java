package ru.zero.util.render.glass.zero;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.ShaderSourceGetter;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class ZeroGlassPipelines {
   private static RenderPipeline guiPipeline;

   private ZeroGlassPipelines() {
   }

   public static synchronized RenderPipeline getGuiPipeline() {
      if (guiPipeline == null) {
         guiPipeline = RenderPipeline.builder(new RenderPipeline.Snippet[0]).withLocation(Identifier.of("zero", "pipeline/liquid_glass_gui")).withVertexShader(Identifier.of("zero", "core/blit_quad")).withFragmentShader(Identifier.of("zero", "program/liquid_glass_gui")).withUniform("Projection", UniformType.UNIFORM_BUFFER).withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER).withUniform("CustomUniforms", UniformType.UNIFORM_BUFFER).withUniform("WidgetInfo", UniformType.UNIFORM_BUFFER).withUniform("BgConfig", UniformType.UNIFORM_BUFFER).withSampler("Sampler0").withSampler("Sampler1").withSampler("Sampler2").withSampler("Sampler3").withSampler("Sampler4").withSampler("Sampler5").withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.QUADS).build();
         RenderSystem.getDevice().precompilePipeline(guiPipeline, (ShaderSourceGetter)null);
      }

      return guiPipeline;
   }
}
