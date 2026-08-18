package ru.zero.util.render.glass.aetherial;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.util.math.ColorHelper;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

public final class AetherialGlassUniforms {
   public static final int MAX_WIDGETS = 64;
   public static final int MAX_BLUR_LEVELS = 5;
   private static final boolean VULKAN_GLASS = Boolean.getBoolean("zero.glass.vulkan");
   private static final AetherialGlassUniforms INSTANCE = new AetherialGlassUniforms();
   private final GpuBuffer samplerInfo = RenderSystem.getDevice().createBuffer(() -> "aetherial SamplerInfo", 130, 16L);
   private final GpuBuffer customUniforms;
   private final GpuBuffer widgetInfo;
   private final GpuBuffer bgConfig;
   // std140 payloads mirrored into direct buffers so the Vulkan glass path can upload
   // them to Vulkan UBOs. The GL path copies these into its mapped GpuBuffers.
   private final java.nio.ByteBuffer samplerInfoData =
         java.nio.ByteBuffer.allocateDirect(16).order(java.nio.ByteOrder.nativeOrder());
   private final java.nio.ByteBuffer customData =
         java.nio.ByteBuffer.allocateDirect(256).order(java.nio.ByteOrder.nativeOrder());
   private final java.nio.ByteBuffer widgetData =
         java.nio.ByteBuffer.allocateDirect(12304).order(java.nio.ByteOrder.nativeOrder());
   private final java.nio.ByteBuffer bgData =
         java.nio.ByteBuffer.allocateDirect(16).order(java.nio.ByteOrder.nativeOrder());
   private int samplerInfoSize = 16;
   private int customSize = 0;
   private int widgetSize = 12304;
   private int bgSize = 16;
   private final List<AetherialGlassElementRenderState> hudWidgets = new ArrayList();
   private final List<AetherialGlassElementRenderState> guiWidgets = new ArrayList();
   private List<AetherialGlassElementRenderState> widgets = this.hudWidgets;
   private boolean guiPhase = false;
   private float guiBaseX = 0.0F;
   private float guiBaseY = 0.0F;
   private float guiCurrentX = 0.0F;
   private float guiCurrentY = 0.0F;
   private boolean guiDeltaActive = false;
   private final HashMap<Integer, Integer> blurRadiusToIndex = new HashMap();
   private final List<Integer> usedBlurRadiiOrdered = new ArrayList();
   private final HashMap<Integer, int[]> bboxByRadius = new HashMap();
   private boolean screenWantsBlur = false;
   private double dtSeconds = (double)0.0F;

   private AetherialGlassUniforms() {
      Std140SizeCalculator calc = new Std140SizeCalculator();
      calc.putFloat();
      calc.align(16);
      calc.putVec4();
      calc.putFloat();
      calc.align(16);
      calc.putVec3();
      calc.align(16);
      calc.putVec4();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      this.customUniforms = RenderSystem.getDevice().createBuffer(() -> "aetherial CustomUniforms", 130, (long)calc.get());
      this.widgetInfo = RenderSystem.getDevice().createBuffer(() -> "aetherial WidgetInfo", 130, 12304L);
      Std140SizeCalculator bgCalc = new Std140SizeCalculator();
      bgCalc.putFloat();
      bgCalc.putFloat();
      bgCalc.putVec2();
      this.bgConfig = RenderSystem.getDevice().createBuffer(() -> "aetherial BgConfig", 130, (long)bgCalc.get());
   }

   public static AetherialGlassUniforms get() {
      return INSTANCE;
   }

   public static boolean isVulkanGlassEnabled() {
      return VULKAN_GLASS;
   }

   public java.nio.ByteBuffer samplerInfoData() {
      this.samplerInfoData.position(0).limit(this.samplerInfoSize);
      return this.samplerInfoData;
   }

   public java.nio.ByteBuffer customData() {
      this.customData.position(0).limit(this.customSize);
      return this.customData;
   }

   public java.nio.ByteBuffer widgetData() {
      this.widgetData.position(0).limit(this.widgetSize);
      return this.widgetData;
   }

   public java.nio.ByteBuffer bgData() {
      this.bgData.position(0).limit(this.bgSize);
      return this.bgData;
   }

   public void beginFrame(double dtSeconds) {
      this.dtSeconds = Math.max((double)0.0F, dtSeconds);
   }

   public void clearWidgets() {
      this.hudWidgets.clear();
      this.guiWidgets.clear();
      this.screenWantsBlur = false;
      this.usedBlurRadiiOrdered.clear();
      this.blurRadiusToIndex.clear();
      this.bboxByRadius.clear();
   }

   public void setGuiPhase(boolean guiPhase) {
      this.guiPhase = guiPhase;
   }

   // GUI glass composited with prev-frame widget positions lags one frame behind
   // the panel (which uses the current GuiScreen.x/y). During the open/close slide
   // the glass and panel diverge → visible shake. Store the GuiScreen position at
   // widget-registration time (setGuiBase) and at composite time (setGuiCurrent);
   // the delta is applied in uploadWidgetInfo so the glass snaps to the current
   // panel position. Per-element offsets (scroll/settings) change slowly enough
   // that their residual lag is imperceptible.
   public void setGuiBase(float x, float y) {
      this.guiBaseX = x;
      this.guiBaseY = y;
   }

   public void setGuiCurrent(float x, float y) {
      this.guiCurrentX = x;
      this.guiCurrentY = y;
   }

   public HashMap<Integer, int[]> getBboxByRadius() {
      return this.bboxByRadius;
   }

   public void addWidget(AetherialGlassElementRenderState element) {
      List<AetherialGlassElementRenderState> list = this.guiPhase ? this.guiWidgets : this.hudWidgets;
      if (list.size() < 64) {
         list.add(element);
      }

   }

   public void compositeHud() {
      if (!this.hudWidgets.isEmpty()) {
         this.widgets = this.hudWidgets;
         this.runComposite();
         this.hudWidgets.clear();
      }

   }

   public void compositeGui() {
      if (!this.guiWidgets.isEmpty()) {
         this.widgets = this.guiWidgets;
         this.guiDeltaActive = true;
         this.runComposite();
         this.guiDeltaActive = false;
         this.guiWidgets.clear();
      }

   }

    // Vulkan glass path (opt-in via -Dzero.glass.vulkan=true). Captures the MC
    // framebuffer into a Vulkan backdrop image, runs blur + glass composite on Vulkan,
    // and blits the result (via the GL/VK interop shared texture) back into the MC
    // framebuffer. Returns true if the Vulkan path handled the frame; false falls back
    // to the GL composite path.
    private boolean tryVulkanComposite() {
       ru.zero.util.render.backends.vk.VulkanGlassCoordinator coord = ru.zero.Zero.getGlassCoordinator();
       if (coord == null || !coord.isAvailable()) {
          return false;
       }
       MinecraftClient client = MinecraftClient.getInstance();
       Framebuffer mainFramebuffer = client.getFramebuffer();
       int w = mainFramebuffer.textureWidth;
       int h = mainFramebuffer.textureHeight;
       if (w <= 0 || h <= 0) {
          return false;
       }
       try {
          // Capture the MC framebuffer content (currently bound) into a direct buffer
          // for the Vulkan backdrop image upload.
          java.nio.ByteBuffer pixels = ru.zero.util.render.backends.vk.VulkanGlassCoordinator.captureFramebuffer(w, h);
          int glTex = coord.composite(w, h, pixels, this.samplerInfoData(), this.customData(),
                this.widgetData(), this.bgData(), this.usedBlurRadiiOrdered, this.bboxByRadius);
          if (glTex == 0) {
             return false;
          }
          // Blit the Vulkan-composited GL texture over the MC framebuffer. The GL/VK
          // interop shared memory + semaphore sync make the Vulkan writes visible.
          ru.zero.util.render.backends.gl.GlBackend gl = ru.zero.Zero.getGlBackend();
          if (gl != null) {
             gl.drawFullscreenTexture(glTex, w, h);
          }
          return true;
       } catch (Throwable t) {
          System.err.println("[Zero/Vulkan] tryVulkanComposite failed, GL fallback: " + t.getMessage());
          t.printStackTrace();
          return false;
       }
    }

    private void runComposite() {
       this.uploadSharedUniforms();
       this.uploadWidgetInfo();
       if (VULKAN_GLASS && this.tryVulkanComposite()) {
          return;
       }
       List<Integer> radii = this.usedBlurRadiiOrdered;
       AetherialGlassPrecomputeRuntime.get().setRequestedRadii(radii);
       AetherialGlassPrecomputeRuntime.get().setBboxByRadius(this.bboxByRadius);
       AetherialGlassPrecomputeRuntime.get().run();
       MinecraftClient client = MinecraftClient.getInstance();
       Framebuffer mainFramebuffer = client.getFramebuffer();
       GpuSampler linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
       GpuBuffer quadVB = AetherialGlassPrecomputeRuntime.get().getQuadVBO();
       RenderSystem.ShapeIndexBuffer quadIdxInfo = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
      GpuBuffer quadIB = quadIdxInfo.getIndexBuffer(6);
      RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "aetherial liquid glass pass", mainFramebuffer.getColorAttachmentView(), OptionalInt.empty(), mainFramebuffer.useDepthAttachment ? mainFramebuffer.getDepthAttachmentView() : null, OptionalDouble.empty());

      try {
         RenderPipeline pipeline = AetherialGlassPipelines.getGuiPipeline();
         pass.setPipeline(pipeline);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("SamplerInfo", this.samplerInfo);
         pass.setUniform("CustomUniforms", this.customUniforms);
         pass.setUniform("WidgetInfo", this.widgetInfo);
         pass.setUniform("BgConfig", this.bgConfig);
         pass.bindTexture("Sampler0", mainFramebuffer.getColorAttachmentView(), linearSampler);

         for(int i = 0; i < 5; ++i) {
            String samplerName;
            switch (i) {
               case 0 -> samplerName = "Sampler1";
               case 1 -> samplerName = "Sampler2";
               case 2 -> samplerName = "Sampler3";
               case 3 -> samplerName = "Sampler4";
               default -> samplerName = "Sampler5";
            }

            if (i < radii.size()) {
               int radius = (Integer)radii.get(i);
               if (radius <= 0) {
                  pass.bindTexture(samplerName, mainFramebuffer.getColorAttachmentView(), linearSampler);
               } else {
                  pass.bindTexture(samplerName, AetherialGlassPrecomputeRuntime.get().getBlurredViewForRadius(radius), linearSampler);
               }
            } else if (!radii.isEmpty()) {
               int radius = (Integer)radii.getFirst();
               if (radius <= 0) {
                  pass.bindTexture(samplerName, mainFramebuffer.getColorAttachmentView(), linearSampler);
               } else {
                  pass.bindTexture(samplerName, AetherialGlassPrecomputeRuntime.get().getBlurredViewForRadius(radius), linearSampler);
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

   public void uploadSharedUniforms() {
      MinecraftClient client = MinecraftClient.getInstance();
      int outWidth = client.getFramebuffer().textureWidth;
      int outHeight = client.getFramebuffer().textureHeight;
      double[] mouseX = new double[1];
      double[] mouseY = new double[1];
      GLFW.glfwGetCursorPos(client.getWindow().getHandle(), mouseX, mouseY);
      float scale = (float)client.getWindow().getScaleFactor();
      int framebufferHeight = client.getFramebuffer().textureHeight;

      // Build SamplerInfo (vec2 OutSize + vec2 InSize = 16 bytes)
      this.samplerInfoData.clear();
      Std140Builder.intoBuffer(this.samplerInfoData)
            .putVec2((float)outWidth, (float)outHeight)
            .putVec2((float)outWidth, (float)outHeight);
      this.samplerInfoSize = this.samplerInfoData.position();
      this.copyToGl(this.samplerInfo, this.samplerInfoData, this.samplerInfoSize);

      // Build CustomUniforms
      this.customData.clear();
      float scaledMouseX = (float)(mouseX[0] * (double)scale);
      float scaledMouseY = (float)framebufferHeight - (float)(mouseY[0] * (double)scale);
      Std140Builder b = Std140Builder.intoBuffer(this.customData);
      b.putFloat((float)GLFW.glfwGetTime());
      b.align(16);
      b.putVec4(new Vector4f(scaledMouseX, scaledMouseY, 0.0F, 0.0F));
      b.putFloat(this.screenWantsBlur ? 1.0F : 0.0F);
      b.align(16);
      b.putVec3(new Vector3f(0.55F, -0.45F, 0.0F));
      b.align(16);
      b.putVec4(1.0F, 1.0F, 1.0F, 0.08F);
      b.putFloat(0.75F);
      b.putFloat(2.0F);
      b.putFloat(0.0F);
      b.putFloat(8.0F);
      b.putFloat(0.0F);
      b.putFloat(0.0F);
      b.putFloat(0.0F);
      b.putFloat(0.0F);
      b.putFloat((float)this.dtSeconds);
      this.customSize = this.customData.position();
      this.copyToGl(this.customUniforms, this.customData, this.customSize);

      // Build BgConfig
      this.bgData.clear();
      Std140Builder.intoBuffer(this.bgData)
            .putFloat(20.0F)
            .putFloat(0.85F)
            .putVec2(0.0F, 2.0F * scale);
      this.bgSize = this.bgData.position();
      this.copyToGl(this.bgConfig, this.bgData, this.bgSize);
   }

   private void copyToGl(GpuBuffer glBuf, java.nio.ByteBuffer src, int size) {
      GpuBuffer.MappedView map = RenderSystem.getDevice().createCommandEncoder().mapBuffer(glBuf, false, true);
      try {
         java.nio.ByteBuffer dst = map.data();
         int savedPos = src.position();
         int savedLimit = src.limit();
         src.position(0).limit(size);
         dst.clear();
         dst.put(src);
         src.position(savedPos).limit(savedLimit);
      } catch (Throwable var15) {
         if (map != null) {
            try {
               map.close();
            } catch (Throwable var14) {
               var15.addSuppressed(var14);
            }
         }
         throw var15;
      }
      if (map != null) {
         map.close();
      }
   }

   public void uploadWidgetInfo() {
      MinecraftClient client = MinecraftClient.getInstance();
      int framebufferHeight = client.getFramebuffer().textureHeight;
      int framebufferWidth = client.getFramebuffer().textureWidth;
      float scale = (float)client.getWindow().getScaleFactor();
      float dx = this.guiDeltaActive ? (this.guiCurrentX - this.guiBaseX) : 0.0F;
      float dy = this.guiDeltaActive ? (this.guiCurrentY - this.guiBaseY) : 0.0F;
      HashSet<Integer> requested = new HashSet();

      for(AetherialGlassElementRenderState widget : this.widgets) {
         requested.add(Math.max(0, widget.style().getBlurRadius()));
      }

      this.bboxByRadius.clear();

      for(AetherialGlassElementRenderState widget : this.widgets) {
         int radius = Math.max(0, widget.style().getBlurRadius());
         if (radius > 0) {
            float width = (float)(widget.x2() - widget.x1());
            float height = (float)(widget.y2() - widget.y1());
            float px = (float)(widget.x1() + dx) * scale;
            float pyTop = (float)(widget.y1() + dy) * scale;
            float scaledWidth = width * scale;
            float scaledHeight = height * scale;
            int x0 = (int)Math.floor((double)px);
            int y0 = (int)Math.floor((double)((float)framebufferHeight - (pyTop + scaledHeight)));
            int x1 = (int)Math.ceil((double)(px + scaledWidth));
            int y1 = (int)Math.ceil((double)((float)framebufferHeight - pyTop));
            int[] existing = (int[])this.bboxByRadius.get(radius);
            if (existing == null) {
               this.bboxByRadius.put(radius, new int[]{x0, y0, x1 - x0, y1 - y0});
            } else {
               int ex0 = existing[0];
               int ey0 = existing[1];
               int ex1 = ex0 + existing[2];
               int ey1 = ey0 + existing[3];
               int ux0 = Math.min(ex0, x0);
               int uy0 = Math.min(ey0, y0);
               int ux1 = Math.max(ex1, x1);
               int uy1 = Math.max(ey1, y1);
               existing[0] = ux0;
               existing[1] = uy0;
               existing[2] = ux1 - ux0;
               existing[3] = uy1 - uy0;
            }
         }
      }

      this.usedBlurRadiiOrdered.clear();
      this.usedBlurRadiiOrdered.addAll(requested.stream().sorted().limit(5L).toList());
      if (this.usedBlurRadiiOrdered.isEmpty()) {
         this.usedBlurRadiiOrdered.add(0);
      }

      this.blurRadiusToIndex.clear();

      for(int i = 0; i < this.usedBlurRadiiOrdered.size(); ++i) {
         this.blurRadiusToIndex.put((Integer)this.usedBlurRadiiOrdered.get(i), i);
      }

       this.widgetData.clear();
       Std140Builder builder = Std140Builder.intoBuffer(this.widgetData);
       builder.putFloat((float)this.widgets.size());
       builder.align(16);

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               AetherialGlassElementRenderState widget = (AetherialGlassElementRenderState)this.widgets.get(i);
               float width = (float)(widget.x2() - widget.x1());
               float height = (float)(widget.y2() - widget.y1());
               float px = (float)(widget.x1() + dx) * scale;
               float pyTop = (float)(widget.y1() + dy) * scale;
               float scaledWidth = width * scale;
               float scaledHeight = height * scale;
               float centerX = px + 0.5F * scaledWidth;
               float centerY = pyTop + 0.5F * scaledHeight;
               float centerFramebufferY = (float)framebufferHeight - centerY;
               builder.putVec4(centerX - 0.5F * scaledWidth, centerFramebufferY - 0.5F * scaledHeight, scaledWidth, scaledHeight);
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               float radius = ((AetherialGlassElementRenderState)this.widgets.get(i)).cornerRadius() * scale;
               builder.putVec4(radius, radius, radius, radius);
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               AetherialGlassStyle style = ((AetherialGlassElementRenderState)this.widgets.get(i)).style();
               int color = style.getTintColor();
               builder.putVec4((float)ColorHelper.getRed(color) / 255.0F, (float)ColorHelper.getGreen(color) / 255.0F, (float)ColorHelper.getBlue(color) / 255.0F, style.getTintAlpha());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               AetherialGlassStyle style = ((AetherialGlassElementRenderState)this.widgets.get(i)).style();
               builder.putVec4(style.getRefThickness(), style.getRefFactor(), style.getRefDispersion(), style.getRefFresnelRange());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               AetherialGlassStyle style = ((AetherialGlassElementRenderState)this.widgets.get(i)).style();
               builder.putVec4(style.getRefFresnelHardness(), style.getRefFresnelFactor(), style.getGlareRange(), style.getGlareHardness());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               AetherialGlassStyle style = ((AetherialGlassElementRenderState)this.widgets.get(i)).style();
               builder.putVec4(style.getGlareConvergence(), style.getGlareOppositeFactor(), style.getGlareFactor(), style.getGlareAngleRad());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               builder.putVec4(((AetherialGlassElementRenderState)this.widgets.get(i)).style().getSmoothing(), 0.0F, 0.0F, 0.0F);
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               ScreenRect scissor = ((AetherialGlassElementRenderState)this.widgets.get(i)).scissorArea();
               if (scissor != null) {
                  float left = (float)(scissor.getLeft() + dx) * scale;
                  float right = (float)(scissor.getRight() + dx) * scale;
                  float top = (float)(scissor.getTop() + dy) * scale;
                  float bottom = (float)(scissor.getBottom() + dy) * scale;
                  builder.putVec4(left, (float)framebufferHeight - bottom, right, (float)framebufferHeight - top);
               } else {
                  builder.putVec4(0.0F, 0.0F, (float)framebufferWidth, (float)framebufferHeight);
               }
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               AetherialGlassStyle style = ((AetherialGlassElementRenderState)this.widgets.get(i)).style();
               builder.putVec4(style.getShadowExpand(), style.getShadowFactor(), style.getShadowOffsetX() * scale, style.getShadowOffsetY() * scale);
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               AetherialGlassStyle style = ((AetherialGlassElementRenderState)this.widgets.get(i)).style();
               int color = style.getShadowColor();
               builder.putVec4((float)ColorHelper.getRed(color) / 255.0F, (float)ColorHelper.getGreen(color) / 255.0F, (float)ColorHelper.getBlue(color) / 255.0F, style.getShadowColorAlpha());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

          for(int i = 0; i < 64; ++i) {
             if (i < this.widgets.size()) {
                int radius = Math.max(0, ((AetherialGlassElementRenderState)this.widgets.get(i)).style().getBlurRadius());
                int index = (Integer)this.blurRadiusToIndex.getOrDefault(radius, 0);
                builder.putVec4((float)index, ((AetherialGlassElementRenderState)this.widgets.get(i)).hover(), ((AetherialGlassElementRenderState)this.widgets.get(i)).focus(), ((AetherialGlassElementRenderState)this.widgets.get(i)).style().getOpacity());
             } else {
                builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
             }
          }
       this.widgetSize = this.widgetData.position();
       this.copyToGl(this.widgetInfo, this.widgetData, this.widgetSize);
    }

   public int getCount() {
      return this.hudWidgets.size() + this.guiWidgets.size();
   }

   public GpuBuffer getSamplerInfoBuffer() {
      return this.samplerInfo;
   }

   public GpuBuffer getCustomUniformsBuffer() {
      return this.customUniforms;
   }

   public GpuBuffer getWidgetInfoBuffer() {
      return this.widgetInfo;
   }

   public GpuBuffer getBgConfigBuffer() {
      return this.bgConfig;
   }

   public List<Integer> getUsedBlurRadiiOrdered() {
      return this.usedBlurRadiiOrdered;
   }
}
