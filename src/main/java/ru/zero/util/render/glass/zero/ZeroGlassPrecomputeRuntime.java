package ru.zero.util.render.glass.zero;

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

public final class ZeroGlassPrecomputeRuntime {
   private static final ZeroGlassPrecomputeRuntime INSTANCE = new ZeroGlassPrecomputeRuntime();
   public static final int MAX_RADIUS = 192;
   private static final int DS = 2;
   private static final int BLUR_CONFIG_SIZE = 3104;
   private static final java.util.Map<Integer, float[]> GAUSSIAN_CACHE = new java.util.HashMap<>();
   private RenderPipeline blurPipeline;
   private GpuBuffer quadVBO;
   private GpuTexture blurTempTexture;
   private GpuTextureView blurTempView;
   private final HashMap<Integer, GpuTexture> blurredByRadius = new HashMap();
   private final HashMap<Integer, GpuTextureView> blurredViewByRadius = new HashMap();
   private GpuBuffer samplerInfoUbo;
   private final java.util.Map<Integer, GpuBuffer> blurConfigUboXByRadius = new java.util.HashMap<>();
   private final java.util.Map<Integer, GpuBuffer> blurConfigUboYByRadius = new java.util.HashMap<>();
   private List<Integer> requestedRadii = new ArrayList();
   private HashMap<Integer, int[]> bboxByRadius = new HashMap();
   private int lastBlurSignature = 0;
   private long lastBlurRunNanos = 0L;
   // Фон под стеклом обновляем не чаще ~30 раз в секунду: glass-backdrop меняется
   // медленно, а два полноэкранных blur-прохода на каждый радиус — самая дорогая часть.
   // При смене размера/набора виджетов blur пересчитывается немедленно (см. signature).
   private static final long BLUR_MIN_INTERVAL_NANOS = 33_000_000L;

   private ZeroGlassPrecomputeRuntime() {
   }

   public static ZeroGlassPrecomputeRuntime get() {
      return INSTANCE;
   }

   private void ensurePipelines() {
      if (this.blurPipeline == null) {
         this.blurPipeline = RenderPipeline.builder(new RenderPipeline.Snippet[0]).withLocation(Identifier.of("zero", "pipeline/blur")).withVertexShader(Identifier.of("zero", "core/blit_quad")).withFragmentShader(Identifier.of("zero", "program/blur")).withUniform("Projection", UniformType.UNIFORM_BUFFER).withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER).withUniform("Config", UniformType.UNIFORM_BUFFER).withSampler("DiffuseSampler").withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.QUADS).build();
         RenderSystem.getDevice().precompilePipeline(this.blurPipeline, (ShaderSourceGetter)null);
      }

      if (this.quadVBO == null) {
         MemoryStack stack = MemoryStack.stackPush();

         try {
            ByteBuffer buf = stack.malloc(48);
            FloatBuffer fb = buf.asFloatBuffer();
            fb.put(new float[]{-1.0F, -1.0F, 0.0F, 1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 0.0F, -1.0F, 1.0F, 0.0F});
            buf.rewind();
            this.quadVBO = RenderSystem.getDevice().createBuffer(() -> "zero quad vbo", 32, buf);
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
         this.samplerInfoUbo = RenderSystem.getDevice().createBuffer(() -> "zero SamplerInfo (pre)", 130, 16L);
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

         this.blurTempTexture = RenderSystem.getDevice().createTexture("zero blurTemp", 12, TextureFormat.RGBA8, width, height, 1, 1);
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

         GpuTexture newTexture = RenderSystem.getDevice().createTexture("zero blurred r=" + radius, 12, TextureFormat.RGBA8, width, height, 1, 1);
         GpuTextureView newView = RenderSystem.getDevice().createTextureView(newTexture);
         this.blurredByRadius.put(radius, newTexture);
         this.blurredViewByRadius.put(radius, newView);
      }

   }

   private static float[] gaussian(int radius) {
      radius = Math.max(0, Math.min(radius, 192));
      float[] cached = GAUSSIAN_CACHE.get(radius);
      if (cached != null) {
         return cached;
      }
      if (radius == 0) {
         float[] kernel = new float[]{1.0F};
         GAUSSIAN_CACHE.put(0, kernel);
         return kernel;
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

         GAUSSIAN_CACHE.put(radius, kernel);
         return kernel;
      }
   }

   private GpuBuffer getBlurConfigUbo(boolean xAxis, int radius) {
      java.util.Map<Integer, GpuBuffer> map = xAxis ? this.blurConfigUboXByRadius : this.blurConfigUboYByRadius;
      GpuBuffer existing = map.get(radius);
      if (existing != null) {
         return existing;
      }
      GpuBuffer ubo = RenderSystem.getDevice().createBuffer(() -> "zero BlurConfig " + (xAxis ? "X" : "Y") + " r=" + radius, 130, (long)BLUR_CONFIG_SIZE);
      this.uploadBlur(ubo, xAxis ? 1.0F : 0.0F, xAxis ? 0.0F : 1.0F, radius);
      map.put(radius, ubo);
      return ubo;
   }

   private void uploadBlur(GpuBuffer ubo, float dx, float dy, int radius) {
      radius = Math.max(0, Math.min(radius, 192));
      int effR = radius > 0 ? Math.max(1, radius / DS) : 0;
      float[] weights = gaussian(effR);
      GpuBuffer.MappedView map = RenderSystem.getDevice().createCommandEncoder().mapBuffer(ubo, false, true);

      try {
         Std140Builder builder = Std140Builder.intoBuffer(map.data());
         builder.putVec4(dx, dy, (float)effR, 0.0F);

         for(int i = 0; i <= 192; ++i) {
            builder.putFloat(i <= effR ? weights[i] : 0.0F);
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
      this.requestedRadii = ordered;
   }

   public void setBboxByRadius(HashMap<Integer, int[]> bbox) {
      this.bboxByRadius = bbox;
   }

   public GpuBuffer getQuadVBO() {
      return this.quadVBO;
   }

   // Предсоздаёт pipelin'ы, temp-target и blur-выходные текстуры + UBO для типичных
   // радиусов glass (18/24). Вызывается при включении Liquid Glass, чтобы первый кадр
   // GUI/HUD не получил аллокационный стечл (пару фреймов лага при открытии).
   public void warmup(int[] radii) {
      try {
         this.ensurePipelines();
         MinecraftClient client = MinecraftClient.getInstance();
         if (client == null || client.getFramebuffer() == null) {
            return;
         }
         int width = client.getFramebuffer().textureWidth;
         int height = client.getFramebuffer().textureHeight;
         if (width <= 0 || height <= 0) {
            return;
         }
         int halfW = Math.max(1, width / DS);
         int halfH = Math.max(1, height / DS);
         this.ensureTempTarget(halfW, halfH);
         for (int r : radii) {
            if (r > 0) {
               this.ensureOutputForRadius(halfW, halfH, r);
               this.getBlurConfigUbo(true, r);
               this.getBlurConfigUbo(false, r);
            }
         }
      } catch (Throwable ignored) {
      }
   }

   public void run() {
      this.ensurePipelines();
      MinecraftClient client = MinecraftClient.getInstance();
      Framebuffer mainFramebuffer = client.getFramebuffer();
      int width = mainFramebuffer.textureWidth;
      int height = mainFramebuffer.textureHeight;
      int halfW = Math.max(1, width / DS);
      int halfH = Math.max(1, height / DS);
      int signature = halfW * 31 + halfH * 17 + this.requestedRadii.hashCode();
      long now = System.nanoTime();
      boolean structuralChange = signature != this.lastBlurSignature || this.blurredViewByRadius.isEmpty();
      boolean timeElapsed = now - this.lastBlurRunNanos >= BLUR_MIN_INTERVAL_NANOS;
      if (!structuralChange && !timeElapsed) {
         return;
      }
      this.lastBlurSignature = signature;
      this.lastBlurRunNanos = now;
      this.ensureTempTarget(halfW, halfH);
      GpuBuffer.MappedView map = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.samplerInfoUbo, false, true);

      try {
         Std140Builder.intoBuffer(map.data()).putVec2((float)halfW, (float)halfH).putVec2((float)halfW, (float)halfH);
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
            this.ensureOutputForRadius(halfW, halfH, radius);
            GpuBuffer blurConfigUboX = this.getBlurConfigUbo(true, radius);
            GpuBuffer blurConfigUboY = this.getBlurConfigUbo(false, radius);
            int nPasses = radius >= 55 ? 2 : 1;
            int[] bbox = (int[])this.bboxByRadius.get(radius);
            int pad = (radius / DS) * nPasses + Math.round(0.15F * (float)halfH);
            int sx;
            int sy;
            int sw;
            int sh;
            if (bbox == null) {
               sx = 0;
               sy = 0;
               sw = halfW;
               sh = halfH;
            } else {
               int x0 = Math.max(0, bbox[0] / DS - pad);
               int y0 = Math.max(0, bbox[1] / DS - pad);
               int x1 = Math.min(halfW, (bbox[0] + bbox[2]) / DS + pad);
               int y1 = Math.min(halfH, (bbox[1] + bbox[3]) / DS + pad);
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
               RenderPass rp = ce.createRenderPass(() -> "zero blur X r=" + radius + " p=" + passIndex, this.blurTempView, OptionalInt.empty());

               try {
                   rp.setPipeline(this.blurPipeline);
                   RenderSystem.bindDefaultUniforms(rp);
                   rp.setUniform("SamplerInfo", this.samplerInfoUbo);
                   rp.setUniform("Config", blurConfigUboX);
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

               rp = ce.createRenderPass(() -> "zero blur Y r=" + radius + " p=" + passIndex, (GpuTextureView)this.blurredViewByRadius.get(radius), OptionalInt.empty());

               try {
                   rp.setPipeline(this.blurPipeline);
                   RenderSystem.bindDefaultUniforms(rp);
                   rp.setUniform("SamplerInfo", this.samplerInfoUbo);
                   rp.setUniform("Config", blurConfigUboY);
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
