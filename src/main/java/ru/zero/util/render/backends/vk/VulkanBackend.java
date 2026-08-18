package ru.zero.util.render.backends.vk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import ru.zero.util.render.backends.RenderBackend;
import ru.zero.util.render.backends.ShapeInstanceBatch;
import ru.zero.util.render.backends.gl.GlBackend;
import ru.zero.util.render.backends.gl.ResourceUtils;

@Environment(EnvType.CLIENT)
public final class VulkanBackend implements RenderBackend {
   private static final int MAX_INSTANCES = 4096;
   private static final int MAX_FRAME_INSTANCES = 65536;
   private static final int INSTANCE_STRIDE = 144;
   private static final long INSTANCE_BUFFER_BYTES = (long) MAX_FRAME_INSTANCES * INSTANCE_STRIDE;

   private final GlBackend gl;
   private VulkanContext context;
   private VulkanOffscreen offscreen;
   private VulkanFrameSync frameSync;
   private VulkanShaderSystem shaderSystem;
   private VulkanTextureManager textures;
   private VulkanDescriptors descriptors;
   private VulkanPipeline pipeline;
   private VulkanInstanceBuffer instanceBuffer;
   private VulkanWorldRenderer worldRenderer;
   private VulkanGLInterop interop;
   private final ShapeInstanceBatch vkBatch = new ShapeInstanceBatch();
   private ByteBuffer vertSpv;
   private ByteBuffer fragSpv;
   private boolean vulkanReady;
   private boolean frameActive;
   private boolean interopActive;
   private int frameWidth;
   private int frameHeight;
   private int compositeGlTex;
   private int compositeGlW;
   private int compositeGlH;
   private int frameSerial;
   private int frameInstanceOffset;
   private boolean destroyed;

   public VulkanBackend() {
      this.gl = new GlBackend();
      this.vkBatch.setFlushAction(this::vkFlush);
      this.initVulkan();
   }

   private void initVulkan() {
      try {
         this.context = new VulkanContext();
         this.context.create();
         this.frameSync = new VulkanFrameSync(this.context);
         this.frameSync.create();
         this.offscreen = new VulkanOffscreen(this.context);
         this.shaderSystem = new VulkanShaderSystem();
         this.shaderSystem.create();
         String vertSrc = ResourceUtils.readText("assets/zero/shaders/shape_vk.vert");
         String fragSrc = ResourceUtils.readText("assets/zero/shaders/shape_vk.frag");
         this.vertSpv = this.shaderSystem.compileVertex(vertSrc, "shape_vk.vert");
         this.fragSpv = this.shaderSystem.compileFragment(fragSrc, "shape_vk.frag");
         System.out.println("[Zero/Vulkan] Shaders compiled: vert=" + this.vertSpv.remaining() + "B, frag="
               + this.fragSpv.remaining() + "B");
         this.textures = new VulkanTextureManager(this.context);
         this.textures.create();
         this.descriptors = new VulkanDescriptors(this.context);
         this.descriptors.create();
         this.instanceBuffer = new VulkanInstanceBuffer(this.context);
         this.instanceBuffer.create(INSTANCE_BUFFER_BYTES);
         this.descriptors.updateSsboBinding(this.instanceBuffer.buffer(), INSTANCE_BUFFER_BYTES);
         this.pipeline = new VulkanPipeline(this.context);
         this.offscreen.ensure(8, 8);
         this.pipeline.create(this.descriptors.pipelineLayout(), this.offscreen.renderPass(), this.vertSpv,
               this.fragSpv);
         this.worldRenderer = new VulkanWorldRenderer(this.context);
         this.worldRenderer.create(this.textures);
         this.interop = new VulkanGLInterop(this.context);
         this.vulkanReady = true;
         System.out.println("[Zero/Vulkan] VulkanBackend Stage 2 ready — full draw path (pipeline + SSBO + textures "
               + "+ descriptors) + world render path. Draws composited via readback into Minecraft framebuffer.");
      } catch (Throwable t) {
         this.vulkanReady = false;
         System.err.println("[Zero/Vulkan] Vulkan Stage 2 init failed, falling back to GL-only: " + t.getMessage());
         t.printStackTrace();
         this.disposeVulkan();
      }
   }

   public boolean isVulkanReady() {
      return this.vulkanReady;
   }

   public VulkanContext context() {
      return this.context;
   }

   public VulkanOffscreen offscreen() {
      return this.offscreen;
   }

   public VulkanPipeline pipeline() {
      return this.pipeline;
   }

   public VulkanWorldRenderer worldRenderer() {
      return this.worldRenderer;
   }

   public VulkanTextureManager textures() {
      return this.textures;
   }

   public void beginWorldFrame(int width, int height, float[] projMatrix16) {
      if (this.vulkanReady && this.worldRenderer != null) {
         try {
            this.worldRenderer.beginFrame(width, height, projMatrix16);
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] beginWorldFrame failed: " + t.getMessage());
            t.printStackTrace();
            this.vulkanReady = false;
         }
      }
   }

   public void flushWorld() {
      if (!this.vulkanReady || this.worldRenderer == null) {
         return;
      }
      try {
         this.worldRenderer.flush();
         this.worldRenderer.compositeToFramebuffer();
         int tex = this.worldRenderer.compositeGlTexture();
         int w = this.worldRenderer.compositeWidth();
         int h = this.worldRenderer.compositeHeight();
         if (tex != 0 && w > 0 && h > 0) {
            this.gl.drawFullscreenTextureToCurrentFb(tex, w, h);
         }
      } catch (Throwable t) {
         System.err.println("[Zero/Vulkan] flushWorld failed: " + t.getMessage());
         t.printStackTrace();
         this.vulkanReady = false;
      }
   }

   @Override
   public void beginFrame(int width, int height) {
      this.frameSerial++;
      this.frameInstanceOffset = 0;
      this.frameWidth = width;
      this.frameHeight = height;
      this.gl.beginFrame(width, height);
      if (this.vulkanReady) {
         try {
            this.offscreen.ensure(width, height);
            this.interopActive = this.interop != null && this.interop.ensure(width, height);
            this.descriptors.beginFrame();
            this.vkBatch.beginFrame(width, height);
            this.beginVulkanRenderPass();
            this.frameActive = true;
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] beginFrame failed: " + t.getMessage());
            t.printStackTrace();
            this.vulkanReady = false;
         }
      }
   }

   private void beginVulkanRenderPass() {
      try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
         int err = org.lwjgl.vulkan.VK10.vkResetCommandBuffer(this.frameSync.commandBuffer(), 0);
         if (err != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkResetCommandBuffer failed: " + VulkanContext.vulkanError(err));
         }
         org.lwjgl.vulkan.VkCommandBufferBeginInfo bi =
               org.lwjgl.vulkan.VkCommandBufferBeginInfo.calloc(stack);
         bi.sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
         bi.flags(org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
         err = org.lwjgl.vulkan.VK10.vkBeginCommandBuffer(this.frameSync.commandBuffer(), bi);
         if (err != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkBeginCommandBuffer failed: " + VulkanContext.vulkanError(err));
         }

         org.lwjgl.vulkan.VkClearValue.Buffer clearValues = org.lwjgl.vulkan.VkClearValue.calloc(1, stack);
         clearValues.get(0).color().float32(0, 0.0F).float32(1, 0.0F).float32(2, 0.0F).float32(3, 0.0F);

         org.lwjgl.vulkan.VkRenderPassBeginInfo rpbi = org.lwjgl.vulkan.VkRenderPassBeginInfo.calloc(stack);
         rpbi.sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
         // Zero-copy путь: рендерим прямо в shared VK image (interop), которую GL сэмплит
         // без readback. Render pass interop совместим с pipeline (тот же формат/samples).
         long targetRenderPass = this.interopActive ? this.interop.renderPass() : this.offscreen.renderPass();
         long targetFramebuffer = this.interopActive ? this.interop.framebuffer() : this.offscreen.framebuffer();
         rpbi.renderPass(targetRenderPass);
         rpbi.framebuffer(targetFramebuffer);
         rpbi.renderArea().offset().set(0, 0);
         rpbi.renderArea().extent().set(this.frameWidth, this.frameHeight);
         rpbi.pClearValues(clearValues);

         org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass(this.frameSync.commandBuffer(), rpbi,
               org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE);

         org.lwjgl.vulkan.VK10.vkCmdBindPipeline(this.frameSync.commandBuffer(),
               org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline.pipeline());

         org.lwjgl.vulkan.VkViewport.Buffer viewport = org.lwjgl.vulkan.VkViewport.calloc(1, stack);
         viewport.get(0).x(0.0F).y(0.0F).width(this.frameWidth).height(this.frameHeight).minDepth(0.0F).maxDepth(1.0F);
         org.lwjgl.vulkan.VK10.vkCmdSetViewport(this.frameSync.commandBuffer(), 0, viewport);

         org.lwjgl.vulkan.VkRect2D.Buffer scissor = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
         scissor.get(0).offset().set(0, 0);
         scissor.get(0).extent().set(this.frameWidth, this.frameHeight);
         org.lwjgl.vulkan.VK10.vkCmdSetScissor(this.frameSync.commandBuffer(), 0, scissor);

         java.nio.ByteBuffer push = stack.malloc(8).order(ByteOrder.nativeOrder());
         push.putFloat(0, this.frameWidth);
         push.putFloat(4, this.frameHeight);
         org.lwjgl.vulkan.VK10.vkCmdPushConstants(this.frameSync.commandBuffer(),
               this.descriptors.pipelineLayout(), org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT, 0, push);

         java.nio.LongBuffer pSets = stack.mallocLong(1);
         pSets.put(0, this.descriptors.ssboSet());
         org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets(this.frameSync.commandBuffer(),
               org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.descriptors.pipelineLayout(), 0, pSets,
               null);
      }
   }

   private void vkFlush() {
      if (!this.vulkanReady || !this.frameActive || this.vkBatch.getInstanceCount() <= 0) {
         this.vkBatch.afterFlush();
         return;
      }
      try {
         ByteBuffer src = this.vkBatch.prepareFlushBuffer();
         int instanceCount = this.vkBatch.getInstanceCount();
         if (this.frameInstanceOffset + instanceCount > MAX_FRAME_INSTANCES) {
            throw new IllegalStateException("Vulkan frame instance buffer exhausted: "
                  + (this.frameInstanceOffset + instanceCount) + " > " + MAX_FRAME_INSTANCES);
         }
         ByteBuffer dst = this.instanceBuffer.mapped();
         dst.position(this.frameInstanceOffset * INSTANCE_STRIDE);
         dst.put(src);

         long samplerSet = this.descriptors.updateSamplerBindings(this.vkBatch, this.textures);

         try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.LongBuffer pSets = stack.mallocLong(1);
            pSets.put(0, samplerSet);
            org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets(this.frameSync.commandBuffer(),
                  org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.descriptors.pipelineLayout(), 1, pSets,
                  null);
         }

         int vertexCount = instanceCount * 6;
         int firstVertex = this.frameInstanceOffset * 6;
         org.lwjgl.vulkan.VK10.vkCmdDraw(this.frameSync.commandBuffer(), vertexCount, 1, firstVertex, 0);
         this.frameInstanceOffset += instanceCount;
         ru.zero.util.render.core.RenderFrameMetrics.getInstance().recordDrawCall(instanceCount * 2);
      } catch (Throwable t) {
         System.err.println("[Zero/Vulkan] vkFlush failed: " + t.getMessage());
         t.printStackTrace();
      } finally {
         this.vkBatch.afterFlush();
      }
   }

   @Override
   public void flush() {
      this.vkFlush();
   }

   @Override
   public void endFrame() {
      if (this.vulkanReady && this.frameActive) {
         try {
            this.vkFlush();
            org.lwjgl.vulkan.VK10.vkCmdEndRenderPass(this.frameSync.commandBuffer());
            int err = org.lwjgl.vulkan.VK10.vkEndCommandBuffer(this.frameSync.commandBuffer());
            if (err != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
               throw new IllegalStateException("vkEndCommandBuffer failed: " + VulkanContext.vulkanError(err));
            }

            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
               org.lwjgl.vulkan.VkSubmitInfo si = org.lwjgl.vulkan.VkSubmitInfo.calloc(stack);
               si.sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);
               org.lwjgl.PointerBuffer pCb = stack.mallocPointer(1);
               pCb.put(0, this.frameSync.commandBuffer());
               si.pCommandBuffers(pCb);
               err = org.lwjgl.vulkan.VK10.vkQueueSubmit(this.context.graphicsQueue(), si, this.frameSync.frameFence());
               if (err != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                  throw new IllegalStateException("vkQueueSubmit failed: " + VulkanContext.vulkanError(err));
               }
            }
            this.frameSync.waitForFence();
            this.compositeToFramebuffer();
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] endFrame failed: " + t.getMessage());
            t.printStackTrace();
            this.vulkanReady = false;
         } finally {
            this.frameActive = false;
         }
      }
      this.gl.endFrame();
   }

   private void compositeToFramebuffer() {
      int w = this.frameWidth;
      int h = this.frameHeight;
      // Zero-copy: Vulkan уже отрисовал в shared image, и та же память видна GL как
      // interop.glTexture(). Fence уже ждали в endFrame, значит рендер завершён —
      // просто рисуем shared текстуру в framebuffer MC, без GPU→CPU→GPU readback.
      if (this.interopActive && this.interop != null && this.interop.glTexture() != 0) {
         this.gl.drawFullscreenTexture(this.interop.glTexture(), w, h);
         return;
      }

      ByteBuffer pixels = this.offscreen.readbackRGBA();
      if (pixels == null) {
         return;
      }
      if (this.compositeGlTex == 0 || this.compositeGlW != w || this.compositeGlH != h) {
         if (this.compositeGlTex != 0) {
            GL11.glDeleteTextures(this.compositeGlTex);
         }
         this.compositeGlTex = GL11.glGenTextures();
         this.compositeGlW = w;
         this.compositeGlH = h;
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.compositeGlTex);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
         GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
      } else {
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.compositeGlTex);
         GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
      }
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
      this.gl.drawFullscreenTexture(this.compositeGlTex, w, h);
   }

   @Override
   public void setScissorEnabled(boolean enabled) {
      this.vkBatch.setScissorEnabled(enabled);
   }

   @Override
   public void setScissorRect(int x, int y, int w, int h, float roundTopLeft, float roundTopRight, float roundBottomRight,
         float roundBottomLeft) {
      this.vkBatch.setScissorRect(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft);
   }

   @Override
   public void setTransform(float[] m3) {
      this.gl.setTransform(m3);
   }

   @Override
   public void setBlurCaptureScale(float scaleX, float scaleY) {
      this.gl.setBlurCaptureScale(scaleX, scaleY);
   }

   @Override
   public void enqueueRect(float x, float y, float w, float h, float roundTopLeft, float roundTopRight,
         float roundBottomRight, float roundBottomLeft, int color, float[] transform) {
      this.vkBatch.enqueueRect(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft, color,
            transform);
   }

   @Override
   public void enqueueRectOutline(float x, float y, float w, float h, float roundTopLeft, float roundTopRight,
         float roundBottomRight, float roundBottomLeft, int color, float thickness, float[] transform) {
      this.vkBatch.enqueueRectOutline(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft,
            color, thickness, transform);
   }

   @Override
   public void enqueueGradient(float x, float y, float w, float h, float roundTopLeft, float roundTopRight,
         float roundBottomRight, float roundBottomLeft, int colorTL, int colorTR, int colorBR, int colorBL,
         float[] transform) {
      this.vkBatch.enqueueGradient(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft, colorTL,
            colorTR, colorBR, colorBL, transform);
   }

   @Override
   public void enqueueCircle(float cx, float cy, float radius, float startDeg, float pct, int color,
         float[] transform) {
      this.vkBatch.enqueueCircle(cx, cy, radius, startDeg, pct, color, transform);
   }

   @Override
   public void drawDropShadowRect(float x, float y, float w, float h, float roundTopLeft, float roundTopRight,
         float roundBottomRight, float roundBottomLeft, float blurStrength, float spread, int rgbaPremul,
         float[] transform) {
      this.vkBatch.drawDropShadowRect(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft,
            blurStrength, spread, rgbaPremul, transform);
   }

   @Override
   public void drawTexturedQuad(int texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1,
         int rgbaPremul, float[] transform) {
      this.vkBatch.drawTexturedQuad(texture, x, y, w, h, u0, v0, u1, v1, rgbaPremul, transform);
   }

   @Override
   public void drawTexturedQuadRounded(int texture, float x, float y, float w, float h, float u0, float v0, float u1,
         float v1, float rounding, int rgbaPremul, float[] transform) {
      this.vkBatch.drawTexturedQuadRounded(texture, x, y, w, h, u0, v0, u1, v1, rounding, rgbaPremul, transform);
   }

   @Override
   public void drawRgbaTexturedQuad(int texture, float x, float y, float w, float h, float u0, float v0, float u1,
         float v1, int rgbaPremul, float[] transform) {
      this.vkBatch.drawRgbaTexturedQuad(texture, x, y, w, h, u0, v0, u1, v1, rgbaPremul, transform);
   }

   @Override
   public void drawRgbaTexturedQuad(int texture, float x, float y, float w, float h, float u0, float v0, float u1,
         float v1, int rgbaPremul, float[] transform, boolean preservePremultipliedColor) {
      this.vkBatch.drawRgbaTexturedQuad(texture, x, y, w, h, u0, v0, u1, v1, rgbaPremul, transform,
            preservePremultipliedColor);
   }

   @Override
   public void drawRgbaTexturedQuadRounded(int texture, float x, float y, float w, float h, float u0, float v0,
         float u1, float v1, float rounding, int rgbaPremul, float[] transform) {
      this.vkBatch.drawRgbaTexturedQuadRounded(texture, x, y, w, h, u0, v0, u1, v1, rounding, rgbaPremul, transform);
   }

   @Override
   public void drawRgbaTexturedQuadRounded(int texture, float x, float y, float w, float h, float u0, float v0,
         float u1, float v1, float rounding, int rgbaPremul, float[] transform, boolean preservePremultipliedColor) {
      this.vkBatch.drawRgbaTexturedQuadRounded(texture, x, y, w, h, u0, v0, u1, v1, rounding, rgbaPremul, transform,
            preservePremultipliedColor);
   }

   @Override
   public void drawRgbaOpaqueTexturedQuadRounded(int texture, float x, float y, float w, float h, float u0, float v0,
         float u1, float v1, float rounding, int rgbaPremul, float[] transform) {
      this.vkBatch.drawRgbaOpaqueTexturedQuadRounded(texture, x, y, w, h, u0, v0, u1, v1, rounding, rgbaPremul,
            transform);
   }

   @Override
   public void drawRgbaOpaqueTexturedQuadRounded(int texture, float x, float y, float w, float h, float u0, float v0,
         float u1, float v1, float rounding, int rgbaPremul, float[] transform, boolean screenSpaceUv) {
      this.vkBatch.drawRgbaOpaqueTexturedQuadRounded(texture, x, y, w, h, u0, v0, u1, v1, rounding, rgbaPremul,
            transform, screenSpaceUv);
   }

   @Override
   public void drawRgbaOpaqueTexturedQuad(int texture, float x, float y, float w, float h, float u0, float v0,
         float u1, float v1, int rgbaPremul, float[] transform) {
      this.vkBatch.drawRgbaOpaqueTexturedQuad(texture, x, y, w, h, u0, v0, u1, v1, rgbaPremul, transform);
   }

   @Override
   public void enqueueMsdfGlyph(int texture, float pxRange, float x, float y, float width, float height, float u0,
         float v0, float u1, float v1, int rgbaColor, float[] transform) {
      this.vkBatch.enqueueMsdfGlyph(texture, pxRange, x, y, width, height, u0, v0, u1, v1, rgbaColor, transform);
   }

   @Override
   public void drawInstances(ByteBuffer data, int instanceCount) {
      this.gl.drawInstances(data, instanceCount);
   }

   @Override
   public int createMsdfTexture(int width, int height, ByteBuffer data) {
      if (this.vulkanReady) {
         try {
            return this.textures.createTexture(width, height, VulkanTextureManager.FORMAT_RGBA8, data);
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] createMsdfTexture failed, GL fallback: " + t.getMessage());
         }
      }
      return this.gl.createMsdfTexture(width, height, data);
   }

   @Override
   public int createAlphaTexture(int width, int height) {
      if (this.vulkanReady) {
         try {
            return this.textures.createTexture(width, height, VulkanTextureManager.FORMAT_R8, null);
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] createAlphaTexture failed, GL fallback: " + t.getMessage());
         }
      }
      return this.gl.createAlphaTexture(width, height);
   }

   @Override
   public void uploadAlphaSubImage(int tex, int x, int y, int w, int h, ByteBuffer data) {
      if (this.vulkanReady && this.textures.texture(tex) != null) {
         try {
            this.textures.uploadAlphaSubImage(tex, x, y, w, h, data, 0);
            return;
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] uploadAlphaSubImage failed: " + t.getMessage());
         }
      }
      this.gl.uploadAlphaSubImage(tex, x, y, w, h, data);
   }

   @Override
   public void uploadAlphaSubImageWithStride(int tex, int x, int y, int w, int h, ByteBuffer data,
         int sourceRowLength) {
      if (this.vulkanReady && this.textures.texture(tex) != null) {
         try {
            this.textures.uploadAlphaSubImage(tex, x, y, w, h, data, sourceRowLength);
            return;
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] uploadAlphaSubImageWithStride failed: " + t.getMessage());
         }
      }
      this.gl.uploadAlphaSubImageWithStride(tex, x, y, w, h, data, sourceRowLength);
   }

   @Override
   public int captureRegionToTexture(int x, int y, int w, int h) {
      return this.captureRegionToTexture(x, y, w, h, true);
   }

   @Override
   public int captureRegionToTexture(int x, int y, int w, int h, boolean fullscreen) {
      int glTex = this.gl.captureRegionToTexture(x, y, w, h, fullscreen);
      if (!this.vulkanReady || glTex == 0) {
         return glTex;
      }
      return this.importGlTextureToVulkan(glTex, w, h);
   }

   private int importGlTextureToVulkan(int glTex, int w, int h) {
      try {
         ByteBuffer pixels = this.readbackGlTexture(glTex, w, h);
         if (pixels == null) {
            return glTex;
         }
         return this.textures.createTexture(w, h, VulkanTextureManager.FORMAT_RGBA8, pixels);
      } catch (Throwable t) {
         System.err.println("[Zero/Vulkan] importGlTextureToVulkan failed, using GL handle: " + t.getMessage());
         return glTex;
      }
   }

   private ByteBuffer readbackGlTexture(int glTex, int w, int h) {
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTex);
      ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
      GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
      return buffer;
   }

   @Override
   public void prepareScreenBlur(int screenW, int screenH, float radiusPx) {
      this.gl.prepareScreenBlur(screenW, screenH, radiusPx);
   }

   @Override
   public boolean prepareRegionBlur(int x, int y, int width, int height, float radiusPx) {
      return this.gl.prepareRegionBlur(x, y, width, height, radiusPx);
   }

   @Override
   public void drawPreparedBlurRounded(float x, float y, float w, float h, float rounding, float alpha,
         float[] transform) {
      if (this.vulkanReady) {
         int blurTex = this.gl.getPreparedBlurTexture();
         if (blurTex != 0) {
            int vkTex = this.importGlTextureToVulkan(blurTex, this.gl.getPreparedBlurWidth(),
                  this.gl.getPreparedBlurHeight());
            this.drawPreparedBlurRoundedVulkan(vkTex, x, y, w, h, rounding, alpha, transform);
            return;
         }
      }
      this.gl.drawPreparedBlurRounded(x, y, w, h, rounding, alpha, transform);
   }

   private void drawPreparedBlurRoundedVulkan(int vkTex, float x, float y, float w, float h, float rounding,
         float alpha, float[] transform) {
      int colorPremul = (int) (Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) << 24 | 16777215;
      float uScale = this.gl.getPreparedBlurWidth() > 0 ? this.gl.getPreparedBlurScaleX() / this.gl.getPreparedBlurWidth()
            : 0.0F;
      float vScale = this.gl.getPreparedBlurHeight() > 0
            ? -this.gl.getPreparedBlurScaleY() / this.gl.getPreparedBlurHeight()
            : 0.0F;
      float uOffset = 0.0F;
      float vOffset = this.gl.getPreparedBlurHeight() > 0 ? 1.0F : 0.0F;
      this.vkBatch.drawRgbaOpaqueTexturedQuadRounded(vkTex, x, y, w, h, uScale, vScale, uOffset, vOffset, rounding,
            colorPremul, transform, true);
   }

   @Override
   public void drawPreparedRegionBlurRounded(float x, float y, float w, float h, float rounding, float alpha,
         float[] transform, int regionX, int regionY, int regionW, int regionH) {
      if (this.vulkanReady) {
         int blurTex = this.gl.getPreparedRegionBlurTexture();
         if (blurTex != 0 && this.gl.getPreparedRegionBlurWidth() == regionW
               && this.gl.getPreparedRegionBlurHeight() == regionH
               && this.gl.getPreparedRegionBlurX() == regionX
               && this.gl.getPreparedRegionBlurY() == regionY) {
            int vkTex = this.importGlTextureToVulkan(blurTex, regionW, regionH);
            int colorPremul = (int) (Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) << 24 | 16777215;
            this.vkBatch.drawRgbaOpaqueTexturedQuadRounded(vkTex, x, y, w, h, 0.0F, 1.0F, 1.0F, 0.0F, rounding,
                  colorPremul, transform, false);
            return;
         }
      }
      this.gl.drawPreparedRegionBlurRounded(x, y, w, h, rounding, alpha, transform, regionX, regionY, regionW,
            regionH);
   }

   @Override
   public RenderBackend.FrameCapture captureFullFrame() {
      return this.gl.captureFullFrame();
   }

   @Override
   public void drawFullscreenTexture(int texture, int width, int height) {
      this.gl.drawFullscreenTexture(texture, width, height);
   }

   @Override
   public void destroyTexture(int textureId) {
      if (this.vulkanReady && this.textures.texture(textureId) != null) {
         this.textures.destroyTexture(textureId);
         return;
      }
      this.gl.destroyTexture(textureId);
   }

   @Override
   public void destroy() {
      if (this.destroyed) {
         return;
      }
      this.destroyed = true;
      this.gl.destroy();
      this.disposeVulkan();
      if (this.compositeGlTex != 0) {
         GL11.glDeleteTextures(this.compositeGlTex);
         this.compositeGlTex = 0;
      }
      if (this.vertSpv != null) {
         org.lwjgl.system.MemoryUtil.memFree(this.vertSpv);
         this.vertSpv = null;
      }
      if (this.fragSpv != null) {
         org.lwjgl.system.MemoryUtil.memFree(this.fragSpv);
         this.fragSpv = null;
      }
   }

   private void disposeVulkan() {
      if (this.interop != null) {
         try {
            this.interop.destroy();
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] Interop destroy error: " + t.getMessage());
         }
         this.interop = null;
      }
      if (this.pipeline != null) {
         try {
            this.pipeline.destroy();
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] Pipeline destroy error: " + t.getMessage());
         }
         this.pipeline = null;
      }
      if (this.worldRenderer != null) {
         try {
            this.worldRenderer.destroy();
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] WorldRenderer destroy error: " + t.getMessage());
         }
         this.worldRenderer = null;
      }
      if (this.instanceBuffer != null) {
         try {
            this.instanceBuffer.destroy();
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] InstanceBuffer destroy error: " + t.getMessage());
         }
         this.instanceBuffer = null;
      }
      if (this.descriptors != null) {
         try {
            this.descriptors.destroy();
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] Descriptors destroy error: " + t.getMessage());
         }
         this.descriptors = null;
      }
      if (this.textures != null) {
         try {
            this.textures.destroy();
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] TextureManager destroy error: " + t.getMessage());
         }
         this.textures = null;
      }
      if (this.shaderSystem != null) {
         try {
            this.shaderSystem.destroy();
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] ShaderSystem destroy error: " + t.getMessage());
         }
         this.shaderSystem = null;
      }
      if (this.frameSync != null) {
         try {
            this.frameSync.destroy();
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] FrameSync destroy error: " + t.getMessage());
         }
         this.frameSync = null;
      }
      if (this.offscreen != null) {
         try {
            this.offscreen.destroy();
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] Offscreen destroy error: " + t.getMessage());
         }
         this.offscreen = null;
      }
      if (this.context != null) {
         try {
            this.context.destroy();
         } catch (Throwable t) {
            System.err.println("[Zero/Vulkan] Context destroy error: " + t.getMessage());
         }
         this.context = null;
      }
      this.vulkanReady = false;
   }
}
