package ru.zero.util.render.backends.vk;

import java.nio.LongBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRExternalMemory;
import org.lwjgl.vulkan.KHRExternalMemoryWin32;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkComponentMapping;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent3D;
import org.lwjgl.vulkan.VkExternalMemoryImageCreateInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryDedicatedAllocateInfo;
import org.lwjgl.vulkan.VkMemoryGetWin32HandleInfoKHR;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkExportMemoryAllocateInfo;
import ru.zero.util.other.PlatformUtil;

/**
 * Zero-copy VK↔GL interop: одна VkImage выделяется с экспортируемой Win32-памятью,
 * та же память импортируется в OpenGL как текстура (GL_EXT_memory_object_win32).
 * Vulkan рендерит прямо в эту shared image, а GL сэмплит ту же текстуру — без
 * CPU-readback. Если любое из требований (VK external_memory_win32, GL_EXT_memory_object,
 * Windows) не выполнено — {@link #ensure} возвращает false и backend остаётся на
 * readback-композите (регрессии нет).
 */
@Environment(EnvType.CLIENT)
public final class VulkanGLInterop {
   public static final int VK_HANDLE_TYPE_OPAQUE_WIN32 = VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;

   private final VulkanContext ctx;
   private boolean available;
   private boolean glCapsChecked;
   private boolean glInteropSupported;
   private long image;
   private long imageMemory;
   private long imageView;
   private long renderPass;
   private long framebuffer;
   private int glTexture;
   private int glMemoryObject;
   private long win32Handle;
   private int width;
   private int height;

   public VulkanGLInterop(VulkanContext ctx) {
      this.ctx = ctx;
   }

   public boolean isAvailable() {
      return this.available;
   }

   public int glTexture() {
      return this.glTexture;
   }

   public long image() {
      return this.image;
   }

   public long imageView() {
      return this.imageView;
   }

   public long renderPass() {
      return this.renderPass;
   }

   public long framebuffer() {
      return this.framebuffer;
   }

   public int width() {
      return this.width;
   }

   public int height() {
      return this.height;
   }

   private boolean checkGLCaps() {
      if (this.glCapsChecked) {
         return this.glInteropSupported;
      }
      this.glCapsChecked = true;
      try {
         org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
         this.glInteropSupported = caps.GL_EXT_memory_object
               && caps.GL_EXT_memory_object_win32
               && PlatformUtil.isWindows()
               && this.ctx.supportsExternalMemoryWin32();
      } catch (Throwable t) {
         this.glInteropSupported = false;
         System.err.println("[Zero/Vulkan] GL interop capability check failed: " + t.getMessage());
      }
      return this.glInteropSupported;
   }

   public boolean ensure(int width, int height) {
      if (width <= 0 || height <= 0) {
         return false;
      }
      if (this.available && this.width == width && this.height == height) {
         return true;
      }
      this.destroy();
      if (!this.checkGLCaps()) {
         return false;
      }
      try {
         this.width = width;
         this.height = height;
         VkDevice device = this.ctx.device();
         try (MemoryStack stack = MemoryStack.stackPush()) {
            this.createImage(device, stack);
            this.allocateAndBind(device, stack);
            this.createImageView(device, stack);
            this.createRenderPass(device, stack);
            this.createFramebuffer(device, stack);
         }
         this.available = true;
         System.out.println("[Zero/Vulkan] GL interop shared target ready: " + width + "x" + height
               + " (glTexture=" + this.glTexture + ")");
      } catch (Throwable t) {
         this.available = false;
         System.err.println("[Zero/Vulkan] GL interop init failed, will use readback: " + t.getMessage());
         t.printStackTrace();
         this.destroyPartial();
      }
      return this.available;
   }

   private void createImage(VkDevice device, MemoryStack stack) {
      VkExtent3D extent = VkExtent3D.calloc(stack);
      extent.width(this.width);
      extent.height(this.height);
      extent.depth(1);

      VkExternalMemoryImageCreateInfo extMem = VkExternalMemoryImageCreateInfo.calloc(stack);
      extMem.sType(KHRExternalMemory.VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO_KHR);
      extMem.handleTypes(VK_HANDLE_TYPE_OPAQUE_WIN32);

      VkImageCreateInfo ci = VkImageCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
      ci.pNext(extMem);
      ci.imageType(VK10.VK_IMAGE_TYPE_2D);
      ci.format(VulkanOffscreen.COLOR_FORMAT);
      ci.extent(extent);
      ci.mipLevels(1);
      ci.arrayLayers(1);
      ci.samples(VK10.VK_SAMPLE_COUNT_1_BIT);
      ci.tiling(VK10.VK_IMAGE_TILING_OPTIMAL);
      ci.usage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT
            | VK10.VK_IMAGE_USAGE_SAMPLED_BIT);
      ci.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
      ci.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);

      LongBuffer pImage = stack.mallocLong(1);
      int err = VK10.vkCreateImage(device, ci, null, pImage);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("interop vkCreateImage failed: " + VulkanContext.vulkanError(err));
      }
      this.image = pImage.get(0);
   }

   private void allocateAndBind(VkDevice device, MemoryStack stack) {
      VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
      VK10.vkGetImageMemoryRequirements(device, this.image, req);
      int typeBits = (int) req.memoryTypeBits();
      int type = this.ctx.findMemoryType(typeBits, VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
      if (type < 0) {
         throw new IllegalStateException("interop: no device-local memory type (typeBits=" + typeBits + ")");
      }

      VkExportMemoryAllocateInfo exportInfo = VkExportMemoryAllocateInfo.calloc(stack);
      exportInfo.sType(KHRExternalMemory.VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO_KHR);
      exportInfo.handleTypes(VK_HANDLE_TYPE_OPAQUE_WIN32);

      VkMemoryDedicatedAllocateInfo dedicated = VkMemoryDedicatedAllocateInfo.calloc(stack);
      dedicated.sType(VK11.VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO);
      dedicated.image(this.image);
      dedicated.pNext(exportInfo.address());

      VkMemoryAllocateInfo ai = VkMemoryAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
      ai.pNext(dedicated);
      ai.allocationSize(req.size());
      ai.memoryTypeIndex(type);

      LongBuffer pMem = stack.mallocLong(1);
      int err = VK10.vkAllocateMemory(device, ai, null, pMem);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("interop vkAllocateMemory failed: " + VulkanContext.vulkanError(err));
      }
      this.imageMemory = pMem.get(0);

      err = VK10.vkBindImageMemory(device, this.image, this.imageMemory, 0);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("interop vkBindImageMemory failed: " + VulkanContext.vulkanError(err));
      }

      this.exportHandleAndImportGL(device, stack, req.size());
   }

   private void exportHandleAndImportGL(VkDevice device, MemoryStack stack, long size) {
      VkMemoryGetWin32HandleInfoKHR hi = VkMemoryGetWin32HandleInfoKHR.calloc(stack);
      hi.sType(KHRExternalMemoryWin32.VK_STRUCTURE_TYPE_MEMORY_GET_WIN32_HANDLE_INFO_KHR);
      hi.memory(this.imageMemory);
      hi.handleType(VK_HANDLE_TYPE_OPAQUE_WIN32);

      PointerBuffer pHandle = stack.mallocPointer(1);
      int err = KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR(device, hi, pHandle);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkGetMemoryWin32HandleKHR failed: " + VulkanContext.vulkanError(err));
      }
      this.win32Handle = pHandle.get(0);

      int[] memObjs = new int[1];
      org.lwjgl.opengl.EXTMemoryObject.glCreateMemoryObjectsEXT(memObjs);
      this.glMemoryObject = memObjs[0];
      if (this.glMemoryObject == 0) {
         throw new IllegalStateException("glCreateMemoryObjectsEXT returned 0");
      }
      org.lwjgl.opengl.EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(
            this.glMemoryObject, size, org.lwjgl.opengl.EXTMemoryObjectWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT,
            this.win32Handle);

      this.glTexture = org.lwjgl.opengl.GL11.glGenTextures();
      org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, this.glTexture);
      org.lwjgl.opengl.EXTMemoryObject.glTexStorageMem2DEXT(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 1, org.lwjgl.opengl.GL11.GL_RGBA8, this.width, this.height,
            this.glMemoryObject, 0L);
      org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
      org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
      org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
      org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
      org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0);
   }

   private void createImageView(VkDevice device, MemoryStack stack) {
      VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack);
      range.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
      range.baseMipLevel(0);
      range.levelCount(1);
      range.baseArrayLayer(0);
      range.layerCount(1);

      VkComponentMapping components = VkComponentMapping.calloc(stack);
      components.r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
      components.g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
      components.b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
      components.a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);

      VkImageViewCreateInfo ci = VkImageViewCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
      ci.image(this.image);
      ci.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);
      ci.format(VulkanOffscreen.COLOR_FORMAT);
      ci.components(components);
      ci.subresourceRange(range);

      LongBuffer pView = stack.mallocLong(1);
      int err = VK10.vkCreateImageView(device, ci, null, pView);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("interop vkCreateImageView failed: " + VulkanContext.vulkanError(err));
      }
      this.imageView = pView.get(0);
   }

   // Render pass совместим с pipeline offscreen (тот же COLOR_FORMAT, 1 sample). finalLayout
   // = SHADER_READ_ONLY, чтобы GL мог сэмплить сразу после отрисовки. Каждый кадр
   // initialLayout = UNDEFINED (clear), так что предыдущий layout не важен.
   private void createRenderPass(VkDevice device, MemoryStack stack) {
      VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1, stack);
      attachments.get(0)
            .format(VulkanOffscreen.COLOR_FORMAT)
            .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

      VkAttachmentReference.Buffer colorRefs = VkAttachmentReference.calloc(1, stack);
      colorRefs.get(0).attachment(0).layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

      VkSubpassDescription.Buffer subpasses = VkSubpassDescription.calloc(1, stack);
      subpasses.get(0)
            .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
            .colorAttachmentCount(1)
            .pColorAttachments(colorRefs);

      VkSubpassDependency.Buffer deps = VkSubpassDependency.calloc(1, stack);
      deps.get(0)
            .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
            .dstSubpass(0)
            .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            .srcAccessMask(0)
            .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

      VkRenderPassCreateInfo ci = VkRenderPassCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
      ci.pAttachments(attachments);
      ci.pSubpasses(subpasses);
      ci.pDependencies(deps);

      LongBuffer pPass = stack.mallocLong(1);
      int err = VK10.vkCreateRenderPass(device, ci, null, pPass);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("interop vkCreateRenderPass failed: " + VulkanContext.vulkanError(err));
      }
      this.renderPass = pPass.get(0);
   }

   private void createFramebuffer(VkDevice device, MemoryStack stack) {
      LongBuffer pAttachments = stack.mallocLong(1);
      pAttachments.put(0, this.imageView);

      VkFramebufferCreateInfo ci = VkFramebufferCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
      ci.renderPass(this.renderPass);
      ci.attachmentCount(1);
      ci.pAttachments(pAttachments);
      ci.width(this.width);
      ci.height(this.height);
      ci.layers(1);

      LongBuffer pFb = stack.mallocLong(1);
      int err = VK10.vkCreateFramebuffer(device, ci, null, pFb);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("interop vkCreateFramebuffer failed: " + VulkanContext.vulkanError(err));
      }
      this.framebuffer = pFb.get(0);
   }

   public void destroy() {
      this.destroyPartial();
   }

   private void destroyPartial() {
      VkDevice device = this.ctx != null ? this.ctx.device() : null;
      if (device != null) {
         if (this.framebuffer != 0) {
            VK10.vkDestroyFramebuffer(device, this.framebuffer, null);
         }
         if (this.renderPass != 0) {
            VK10.vkDestroyRenderPass(device, this.renderPass, null);
         }
         if (this.imageView != 0) {
            VK10.vkDestroyImageView(device, this.imageView, null);
         }
         if (this.image != 0) {
            VK10.vkDestroyImage(device, this.image, null);
         }
         if (this.imageMemory != 0) {
            VK10.vkFreeMemory(device, this.imageMemory, null);
         }
      }
      if (this.glTexture != 0) {
         try {
            org.lwjgl.opengl.GL11.glDeleteTextures(this.glTexture);
         } catch (Throwable ignored) {
         }
      }
      if (this.glMemoryObject != 0) {
         try {
            org.lwjgl.opengl.EXTMemoryObject.glDeleteMemoryObjectsEXT(new int[] { this.glMemoryObject });
         } catch (Throwable ignored) {
         }
      }
      this.framebuffer = 0;
      this.renderPass = 0;
      this.imageView = 0;
      this.image = 0;
      this.imageMemory = 0;
      this.glTexture = 0;
      this.glMemoryObject = 0;
      this.win32Handle = 0;
      this.width = 0;
      this.height = 0;
      this.available = false;
   }
}
