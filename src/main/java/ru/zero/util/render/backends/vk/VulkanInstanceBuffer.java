package ru.zero.util.render.backends.vk;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

@Environment(EnvType.CLIENT)
public final class VulkanInstanceBuffer {
   private final VulkanContext ctx;
   private long buffer;
   private long memory;
   private long size;
   private ByteBuffer mapped;
   private boolean created;

   public VulkanInstanceBuffer(VulkanContext ctx) {
      this.ctx = ctx;
   }

   public long buffer() {
      return this.buffer;
   }

   public long size() {
      return this.size;
   }

   public ByteBuffer mapped() {
      return this.mapped;
   }

   public void create(long size) {
      this.size = size;
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkBufferCreateInfo ci = VkBufferCreateInfo.calloc(stack);
         ci.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
         ci.size(size);
         ci.usage(VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT);
         ci.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

         LongBuffer pBuffer = stack.mallocLong(1);
         int err = VK10.vkCreateBuffer(this.ctx.device(), ci, null, pBuffer);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkCreateBuffer (ssbo) failed: " + VulkanContext.vulkanError(err));
         }
         this.buffer = pBuffer.get(0);

         VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
         VK10.vkGetBufferMemoryRequirements(this.ctx.device(), this.buffer, req);
         int type = this.ctx.findMemoryType((int) req.memoryTypeBits(),
               VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
         if (type < 0) {
            throw new IllegalStateException("No host-visible coherent memory for SSBO");
         }

         VkMemoryAllocateInfo ai = VkMemoryAllocateInfo.calloc(stack);
         ai.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
         ai.allocationSize(req.size());
         ai.memoryTypeIndex(type);

         LongBuffer pMem = stack.mallocLong(1);
         err = VK10.vkAllocateMemory(this.ctx.device(), ai, null, pMem);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkAllocateMemory (ssbo) failed: " + VulkanContext.vulkanError(err));
         }
         this.memory = pMem.get(0);

         err = VK10.vkBindBufferMemory(this.ctx.device(), this.buffer, this.memory, 0);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkBindBufferMemory (ssbo) failed: " + VulkanContext.vulkanError(err));
         }

         org.lwjgl.PointerBuffer pData = stack.mallocPointer(1);
         err = VK10.vkMapMemory(this.ctx.device(), this.memory, 0, req.size(), 0, pData);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkMapMemory (ssbo) failed: " + VulkanContext.vulkanError(err));
         }
         this.mapped = MemoryUtil.memByteBuffer(pData.get(0), (int) size);
      }
      this.created = true;
      System.out.println("[Zero/Vulkan] Instance SSBO created (size=" + size + ", handle=" + this.buffer + ")");
   }

   public void destroy() {
      if (!this.created) {
         return;
      }
      if (this.mapped != null) {
         VK10.vkUnmapMemory(this.ctx.device(), this.memory);
         this.mapped = null;
      }
      if (this.buffer != 0) {
         VK10.vkDestroyBuffer(this.ctx.device(), this.buffer, null);
         this.buffer = 0;
      }
      if (this.memory != 0) {
         VK10.vkFreeMemory(this.ctx.device(), this.memory, null);
         this.memory = 0;
      }
      this.created = false;
   }
}
