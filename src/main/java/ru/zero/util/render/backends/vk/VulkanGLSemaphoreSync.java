package ru.zero.util.render.backends.vk;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRExternalSemaphore;
import org.lwjgl.vulkan.KHRExternalSemaphoreWin32;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExportSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreGetWin32HandleInfoKHR;
import org.lwjgl.vulkan.VkSubmitInfo;

@Environment(EnvType.CLIENT)
public final class VulkanGLSemaphoreSync {
   public static final int VK_HANDLE_TYPE_OPAQUE_WIN32 = VK11.VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT;

   private final VulkanContext ctx;
   private long vkSemaphore;
   private long win32Handle;
   private int glSemaphore;
   private boolean available;
   private boolean glCapsChecked;
   private boolean glSemSupported;

   public VulkanGLSemaphoreSync(VulkanContext ctx) {
      this.ctx = ctx;
   }

   public boolean isAvailable() {
      return this.available;
   }

   public long vkSemaphore() {
      return this.vkSemaphore;
   }

   private boolean checkGLCaps() {
      if (this.glCapsChecked) {
         return this.glSemSupported;
      }
      this.glCapsChecked = true;
      try {
         org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
         this.glSemSupported = caps.GL_EXT_semaphore
               && caps.GL_EXT_semaphore_win32
               && this.ctx.supportsExternalSemaphoreWin32();
      } catch (Throwable t) {
         this.glSemSupported = false;
      }
      return this.glSemSupported;
   }

   public boolean init() {
      if (this.available) {
         return true;
      }
      if (!this.checkGLCaps()) {
         return false;
      }
      VkDevice device = this.ctx.device();
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkExportSemaphoreCreateInfo exportInfo = VkExportSemaphoreCreateInfo.calloc(stack);
         exportInfo.sType(KHRExternalSemaphore.VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO_KHR);
         exportInfo.handleTypes(VK_HANDLE_TYPE_OPAQUE_WIN32);

         VkSemaphoreCreateInfo ci = VkSemaphoreCreateInfo.calloc(stack);
         ci.sType(VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
         ci.pNext(exportInfo.address());

         LongBuffer pSem = stack.mallocLong(1);
         int err = VK10.vkCreateSemaphore(device, ci, null, pSem);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("interop vkCreateSemaphore failed: " + VulkanContext.vulkanError(err));
         }
         this.vkSemaphore = pSem.get(0);

         VkSemaphoreGetWin32HandleInfoKHR hi = VkSemaphoreGetWin32HandleInfoKHR.calloc(stack);
         hi.sType(KHRExternalSemaphoreWin32.VK_STRUCTURE_TYPE_SEMAPHORE_GET_WIN32_HANDLE_INFO_KHR);
         hi.semaphore(this.vkSemaphore);
         hi.handleType(VK_HANDLE_TYPE_OPAQUE_WIN32);

         PointerBuffer pHandle = stack.mallocPointer(1);
         err = KHRExternalSemaphoreWin32.vkGetSemaphoreWin32HandleKHR(device, hi, pHandle);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkGetSemaphoreWin32HandleKHR failed: " + VulkanContext.vulkanError(err));
         }
         this.win32Handle = pHandle.get(0);

         this.glSemaphore = org.lwjgl.opengl.EXTSemaphore.glGenSemaphoresEXT();
         if (this.glSemaphore == 0) {
            throw new IllegalStateException("glGenSemaphoresEXT returned 0");
         }
         org.lwjgl.opengl.EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT(
               this.glSemaphore, org.lwjgl.opengl.EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT,
               this.win32Handle);
         this.available = true;
      } catch (Throwable t) {
         this.available = false;
         this.destroyPartial(device);
         System.err.println("[Zero/Vulkan] Semaphore sync init failed, using fence-only: " + t.getMessage());
      }
      return this.available;
   }

   // Attach the Vulkan semaphore as a signal to the submit info (Vulkan signals after render).
   public void applySignal(VkSubmitInfo si, MemoryStack stack) {
      if (!this.available) {
         return;
      }
      LongBuffer pSignal = stack.mallocLong(1);
      pSignal.put(0, this.vkSemaphore);
      si.pSignalSemaphores(pSignal);
   }

   // GL waits on the semaphore before sampling/blitting the shared texture.
   public void glWait() {
      if (!this.available) {
         return;
      }
      try {
         IntBuffer empty = IntBuffer.allocate(0);
         org.lwjgl.opengl.EXTSemaphore.glWaitSemaphoreEXT(this.glSemaphore, empty, empty.slice(), empty.slice());
      } catch (Throwable t) {
         System.err.println("[Zero/Vulkan] glWaitSemaphoreEXT failed: " + t.getMessage());
      }
   }

   public void destroy() {
      this.destroyPartial(this.ctx.device());
   }

   private void destroyPartial(VkDevice device) {
      if (this.glSemaphore != 0) {
         try {
            org.lwjgl.opengl.EXTSemaphore.glDeleteSemaphoresEXT(this.glSemaphore);
         } catch (Throwable ignored) {
         }
         this.glSemaphore = 0;
      }
      if (device != null && this.vkSemaphore != 0) {
         try {
            VK10.vkDestroySemaphore(device, this.vkSemaphore, null);
         } catch (Throwable ignored) {
         }
      }
      this.vkSemaphore = 0;
      this.win32Handle = 0;
      this.available = false;
   }
}
