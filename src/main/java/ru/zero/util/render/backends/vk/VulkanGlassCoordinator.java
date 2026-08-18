package ru.zero.util.render.backends.vk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import ru.zero.util.render.backends.gl.GlBackend;

@Environment(EnvType.CLIENT)
public final class VulkanGlassCoordinator {
   private final VulkanContext ctx;
   private final VulkanShaderSystem shaders;
   private final VulkanTextureManager textures;
   private final GlBackend gl;
   private VulkanGlassComposite composite;
   private VulkanGLInterop outputInterop;
   private VulkanGLSemaphoreSync outputSync;
   private boolean available;
   private int lastFbW;
   private int lastFbH;

   public VulkanGlassCoordinator(VulkanContext ctx, VulkanShaderSystem shaders, VulkanTextureManager textures,
         GlBackend gl) {
      this.ctx = ctx;
      this.shaders = shaders;
      this.textures = textures;
      this.gl = gl;
   }

   public boolean isAvailable() {
      return this.available;
   }

   public boolean init() {
      if (this.available) {
         return true;
      }
      try {
         this.outputInterop = new VulkanGLInterop(this.ctx);
         this.outputSync = new VulkanGLSemaphoreSync(this.ctx);
         this.composite = new VulkanGlassComposite(this.ctx, this.shaders, this.textures);
         if (!this.composite.init()) {
            throw new IllegalStateException("VulkanGlassComposite init failed");
         }
         if (!this.outputSync.init()) {
            // semaphore optional — fence fallback still works, keep going
            System.err.println("[Zero/Vulkan] Glass output semaphore unavailable, using fence-only sync");
         }
         this.available = true;
         System.out.println("[Zero/Vulkan] VulkanGlassCoordinator ready");
      } catch (Throwable t) {
         this.available = false;
         this.destroyPartial();
         System.err.println("[Zero/Vulkan] VulkanGlassCoordinator init failed: " + t.getMessage());
         t.printStackTrace();
      }
      return this.available;
   }

   // Full Vulkan glass composite. Caller provides the std140 UBO payloads (built by
   // AetherialGlassUniforms) and the Minecraft framebuffer pixels (read via glReadPixels
   // by the caller). Returns a GL texture id holding the composited result, ready to
   // blit into the MC framebuffer; returns 0 on failure (caller falls back to GL glass).
   public int composite(int fbW, int fbH, ByteBuffer mcFbPixels, ByteBuffer samplerInfo, ByteBuffer custom,
         ByteBuffer widget, ByteBuffer bg, List<Integer> radii, Map<Integer, int[]> bboxByRadius) {
      if (!this.available || mcFbPixels == null) {
         return 0;
      }
      try {
         if (!this.outputInterop.ensure(fbW, fbH)) {
            return 0;
         }
         this.composite.ensureBackdrop(fbW, fbH);
         this.composite.uploadBackdrop(mcFbPixels, fbW, fbH);
         this.composite.uploadUniforms(samplerInfo, custom, widget, bg);
         Map<Integer, long[]> blurredViews = new java.util.HashMap<>();
         this.composite.composite(fbW, fbH, this.composite.backdropView(), 0L,
               this.outputInterop.image(), this.outputInterop.imageView(), this.outputInterop.framebuffer(),
               radii, bboxByRadius, blurredViews);
         // The composite's one-time submits fence-wait, so VK work is complete here.
         // glWaitSemaphoreEXT (if available) guarantees cross-API visibility of the
         // shared memory writes before GL samples the output texture.
         if (this.outputSync.isAvailable()) {
            this.outputSync.glWait();
         }
         this.lastFbW = fbW;
         this.lastFbH = fbH;
         return this.outputInterop.glTexture();
      } catch (Throwable t) {
         System.err.println("[Zero/Vulkan] glass coordinator composite failed: " + t.getMessage());
         t.printStackTrace();
         this.available = false;
         return 0;
      }
   }

   public int lastWidth() {
      return this.lastFbW;
   }

   public int lastHeight() {
      return this.lastFbH;
   }

   // Capture the currently-bound READ framebuffer (the MC main framebuffer) into a
   // direct RGBA ByteBuffer suitable for uploadBackdrop. Caller must have the MC FB
   // bound as READ framebuffer.
   public static ByteBuffer captureFramebuffer(int w, int h) {
      ByteBuffer pixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
      GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
      return pixels;
   }

   public void destroy() {
      this.destroyPartial();
   }

   private void destroyPartial() {
      if (this.composite != null) {
         try {
            this.composite.destroy();
         } catch (Throwable ignored) {
         }
         this.composite = null;
      }
      if (this.outputInterop != null) {
         try {
            this.outputInterop.destroy();
         } catch (Throwable ignored) {
         }
         this.outputInterop = null;
      }
      if (this.outputSync != null) {
         try {
            this.outputSync.destroy();
         } catch (Throwable ignored) {
         }
         this.outputSync = null;
      }
      this.available = false;
   }
}
