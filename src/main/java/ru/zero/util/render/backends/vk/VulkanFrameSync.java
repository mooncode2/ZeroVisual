package ru.zero.util.render.backends.vk;

import java.nio.LongBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

@Environment(EnvType.CLIENT)
public final class VulkanFrameSync {
   private final VulkanContext ctx;
   private long renderFinishedSemaphore;
   private long imageAvailableSemaphore;
   private long frameFence;
   private VkCommandBuffer commandBuffer;
   private boolean created;

   public VulkanFrameSync(VulkanContext ctx) {
      this.ctx = ctx;
   }

   public long renderFinishedSemaphore() {
      return this.renderFinishedSemaphore;
   }

   public long imageAvailableSemaphore() {
      return this.imageAvailableSemaphore;
   }

   public long frameFence() {
      return this.frameFence;
   }

   public VkCommandBuffer commandBuffer() {
      return this.commandBuffer;
   }

   public boolean isCreated() {
      return this.created;
   }

   public void create() {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         this.createSemaphores(stack);
         this.createFence(stack);
         this.allocateCommandBuffer(stack);
      }
      this.created = true;
      System.out.println("[Zero/Vulkan] Frame sync created (semaphore=" + this.renderFinishedSemaphore + ", fence="
            + this.frameFence + ")");
   }

   private void createSemaphores(MemoryStack stack) {
      VkSemaphoreCreateInfo ci = VkSemaphoreCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

      LongBuffer pSem = stack.mallocLong(1);
      int err = VK10.vkCreateSemaphore(this.ctx.device(), ci, null, pSem);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateSemaphore (renderFinished) failed: " + vulkanError(err));
      }
      this.renderFinishedSemaphore = pSem.get(0);

      err = VK10.vkCreateSemaphore(this.ctx.device(), ci, null, pSem);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateSemaphore (imageAvailable) failed: " + vulkanError(err));
      }
      this.imageAvailableSemaphore = pSem.get(0);
   }

   private void createFence(MemoryStack stack) {
      VkFenceCreateInfo ci = VkFenceCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
      ci.flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);

      LongBuffer pFence = stack.mallocLong(1);
      int err = VK10.vkCreateFence(this.ctx.device(), ci, null, pFence);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateFence failed: " + vulkanError(err));
      }
      this.frameFence = pFence.get(0);
   }

   private void allocateCommandBuffer(MemoryStack stack) {
      VkCommandBufferAllocateInfo ci = VkCommandBufferAllocateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
      ci.commandPool(this.ctx.commandPoolHandle());
      ci.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
      ci.commandBufferCount(1);

      PointerBuffer pCb = stack.mallocPointer(1);
      int err = VK10.vkAllocateCommandBuffers(this.ctx.device(), ci, pCb);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkAllocateCommandBuffers failed: " + vulkanError(err));
      }
      this.commandBuffer = new VkCommandBuffer(pCb.get(0), this.ctx.device());
   }

   public void waitForFence() {
      int err = VK10.vkWaitForFences(this.ctx.device(), this.frameFence, true, 1_000_000_000L);
      if (err != VK10.VK_SUCCESS && err != VK10.VK_TIMEOUT) {
         System.err.println("[Zero/Vulkan] vkWaitForFences failed: " + vulkanError(err));
      }
      VK10.vkResetFences(this.ctx.device(), this.frameFence);
   }

   public void destroy() {
      if (!this.created) {
         return;
      }
      if (this.commandBuffer != null && this.ctx.commandPoolHandle() != 0) {
         VK10.vkFreeCommandBuffers(this.ctx.device(), this.ctx.commandPoolHandle(), this.commandBuffer);
         this.commandBuffer = null;
      }
      if (this.frameFence != 0) {
         VK10.vkDestroyFence(this.ctx.device(), this.frameFence, null);
         this.frameFence = 0;
      }
      if (this.imageAvailableSemaphore != 0) {
         VK10.vkDestroySemaphore(this.ctx.device(), this.imageAvailableSemaphore, null);
         this.imageAvailableSemaphore = 0;
      }
      if (this.renderFinishedSemaphore != 0) {
         VK10.vkDestroySemaphore(this.ctx.device(), this.renderFinishedSemaphore, null);
         this.renderFinishedSemaphore = 0;
      }
      this.created = false;
   }

   private static String vulkanError(int err) {
      return VulkanContext.vulkanError(err);
   }
}
