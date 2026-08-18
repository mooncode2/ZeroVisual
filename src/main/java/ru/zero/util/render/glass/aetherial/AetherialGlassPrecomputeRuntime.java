package ru.zero.util.render.glass.aetherial;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.ShaderSourceGetter;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryStack;

public final class AetherialGlassPrecomputeRuntime {
   private static final AetherialGlassPrecomputeRuntime INSTANCE = new AetherialGlassPrecomputeRuntime();
   public static final int MAX_RADIUS = 192;
   private RenderPipeline blurPipeline;
   private GpuBuffer quadVBO;
   private GpuTexture blurTempTexture;
   private GpuTextureView blurTempView;
   private final HashMap<Integer, GpuTexture> blurredByRadius = new HashMap();
   private final HashMap<Integer, GpuTextureView> blurredViewByRadius = new HashMap();
   private GpuBuffer samplerInfoUbo;
   private GpuBuffer blurConfigUboX;
   private GpuBuffer blurConfigUboY;
   private List<Integer> requestedRadii = new ArrayList();
   private HashMap<Integer, int[]> bboxByRadius = new HashMap();

   private AetherialGlassPrecomputeRuntime() {
   }

   public static AetherialGlassPrecomputeRuntime get() {
      return INSTANCE;
   }

   private void ensurePipelines() {
      if (this.blurPipeline == null) {
         this.blurPipeline = RenderPipeline.builder(new RenderPipeline.Snippet[0]).withLocation(Identifier.of("aetherial", "pipeline/blur")).withVertexShader(Identifier.of("aetherial", "core/blit_quad")).withFragmentShader(Identifier.of("aetherial", "program/blur")).withUniform("Projection", UniformType.UNIFORM_BUFFER).withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER).withUniform("Config", UniformType.UNIFORM_BUFFER).withSampler("DiffuseSampler").withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.QUADS).build();
         RenderSystem.getDevice().precompilePipeline(this.blurPipeline, (ShaderSourceGetter)null);
      }

      if (this.quadVBO == null) {
         MemoryStack stack = MemoryStack.stackPush();

         try {
            ByteBuffer buf = stack.malloc(48);
            FloatBuffer fb = buf.asFloatBuffer();
            fb.put(new float[]{-1.0F, -1.0F, 0.0F, 1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 0.0F, -1.0F, 1.0F, 0.0F});
            buf.rewind();
            this.quadVBO = RenderSystem.getDevice().createBuffer(() -> "aetherial quad vbo", 32, buf);
         } catch (Throwable var5) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (stack != null) {
            stack.close();
         }
      }

      if (this.samplerInfoUbo == null) {
         this.samplerInfoUbo = RenderSystem.getDevice().createBuffer(() -> "aetherial SamplerInfo (pre)", 130, 16L);
      }

      int blurConfigSize = 3104;
      if (this.blurConfigUboX == null) {
         this.blurConfigUboX = RenderSystem.getDevice().createBuffer(() -> "aetherial BlurConfig X", 130, (long)blurConfigSize);
      }

      if (this.blurConfigUboY == null) {
         this.blurConfigUboY = RenderSystem.getDevice().createBuffer(() -> "aetherial BlurConfig Y", 130, (long)blurConfigSize);
      }

   }

   private void ensureTempTarget(int width, int height) {
      if (this.blurTempTexture == null || this.blurTempTexture.getWidth(0) != width || this.blurTempTexture.getHeight(0) != height) {
         if (this.blurTempTexture != null) {
            if (this.blurTempView != null) {
               this.blurTempView.close();
            }

            this.blurTempTexture.close();
         }

         this.blurTempTexture = RenderSystem.getDevice().createTexture("aetherial blurTemp", 12, TextureFormat.RGBA8, width, height, 1, 1);
         this.blurTempView = RenderSystem.getDevice().createTextureView(this.blurTempTexture);
      }

   }

   private void ensureOutputForRadius(int width, int height, int radius) {
      GpuTexture texture = (GpuTexture)this.blurredByRadius.get(radius);
      if (texture == null || texture.getWidth(0) != width || texture.getHeight(0) != height) {
         if (texture != null) {
            GpuTextureView oldView = (GpuTextureView)this.blurredViewByRadius.get(radius);
            if (oldView != null) {
               oldView.close();
            }

            texture.close();
         }

         GpuTexture newTexture = RenderSystem.getDevice().createTexture("aetherial blurred r=" + radius, 12, TextureFormat.RGBA8, width, height, 1, 1);
         GpuTextureView newView = RenderSystem.getDevice().createTextureView(newTexture);
         this.blurredByRadius.put(radius, newTexture);
         this.blurredViewByRadius.put(radius, newView);
      }

   }

   private static float[] gaussian(int radius) {
      radius = Math.max(0, Math.min(radius, 192));
      if (radius == 0) {
         return new float[]{1.0F};
      } else {
         float sigma = (float)radius / 2.0F;
         float[] kernel = new float[radius + 1];
         float sum = 0.0F;

         for(int i = 0; i <= radius; ++i) {
            float w = (float)Math.exp((double)(-0.5F * (float)i * (float)i / (sigma * sigma)));
            kernel[i] = w;
            sum += i == 0 ? w : 2.0F * w;
         }

         for(int i = 0; i <= radius; ++i) {
            kernel[i] /= sum;
         }

         return kernel;
      }
   }

   private void uploadBlur(GpuBuffer ubo, float dx, float dy, int radius) {
      radius = Math.max(0, Math.min(radius, 192));
      float[] weights = gaussian(radius);
      GpuBuffer.MappedView map = RenderSystem.getDevice().createCommandEncoder().mapBuffer(ubo, false, true);

      try {
         Std140Builder builder = Std140Builder.intoBuffer(map.data());
         builder.putVec4(dx, dy, (float)radius, 0.0F);

         for(int i = 0; i <= 192; ++i) {
            builder.putFloat(i <= radius ? weights[i] : 0.0F);
            builder.align(16);
         }
      } catch (Throwable var10) {
         if (map != null) {
            try {
               map.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }
         }

         throw var10;
      }

      if (map != null) {
         map.close();
      }

   }

   public void setRequestedRadii(List<Integer> ordered) {
      this.requestedRadii = new ArrayList(ordered);
   }

   public void setBboxByRadius(HashMap<Integer, int[]> bbox) {
      this.bboxByRadius = new HashMap(bbox);
   }

   public GpuBuffer getQuadVBO() {
      return this.quadVBO;
   }

   public void run() {
      this.ensurePipelines();
      MinecraftClient client = MinecraftClient.getInstance();
      Framebuffer mainFramebuffer = client.getFramebuffer();
      int width = mainFramebuffer.textureWidth;
      int height = mainFramebuffer.textureHeight;
      this.ensureTempTarget(width, height);
      GpuBuffer.MappedView map = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.samplerInfoUbo, false, true);

      try {
         Std140Builder.intoBuffer(map.data()).putVec2((float)width, (float)height).putVec2((float)width, (float)height);
      } catch (Throwable var35) {
         if (map != null) {
            try {
               map.close();
            } catch (Throwable var34) {
               var35.addSuppressed(var34);
            }
         }

         throw var35;
      }

      if (map != null) {
         map.close();
      }

      CommandEncoder ce = RenderSystem.getDevice().createCommandEncoder();
      GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
      GpuTextureView sourceView = mainFramebuffer.getColorAttachmentView();
      RenderSystem.ShapeIndexBuffer idxInfo = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
      GpuBuffer ib = idxInfo.getIndexBuffer(6);
      List<Integer> radii = this.requestedRadii.isEmpty() ? List.of(0) : this.requestedRadii;
      int max = Math.min(5, radii.size());

      for(int i = 0; i < max; ++i) {
         int radius = (Integer)radii.get(i);
         if (radius > 0) {
            this.ensureOutputForRadius(width, height, radius);
            this.uploadBlur(this.blurConfigUboX, 1.0F, 0.0F, radius);
            this.uploadBlur(this.blurConfigUboY, 0.0F, 1.0F, radius);
            int nPasses = radius >= 55 ? 2 : 1;
            int[] bbox = (int[])this.bboxByRadius.get(radius);
            int pad = radius * nPasses + Math.round(0.15F * (float)height);
            int sx;
            int sy;
            int sw;
            int sh;
            if (bbox == null) {
               sx = 0;
               sy = 0;
               sw = width;
               sh = height;
            } else {
               int x0 = Math.max(0, bbox[0] - pad);
               int y0 = Math.max(0, bbox[1] - pad);
               int x1 = Math.min(width, bbox[0] + bbox[2] + pad);
               int y1 = Math.min(height, bbox[1] + bbox[3] + pad);
               sx = x0;
               sy = y0;
               sw = Math.max(0, x1 - x0);
               sh = Math.max(0, y1 - y0);
            }

            int scX = sx;
            int scY = sy;
            int scW = sw;
            int scH = sh;
            GpuTextureView passSource = sourceView;

            for(int p = 0; p < nPasses; ++p) {
               int passIndex = p;
               GpuTextureView src = passSource;
               RenderPass rp = ce.createRenderPass(() -> "aetherial blur X r=" + radius + " p=" + passIndex, this.blurTempView, OptionalInt.empty());

               try {
                  rp.setPipeline(this.blurPipeline);
                  RenderSystem.bindDefaultUniforms(rp);
                  rp.setUniform("SamplerInfo", this.samplerInfoUbo);
                  rp.setUniform("Config", this.blurConfigUboX);
                  rp.bindTexture("DiffuseSampler", src, linear);
                  if (scW > 0 && scH > 0) {
                     rp.enableScissor(scX, scY, scW, scH);
                  }

                  rp.setVertexBuffer(0, this.quadVBO);
                  rp.setIndexBuffer(ib, idxInfo.getIndexType());
                  rp.drawIndexed(0, 0, 6, 1);
               } catch (Throwable var37) {
                  if (rp != null) {
                     try {
                        rp.close();
                     } catch (Throwable var33) {
                        var37.addSuppressed(var33);
                     }
                  }

                  throw var37;
               }

               if (rp != null) {
                  rp.close();
               }

               rp = ce.createRenderPass(() -> "aetherial blur Y r=" + radius + " p=" + passIndex, (GpuTextureView)this.blurredViewByRadius.get(radius), OptionalInt.empty());

               try {
                  rp.setPipeline(this.blurPipeline);
                  RenderSystem.bindDefaultUniforms(rp);
                  rp.setUniform("SamplerInfo", this.samplerInfoUbo);
                  rp.setUniform("Config", this.blurConfigUboY);
                  rp.bindTexture("DiffuseSampler", this.blurTempView, linear);
                  if (scW > 0 && scH > 0) {
                     rp.enableScissor(scX, scY, scW, scH);
                  }

                  rp.setVertexBuffer(0, this.quadVBO);
                  rp.setIndexBuffer(ib, idxInfo.getIndexType());
                  rp.drawIndexed(0, 0, 6, 1);
               } catch (Throwable var36) {
                  if (rp != null) {
                     try {
                        rp.close();
                     } catch (Throwable var32) {
                        var36.addSuppressed(var32);
                     }
                  }

                  throw var36;
               }

               if (rp != null) {
                  rp.close();
               }

               passSource = (GpuTextureView)this.blurredViewByRadius.get(radius);
            }
         }
      }

   }

   public GpuTextureView getBlurredViewForRadius(int radius) {
      return (GpuTextureView)this.blurredViewByRadius.get(radius);
   }
}
