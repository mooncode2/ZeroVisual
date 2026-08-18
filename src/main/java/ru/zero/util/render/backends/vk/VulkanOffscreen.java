package ru.zero.util.render.backends.vk;

import java.nio.LongBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkComponentMapping;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent3D;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

@Environment(EnvType.CLIENT)
public final class VulkanOffscreen {
   public static final int COLOR_FORMAT = VK10.VK_FORMAT_R8G8B8A8_UNORM;

   private final VulkanContext ctx;
   private long image;
   private long imageMemory;
   private long imageView;
   private long renderPass;
   private long framebuffer;
   private int width;
   private int height;

   private long readbackBuffer;
   private long readbackMemory;
   private long readbackBufferSize;
   private long readbackFence;
   private VkCommandBuffer readbackCb;
   private java.nio.ByteBuffer readbackCopy;

   public VulkanOffscreen(VulkanContext ctx) {
      this.ctx = ctx;
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

   public int format() {
      return COLOR_FORMAT;
   }

   public void ensure(int width, int height) {
      if (width <= 0 || height <= 0) {
         return;
      }
      if (this.image != 0 && this.width == width && this.height == height) {
         return;
      }
      this.destroy();
      this.create(width, height);
   }

   private void create(int width, int height) {
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
      System.out.println("[Zero/Vulkan] Offscreen target created: " + width + "x" + height);
   }

   private void createImage(VkDevice device, MemoryStack stack) {
      VkExtent3D extent = VkExtent3D.calloc(stack);
      extent.width(width);
      extent.height(height);
      extent.depth(1);

      VkImageCreateInfo ci = VkImageCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
      ci.imageType(VK10.VK_IMAGE_TYPE_2D);
      ci.format(COLOR_FORMAT);
      ci.extent(extent);
      ci.mipLevels(1);
      ci.arrayLayers(1);
      ci.samples(VK10.VK_SAMPLE_COUNT_1_BIT);
      ci.tiling(VK10.VK_IMAGE_TILING_OPTIMAL);
      ci.usage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
            | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT);
      ci.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
      ci.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);

      LongBuffer pImage = stack.mallocLong(1);
      int err = VK10.vkCreateImage(device, ci, null, pImage);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateImage failed: " + VulkanContext.vulkanError(err));
      }
      this.image = pImage.get(0);
   }

   private void allocateAndBind(VkDevice device, MemoryStack stack) {
      VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
      VK10.vkGetImageMemoryRequirements(device, this.image, req);
      int typeBits = (int) req.memoryTypeBits();
      int type = this.ctx.findMemoryType(typeBits, VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
      if (type < 0) {
         throw new IllegalStateException("No device-local memory type for offscreen image (typeBits=" + typeBits + ")");
      }

      VkMemoryAllocateInfo ai = VkMemoryAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
      ai.allocationSize(req.size());
      ai.memoryTypeIndex(type);

      LongBuffer pMem = stack.mallocLong(1);
      int err = VK10.vkAllocateMemory(device, ai, null, pMem);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkAllocateMemory (offscreen) failed: " + VulkanContext.vulkanError(err));
      }
      this.imageMemory = pMem.get(0);

      err = VK10.vkBindImageMemory(device, this.image, this.imageMemory, 0);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkBindImageMemory failed: " + VulkanContext.vulkanError(err));
      }
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
      ci.format(COLOR_FORMAT);
      ci.components(components);
      ci.subresourceRange(range);

      LongBuffer pView = stack.mallocLong(1);
      int err = VK10.vkCreateImageView(device, ci, null, pView);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateImageView failed: " + VulkanContext.vulkanError(err));
      }
      this.imageView = pView.get(0);
   }

   private void createRenderPass(VkDevice device, MemoryStack stack) {
      VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1, stack);
      attachments.get(0)
            .format(COLOR_FORMAT)
            .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

      VkAttachmentReference.Buffer colorRefs = VkAttachmentReference.calloc(1, stack);
      colorRefs.get(0)
            .attachment(0)
            .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

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
         throw new IllegalStateException("vkCreateRenderPass failed: " + VulkanContext.vulkanError(err));
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
      ci.width(width);
      ci.height(height);
      ci.layers(1);

      LongBuffer pFb = stack.mallocLong(1);
      int err = VK10.vkCreateFramebuffer(device, ci, null, pFb);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateFramebuffer failed: " + VulkanContext.vulkanError(err));
      }
      this.framebuffer = pFb.get(0);
   }

   public void destroy() {
      VkDevice device = this.ctx.device();
      if (device == null) {
         return;
      }
      if (this.framebuffer != 0) {
         VK10.vkDestroyFramebuffer(device, this.framebuffer, null);
         this.framebuffer = 0;
      }
      if (this.renderPass != 0) {
         VK10.vkDestroyRenderPass(device, this.renderPass, null);
         this.renderPass = 0;
      }
      if (this.imageView != 0) {
         VK10.vkDestroyImageView(device, this.imageView, null);
         this.imageView = 0;
      }
      if (this.image != 0) {
         VK10.vkDestroyImage(device, this.image, null);
         this.image = 0;
      }
       if (this.imageMemory != 0) {
          VK10.vkFreeMemory(device, this.imageMemory, null);
          this.imageMemory = 0;
       }
       this.destroyReadbackResources();
       this.width = 0;
       this.height = 0;
    }

   private void destroyReadbackResources() {
      VkDevice device = this.ctx.device();
      if (this.readbackCb != null) {
         VK10.vkFreeCommandBuffers(device, this.ctx.commandPoolHandle(), this.readbackCb);
         this.readbackCb = null;
      }
      if (this.readbackFence != 0) {
         VK10.vkDestroyFence(device, this.readbackFence, null);
         this.readbackFence = 0;
      }
      if (this.readbackBuffer != 0) {
         VK10.vkDestroyBuffer(device, this.readbackBuffer, null);
         this.readbackBuffer = 0;
      }
      if (this.readbackMemory != 0) {
         VK10.vkFreeMemory(device, this.readbackMemory, null);
         this.readbackMemory = 0;
      }
      this.readbackBufferSize = 0;
      this.readbackCopy = null;
   }

   private void ensureReadbackResources(MemoryStack stack, long size) {
      VkDevice device = this.ctx.device();
      if (this.readbackFence == 0) {
         org.lwjgl.vulkan.VkFenceCreateInfo fci = org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack);
         fci.sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
         java.nio.LongBuffer pFence = stack.mallocLong(1);
         int err = VK10.vkCreateFence(device, fci, null, pFence);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("readback vkCreateFence failed: " + VulkanContext.vulkanError(err));
         }
         this.readbackFence = pFence.get(0);
      }
      if (this.readbackCb == null) {
         VkCommandBufferAllocateInfo ai = VkCommandBufferAllocateInfo.calloc(stack);
         ai.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
         ai.commandPool(this.ctx.commandPoolHandle());
         ai.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
         ai.commandBufferCount(1);
         org.lwjgl.PointerBuffer pCb = stack.mallocPointer(1);
         int err = VK10.vkAllocateCommandBuffers(device, ai, pCb);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("readback vkAllocateCommandBuffers failed: " + VulkanContext.vulkanError(err));
         }
         this.readbackCb = new VkCommandBuffer(pCb.get(0), device);
      }
      if (this.readbackBufferSize < size) {
         if (this.readbackBuffer != 0) {
            VK10.vkDestroyBuffer(device, this.readbackBuffer, null);
            this.readbackBuffer = 0;
         }
         if (this.readbackMemory != 0) {
            VK10.vkFreeMemory(device, this.readbackMemory, null);
            this.readbackMemory = 0;
         }
         VkBufferCreateInfo bci = VkBufferCreateInfo.calloc(stack);
         bci.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
         bci.size(size);
         bci.usage(VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT);
         bci.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
         java.nio.LongBuffer pBuffer = stack.mallocLong(1);
         int err = VK10.vkCreateBuffer(device, bci, null, pBuffer);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("readback vkCreateBuffer failed: " + VulkanContext.vulkanError(err));
         }
         this.readbackBuffer = pBuffer.get(0);

         VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
         VK10.vkGetBufferMemoryRequirements(device, this.readbackBuffer, req);
         int type = this.ctx.findMemoryType((int) req.memoryTypeBits(),
               VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
         if (type < 0) {
            VK10.vkDestroyBuffer(device, this.readbackBuffer, null);
            this.readbackBuffer = 0;
            throw new IllegalStateException("No host-visible memory for readback");
         }
         VkMemoryAllocateInfo ai2 = VkMemoryAllocateInfo.calloc(stack);
         ai2.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
         ai2.allocationSize(req.size());
         ai2.memoryTypeIndex(type);
         err = VK10.vkAllocateMemory(device, ai2, null, pBuffer);
         if (err != VK10.VK_SUCCESS) {
            VK10.vkDestroyBuffer(device, this.readbackBuffer, null);
            this.readbackBuffer = 0;
            throw new IllegalStateException("readback vkAllocateMemory failed: " + VulkanContext.vulkanError(err));
         }
         this.readbackMemory = pBuffer.get(0);
         err = VK10.vkBindBufferMemory(device, this.readbackBuffer, this.readbackMemory, 0);
         if (err != VK10.VK_SUCCESS) {
            VK10.vkDestroyBuffer(device, this.readbackBuffer, null);
            this.readbackBuffer = 0;
            VK10.vkFreeMemory(device, this.readbackMemory, null);
            this.readbackMemory = 0;
            throw new IllegalStateException("readback vkBindBufferMemory failed: " + VulkanContext.vulkanError(err));
         }
         this.readbackBufferSize = req.size();
         this.readbackCopy = java.nio.ByteBuffer.allocateDirect((int) size).order(java.nio.ByteOrder.nativeOrder());
      }
   }

   public java.nio.ByteBuffer readbackRGBA() {
      if (this.image == 0 || this.width <= 0 || this.height <= 0) {
         return null;
      }
      VkDevice device = this.ctx.device();
      long size = (long) this.width * this.height * 4L;
      try (MemoryStack stack = MemoryStack.stackPush()) {
         this.ensureReadbackResources(stack, size);

         VK10.vkResetFences(device, this.readbackFence);
         VK10.vkResetCommandBuffer(this.readbackCb, 0);

         VkCommandBufferBeginInfo bi = VkCommandBufferBeginInfo.calloc(stack);
         bi.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
         bi.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
         int err = VK10.vkBeginCommandBuffer(this.readbackCb, bi);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("readback vkBeginCommandBuffer failed: " + VulkanContext.vulkanError(err));
         }

         this.barrier(stack, this.readbackCb, this.image, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
               VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK10.VK_ACCESS_SHADER_READ_BIT,
               VK10.VK_ACCESS_TRANSFER_READ_BIT, VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
               VK10.VK_PIPELINE_STAGE_TRANSFER_BIT);

         VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
         region.get(0)
               .bufferOffset(0)
               .bufferRowLength(this.width)
               .bufferImageHeight(this.height)
               .imageSubresource()
               .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
               .mipLevel(0)
               .baseArrayLayer(0)
               .layerCount(1);
         region.get(0).imageOffset().set(0, 0, 0);
         region.get(0).imageExtent().set(this.width, this.height, 1);

         VK10.vkCmdCopyImageToBuffer(this.readbackCb, this.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, this.readbackBuffer, region);

         this.barrier(stack, this.readbackCb, this.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
               VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK10.VK_ACCESS_TRANSFER_READ_BIT,
               VK10.VK_ACCESS_SHADER_READ_BIT, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
               VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);

         err = VK10.vkEndCommandBuffer(this.readbackCb);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("readback vkEndCommandBuffer failed: " + VulkanContext.vulkanError(err));
         }
         VkSubmitInfo si = VkSubmitInfo.calloc(stack);
         si.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);
         org.lwjgl.PointerBuffer pCb = stack.mallocPointer(1);
         pCb.put(0, this.readbackCb);
         si.pCommandBuffers(pCb);
         err = VK10.vkQueueSubmit(this.ctx.graphicsQueue(), si, this.readbackFence);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("readback vkQueueSubmit failed: " + VulkanContext.vulkanError(err));
         }
         err = VK10.vkWaitForFences(device, this.readbackFence, true, 5_000_000_000L);
         if (err != VK10.VK_SUCCESS) {
            System.err.println("[Zero/Vulkan] readback vkWaitForFences failed: " + VulkanContext.vulkanError(err));
         }

         org.lwjgl.PointerBuffer pData = stack.mallocPointer(1);
         err = VK10.vkMapMemory(device, this.readbackMemory, 0, size, 0, pData);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("readback vkMapMemory failed: " + VulkanContext.vulkanError(err));
         }
         java.nio.ByteBuffer mapped = org.lwjgl.system.MemoryUtil.memByteBuffer(pData.get(0), (int) size);
         java.nio.ByteBuffer copy = this.readbackCopy;
         copy.clear();
         copy.put(mapped);
         copy.flip();
         VK10.vkUnmapMemory(device, this.readbackMemory);
         return copy;
      }
   }

   private void barrier(MemoryStack stack, VkCommandBuffer cb, long image, int oldLayout, int newLayout,
         int srcAccess, int dstAccess, int srcStage, int dstStage) {
      VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack);
      range.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
      range.baseMipLevel(0);
      range.levelCount(1);
      range.baseArrayLayer(0);
      range.layerCount(1);

      VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
      barrier.get(0)
            .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .oldLayout(oldLayout)
            .newLayout(newLayout)
            .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
            .image(image)
            .subresourceRange(range)
            .srcAccessMask(srcAccess)
            .dstAccessMask(dstAccess);

      VK10.vkCmdPipelineBarrier(cb, srcStage, dstStage, 0, null, null, barrier);
   }

   private VkCommandBuffer beginOneTime(MemoryStack stack) {
      VkCommandBufferAllocateInfo ai = VkCommandBufferAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
      ai.commandPool(this.ctx.commandPoolHandle());
      ai.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
      ai.commandBufferCount(1);

      org.lwjgl.PointerBuffer pCb = stack.mallocPointer(1);
      int err = VK10.vkAllocateCommandBuffers(this.ctx.device(), ai, pCb);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("readback vkAllocateCommandBuffers failed: " + VulkanContext.vulkanError(err));
      }
      VkCommandBuffer cb = new VkCommandBuffer(pCb.get(0), this.ctx.device());

      VkCommandBufferBeginInfo bi = VkCommandBufferBeginInfo.calloc(stack);
      bi.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
      bi.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
      err = VK10.vkBeginCommandBuffer(cb, bi);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("readback vkBeginCommandBuffer failed: " + VulkanContext.vulkanError(err));
      }
      return cb;
   }

   private void endAndSubmitWait(MemoryStack stack, VkCommandBuffer cb) {
      int err = VK10.vkEndCommandBuffer(cb);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("readback vkEndCommandBuffer failed: " + VulkanContext.vulkanError(err));
      }
      VkSubmitInfo si = VkSubmitInfo.calloc(stack);
      si.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);
      org.lwjgl.PointerBuffer pCb = stack.mallocPointer(1);
      pCb.put(0, cb);
      si.pCommandBuffers(pCb);

      java.nio.LongBuffer pFence = stack.mallocLong(1);
      org.lwjgl.vulkan.VkFenceCreateInfo fci = org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack);
      fci.sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
      err = VK10.vkCreateFence(this.ctx.device(), fci, null, pFence);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("readback vkCreateFence failed: " + VulkanContext.vulkanError(err));
      }
      long fence = pFence.get(0);
      err = VK10.vkQueueSubmit(this.ctx.graphicsQueue(), si, fence);
      if (err != VK10.VK_SUCCESS) {
         VK10.vkDestroyFence(this.ctx.device(), fence, null);
         throw new IllegalStateException("readback vkQueueSubmit failed: " + VulkanContext.vulkanError(err));
      }
      err = VK10.vkWaitForFences(this.ctx.device(), fence, true, 5_000_000_000L);
      if (err != VK10.VK_SUCCESS) {
         System.err.println("[Zero/Vulkan] readback vkWaitForFences failed: " + VulkanContext.vulkanError(err));
      }
      VK10.vkDestroyFence(this.ctx.device(), fence, null);
      VK10.vkFreeCommandBuffers(this.ctx.device(), this.ctx.commandPoolHandle(), cb);
   }
}
