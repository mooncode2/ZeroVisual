package ru.zero.util.render.backends.vk;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkComponentMapping;

@Environment(EnvType.CLIENT)
public final class VulkanTextureManager {
   public static final int FORMAT_RGBA8 = VK10.VK_FORMAT_R8G8B8A8_UNORM;
   public static final int FORMAT_R8 = VK10.VK_FORMAT_R8_UNORM;

   private final VulkanContext ctx;
   private final Map<Integer, VulkanTexture> textures = new HashMap<>();
   private final AtomicInteger nextId = new AtomicInteger(1);
   private long sharedSampler;
   private boolean created;

   public VulkanTextureManager(VulkanContext ctx) {
      this.ctx = ctx;
   }

   public static final class VulkanTexture {
      public long image;
      public long memory;
      public long view;
      public int width;
      public int height;
      public int format;
   }

   public long sharedSampler() {
      return this.sharedSampler;
   }

   public VulkanTexture texture(int handle) {
      return this.textures.get(handle);
   }

   public void create() {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         this.createSharedSampler(stack);
      }
      this.created = true;
      System.out.println("[Zero/Vulkan] TextureManager ready (shared sampler=" + this.sharedSampler + ")");
   }

   private void createSharedSampler(MemoryStack stack) {
      VkSamplerCreateInfo ci = VkSamplerCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);
      ci.magFilter(VK10.VK_FILTER_LINEAR);
      ci.minFilter(VK10.VK_FILTER_LINEAR);
      ci.mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR);
      ci.addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
      ci.addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
      ci.addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
      ci.minLod(0.0F);
      ci.maxLod(0.25F);

      LongBuffer pSampler = stack.mallocLong(1);
      int err = VK10.vkCreateSampler(this.ctx.device(), ci, null, pSampler);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateSampler failed: " + VulkanContext.vulkanError(err));
      }
      this.sharedSampler = pSampler.get(0);
   }

   public int createTexture(int width, int height, int format, ByteBuffer data) {
      VulkanTexture tex = new VulkanTexture();
      tex.width = width;
      tex.height = height;
      tex.format = format;
      try (MemoryStack stack = MemoryStack.stackPush()) {
         this.createImage(stack, tex);
         this.allocateAndBind(stack, tex);
         this.createView(stack, tex);
         this.transitionToDst(stack, tex);
         if (data != null) {
            this.uploadData(stack, tex, data, width, height, 0, 0, width);
         }
         this.transitionToShaderRead(stack, tex);
      }
      int id = this.nextId.getAndIncrement();
      this.textures.put(id, tex);
      return id;
   }

   private void createImage(MemoryStack stack, VulkanTexture tex) {
      org.lwjgl.vulkan.VkExtent3D extent = org.lwjgl.vulkan.VkExtent3D.calloc(stack);
      extent.width(tex.width);
      extent.height(tex.height);
      extent.depth(1);

      VkImageCreateInfo ci = VkImageCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
      ci.imageType(VK10.VK_IMAGE_TYPE_2D);
      ci.format(tex.format);
      ci.extent(extent);
      ci.mipLevels(1);
      ci.arrayLayers(1);
      ci.samples(VK10.VK_SAMPLE_COUNT_1_BIT);
      ci.tiling(VK10.VK_IMAGE_TILING_OPTIMAL);
      ci.usage(VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT);
      ci.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
      ci.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);

      LongBuffer pImage = stack.mallocLong(1);
      int err = VK10.vkCreateImage(this.ctx.device(), ci, null, pImage);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateImage (texture) failed: " + VulkanContext.vulkanError(err));
      }
      tex.image = pImage.get(0);
   }

   private void allocateAndBind(MemoryStack stack, VulkanTexture tex) {
      VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
      VK10.vkGetImageMemoryRequirements(this.ctx.device(), tex.image, req);
      int type = this.ctx.findMemoryType((int) req.memoryTypeBits(), VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
      if (type < 0) {
         throw new IllegalStateException("No device-local memory for texture");
      }
      VkMemoryAllocateInfo ai = VkMemoryAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
      ai.allocationSize(req.size());
      ai.memoryTypeIndex(type);

      LongBuffer pMem = stack.mallocLong(1);
      int err = VK10.vkAllocateMemory(this.ctx.device(), ai, null, pMem);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkAllocateMemory (texture) failed: " + VulkanContext.vulkanError(err));
      }
      tex.memory = pMem.get(0);
      err = VK10.vkBindImageMemory(this.ctx.device(), tex.image, tex.memory, 0);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkBindImageMemory (texture) failed: " + VulkanContext.vulkanError(err));
      }
   }

   private void createView(MemoryStack stack, VulkanTexture tex) {
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
      ci.image(tex.image);
      ci.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);
      ci.format(tex.format);
      ci.components(components);
      ci.subresourceRange(range);

      LongBuffer pView = stack.mallocLong(1);
      int err = VK10.vkCreateImageView(this.ctx.device(), ci, null, pView);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateImageView (texture) failed: " + VulkanContext.vulkanError(err));
      }
      tex.view = pView.get(0);
   }

   private void transitionToDst(MemoryStack stack, VulkanTexture tex) {
      VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack);
      range.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
      range.baseMipLevel(0);
      range.levelCount(1);
      range.baseArrayLayer(0);
      range.layerCount(1);

      VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
      barrier.get(0)
            .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .oldLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
            .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
            .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
            .image(tex.image)
            .subresourceRange(range)
            .srcAccessMask(0)
            .dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);

      VkCommandBuffer cb = this.beginOneTime(stack);
      VK10.vkCmdPipelineBarrier(cb, VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);
      this.endAndSubmitWait(stack, cb);
   }

   private void transitionToShaderRead(MemoryStack stack, VulkanTexture tex) {
      VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack);
      range.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
      range.baseMipLevel(0);
      range.levelCount(1);
      range.baseArrayLayer(0);
      range.layerCount(1);

      VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
      barrier.get(0)
            .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
            .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
            .image(tex.image)
            .subresourceRange(range)
            .srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
            .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);

      VkCommandBuffer cb = this.beginOneTime(stack);
      VK10.vkCmdPipelineBarrier(cb, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barrier);
      this.endAndSubmitWait(stack, cb);
   }

   public void uploadAlphaSubImage(int handle, int x, int y, int w, int h, ByteBuffer data, int sourceRowLength) {
      VulkanTexture tex = this.textures.get(handle);
      if (tex == null || w <= 0 || h <= 0) {
         return;
      }
      try (MemoryStack stack = MemoryStack.stackPush()) {
         this.transitionShaderToDst(stack, tex);
         this.uploadData(stack, tex, data, w, h, x, y, sourceRowLength);
         this.transitionToShaderRead(stack, tex);
      }
   }

   private void transitionShaderToDst(MemoryStack stack, VulkanTexture tex) {
      VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack);
      range.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
      range.baseMipLevel(0);
      range.levelCount(1);
      range.baseArrayLayer(0);
      range.layerCount(1);

      VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
      barrier.get(0)
            .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .oldLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
            .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
            .image(tex.image)
            .subresourceRange(range)
            .srcAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
            .dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);

      VkCommandBuffer cb = this.beginOneTime(stack);
      VK10.vkCmdPipelineBarrier(cb, VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);
      this.endAndSubmitWait(stack, cb);
   }

   private void uploadData(MemoryStack stack, VulkanTexture tex, ByteBuffer data, int w, int h, int x, int y,
         int sourceRowLength) {
      VkDevice device = this.ctx.device();
      int bytesPerPixel = tex.format == FORMAT_R8 ? 1 : 4;
      long rowBytes = (long) w * bytesPerPixel;
      long stagingSize = rowBytes * h;

      long staging = this.createStagingBuffer(stack, stagingSize);
      long stagingMemory = this.bindStagingHostVisible(stack, staging, stagingSize);
      ByteBuffer mapped = this.mapStaging(stack, stagingMemory, stagingSize);
      if (sourceRowLength <= 0 || sourceRowLength == w) {
         mapped.put(data);
      } else {
         int dataPos = data.position();
         for (int row = 0; row < h; row++) {
            int limit = data.limit();
            data.limit(dataPos + row * sourceRowLength + w * bytesPerPixel);
            data.position(dataPos + row * sourceRowLength);
            mapped.put(data);
            data.limit(limit);
         }
         data.position(dataPos);
      }
      mapped.flip();
      VK10.vkUnmapMemory(device, stagingMemory);

      VkCommandBuffer cb = this.beginOneTime(stack);

      VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
      region.get(0)
            .bufferOffset(0)
            .bufferRowLength(sourceRowLength > 0 ? sourceRowLength : w)
            .bufferImageHeight(h)
            .imageSubresource()
            .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
            .mipLevel(0)
            .baseArrayLayer(0)
            .layerCount(1);
      region.get(0).imageOffset().set(x, y, 0);
      region.get(0).imageExtent().set(w, h, 1);

      VK10.vkCmdCopyBufferToImage(cb, staging, tex.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
      this.endAndSubmitWait(stack, cb);

      VK10.vkDestroyBuffer(device, staging, null);
      VK10.vkFreeMemory(device, stagingMemory, null);
   }

   private long createStagingBuffer(MemoryStack stack, long size) {
      org.lwjgl.vulkan.VkBufferCreateInfo ci = org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
      ci.size(size);
      ci.usage(VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
      ci.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

      LongBuffer pBuffer = stack.mallocLong(1);
      int err = VK10.vkCreateBuffer(this.ctx.device(), ci, null, pBuffer);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateBuffer (staging) failed: " + VulkanContext.vulkanError(err));
      }
      return pBuffer.get(0);
   }

   private long bindStagingHostVisible(MemoryStack stack, long buffer, long size) {
      VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
      VK10.vkGetBufferMemoryRequirements(this.ctx.device(), buffer, req);
      int type = this.ctx.findMemoryType((int) req.memoryTypeBits(),
            VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
      if (type < 0) {
         throw new IllegalStateException("No host-visible memory for staging buffer");
      }
      VkMemoryAllocateInfo ai = VkMemoryAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
      ai.allocationSize(req.size());
      ai.memoryTypeIndex(type);

      LongBuffer pMem = stack.mallocLong(1);
      int err = VK10.vkAllocateMemory(this.ctx.device(), ai, null, pMem);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkAllocateMemory (staging) failed: " + VulkanContext.vulkanError(err));
      }
      long mem = pMem.get(0);
      err = VK10.vkBindBufferMemory(this.ctx.device(), buffer, mem, 0);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkBindBufferMemory (staging) failed: " + VulkanContext.vulkanError(err));
      }
      return mem;
   }

   private ByteBuffer mapStaging(MemoryStack stack, long memory, long size) {
      org.lwjgl.PointerBuffer pData = stack.mallocPointer(1);
      int err = VK10.vkMapMemory(this.ctx.device(), memory, 0, size, 0, pData);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkMapMemory (staging) failed: " + VulkanContext.vulkanError(err));
      }
      return org.lwjgl.system.MemoryUtil.memByteBuffer(pData.get(0), (int) size);
   }

   private VkCommandBuffer beginOneTime(MemoryStack stack) {
      org.lwjgl.vulkan.VkCommandBufferAllocateInfo ai =
            org.lwjgl.vulkan.VkCommandBufferAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
      ai.commandPool(this.ctx.commandPoolHandle());
      ai.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
      ai.commandBufferCount(1);

      org.lwjgl.PointerBuffer pCb = stack.mallocPointer(1);
      int err = VK10.vkAllocateCommandBuffers(this.ctx.device(), ai, pCb);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkAllocateCommandBuffers (one-time) failed: " + vulkanError(err));
      }
      VkCommandBuffer cb = new VkCommandBuffer(pCb.get(0), this.ctx.device());

      VkCommandBufferBeginInfo bi = VkCommandBufferBeginInfo.calloc(stack);
      bi.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
      bi.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
      err = VK10.vkBeginCommandBuffer(cb, bi);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkBeginCommandBuffer failed: " + vulkanError(err));
      }
      return cb;
   }

   private void endAndSubmitWait(MemoryStack stack, VkCommandBuffer cb) {
      int err = VK10.vkEndCommandBuffer(cb);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkEndCommandBuffer failed: " + vulkanError(err));
      }

      VkSubmitInfo si = VkSubmitInfo.calloc(stack);
      si.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);
      org.lwjgl.PointerBuffer pCb = stack.mallocPointer(1);
      pCb.put(0, cb);
      si.pCommandBuffers(pCb);

      LongBuffer pFence = stack.mallocLong(1);
      err = VK10.vkCreateFence(this.ctx.device(), org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO), null, pFence);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateFence (upload) failed: " + vulkanError(err));
      }
      long fence = pFence.get(0);
      err = VK10.vkQueueSubmit(this.ctx.graphicsQueue(), si, fence);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkQueueSubmit (upload) failed: " + vulkanError(err));
      }
      err = VK10.vkWaitForFences(this.ctx.device(), fence, true, 1_000_000_000L);
      if (err != VK10.VK_SUCCESS) {
         System.err.println("[Zero/Vulkan] vkWaitForFences (upload) failed: " + vulkanError(err));
      }
      VK10.vkDestroyFence(this.ctx.device(), fence, null);
      VK10.vkFreeCommandBuffers(this.ctx.device(), this.ctx.commandPoolHandle(), cb);
   }

   public void destroyTexture(int handle) {
      VulkanTexture tex = this.textures.remove(handle);
      if (tex == null) {
         return;
      }
      VkDevice device = this.ctx.device();
      if (tex.view != 0) {
         VK10.vkDestroyImageView(device, tex.view, null);
      }
      if (tex.image != 0) {
         VK10.vkDestroyImage(device, tex.image, null);
      }
      if (tex.memory != 0) {
         VK10.vkFreeMemory(device, tex.memory, null);
      }
   }

   public void destroy() {
      if (!this.created) {
         return;
      }
      for (Integer handle : new java.util.ArrayList<>(this.textures.keySet())) {
         this.destroyTexture(handle);
      }
      if (this.sharedSampler != 0) {
         VK10.vkDestroySampler(this.ctx.device(), this.sharedSampler, null);
         this.sharedSampler = 0;
      }
      this.created = false;
      System.out.println("[Zero/Vulkan] TextureManager destroyed");
   }

   private static String vulkanError(int err) {
      return VulkanContext.vulkanError(err);
   }
}
