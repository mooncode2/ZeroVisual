package ru.zero.util.render.backends.vk;

import java.nio.LongBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import ru.zero.util.render.backends.ShapeInstanceBatch;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

@Environment(EnvType.CLIENT)
public final class VulkanDescriptors {
   public static final int MAX_TEXTURE_SLOTS = 16;
   public static final int INSTANCE_SSBO_BINDING = 0;
   public static final int SAMPLERS_BINDING = 0;
   public static final int PUSH_CONSTANT_RANGE_SIZE = 8;

   private final VulkanContext ctx;
   private long set0Layout;
   private long set1Layout;
   private long pipelineLayout;
   private long descriptorPool;
   private long ssboSet;
   private long samplerSet;
   private static final int SAMPLER_SET_RING = 128;
   private final long[] samplerSets = new long[SAMPLER_SET_RING];
   private int samplerSetCursor;
   private boolean created;

   public VulkanDescriptors(VulkanContext ctx) {
      this.ctx = ctx;
   }

   public long set0Layout() {
      return this.set0Layout;
   }

   public long set1Layout() {
      return this.set1Layout;
   }

   public long pipelineLayout() {
      return this.pipelineLayout;
   }

   public long ssboSet() {
      return this.ssboSet;
   }

   public long samplerSet() {
      return this.samplerSet;
   }

   public void create() {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         this.createSet0Layout(stack);
         this.createSet1Layout(stack);
         this.createPipelineLayout(stack);
         this.createPool(stack);
         this.allocateSets(stack);
      }
      this.created = true;
      System.out.println("[Zero/Vulkan] Descriptors ready (set0=SSBO, set1=sampler[16], pipelineLayout="
            + this.pipelineLayout + ")");
   }

   private void createSet0Layout(MemoryStack stack) {
      VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
      bindings.get(0)
            .binding(INSTANCE_SSBO_BINDING)
            .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
            .descriptorCount(1)
            .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT);

      VkDescriptorSetLayoutCreateInfo ci = VkDescriptorSetLayoutCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
      ci.pBindings(bindings);

      LongBuffer pLayout = stack.mallocLong(1);
      int err = VK10.vkCreateDescriptorSetLayout(this.ctx.device(), ci, null, pLayout);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateDescriptorSetLayout (set0) failed: " + vulkanError(err));
      }
      this.set0Layout = pLayout.get(0);
   }

   private void createSet1Layout(MemoryStack stack) {
      VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
      bindings.get(0)
            .binding(SAMPLERS_BINDING)
            .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(MAX_TEXTURE_SLOTS)
            .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);

      VkDescriptorSetLayoutCreateInfo ci = VkDescriptorSetLayoutCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
      ci.pBindings(bindings);

      LongBuffer pLayout = stack.mallocLong(1);
      int err = VK10.vkCreateDescriptorSetLayout(this.ctx.device(), ci, null, pLayout);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateDescriptorSetLayout (set1) failed: " + vulkanError(err));
      }
      this.set1Layout = pLayout.get(0);
   }

   private void createPipelineLayout(MemoryStack stack) {
      LongBuffer pSetLayouts = stack.mallocLong(2);
      pSetLayouts.put(0, this.set0Layout);
      pSetLayouts.put(1, this.set1Layout);

      org.lwjgl.vulkan.VkPushConstantRange.Buffer pushRanges =
            org.lwjgl.vulkan.VkPushConstantRange.calloc(1, stack);
      pushRanges.get(0)
            .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT)
            .offset(0)
            .size(PUSH_CONSTANT_RANGE_SIZE);

      org.lwjgl.vulkan.VkPipelineLayoutCreateInfo ci =
            org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
      ci.pSetLayouts(pSetLayouts);
      ci.pPushConstantRanges(pushRanges);

      LongBuffer pLayout = stack.mallocLong(1);
      int err = VK10.vkCreatePipelineLayout(this.ctx.device(), ci, null, pLayout);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreatePipelineLayout failed: " + vulkanError(err));
      }
      this.pipelineLayout = pLayout.get(0);
   }

   private void createPool(MemoryStack stack) {
      VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(2, stack);
      sizes.get(0)
            .type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
            .descriptorCount(4);
      sizes.get(1)
            .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(MAX_TEXTURE_SLOTS * (SAMPLER_SET_RING + 2));

      VkDescriptorPoolCreateInfo ci = VkDescriptorPoolCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
      ci.maxSets(SAMPLER_SET_RING + 4);
      ci.pPoolSizes(sizes);

      LongBuffer pPool = stack.mallocLong(1);
      int err = VK10.vkCreateDescriptorPool(this.ctx.device(), ci, null, pPool);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateDescriptorPool failed: " + vulkanError(err));
      }
      this.descriptorPool = pPool.get(0);
   }

   private void allocateSets(MemoryStack stack) {
      LongBuffer pLayouts = stack.mallocLong(1);
      pLayouts.put(0, this.set0Layout);

      VkDescriptorSetAllocateInfo ai = VkDescriptorSetAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
      ai.descriptorPool(this.descriptorPool);
      ai.pSetLayouts(pLayouts);

      LongBuffer pSet = stack.mallocLong(1);
      int err = VK10.vkAllocateDescriptorSets(this.ctx.device(), ai, pSet);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkAllocateDescriptorSets (ssbo) failed: " + vulkanError(err));
      }
      this.ssboSet = pSet.get(0);

      pLayouts.put(0, this.set1Layout);
      ai.pSetLayouts(pLayouts);

      // Каждый flush внутри одного кадра должен получить СВОЙ sampler-набор.
      // Ранее набор был один: vkFlush записывал vkCmdDraw, но submit происходил только
      // в endFrame, поэтому повторный vkUpdateDescriptorSets перезаписывал биндинги для
      // уже записанных команд. При включённом Blur флашей много (каждая панель приносит
      // новую blur-текстуру и переполняет 16 слотов), и весь HUD пропадал.
      for (int i = 0; i < SAMPLER_SET_RING; i++) {
         err = VK10.vkAllocateDescriptorSets(this.ctx.device(), ai, pSet);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkAllocateDescriptorSets (sampler) failed: " + vulkanError(err));
         }
         this.samplerSets[i] = pSet.get(0);
      }
      this.samplerSet = this.samplerSets[0];
   }

   public void updateSsboBinding(long ssboBuffer, long ssboSize) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkDescriptorBufferInfo.Buffer bufInfo = VkDescriptorBufferInfo.calloc(1, stack);
         bufInfo.get(0)
               .buffer(ssboBuffer)
               .offset(0)
               .range(ssboSize);

         VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
         write.get(0)
               .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
               .dstSet(this.ssboSet)
               .dstBinding(INSTANCE_SSBO_BINDING)
               .dstArrayElement(0)
               .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
               .descriptorCount(1)
               .pBufferInfo(bufInfo);

         VK10.vkUpdateDescriptorSets(this.ctx.device(), write, null);
      }
   }

   /** Сбрасывает ring sampler-наборов. Вызывать один раз за кадр, до записи команд. */
   public void beginFrame() {
      this.samplerSetCursor = 0;
   }

   /**
    * Заполняет очередной sampler-набор из ring и возвращает его handle.
    * Возвращённый набор нужно забиндить именно для текущего vkCmdDraw.
    */
   public long updateSamplerBindings(ShapeInstanceBatch batch, VulkanTextureManager textures) {
      long targetSet = this.samplerSets[Math.min(this.samplerSetCursor, SAMPLER_SET_RING - 1)];
      if (this.samplerSetCursor < SAMPLER_SET_RING) {
         this.samplerSetCursor++;
      }
      this.samplerSet = targetSet;

      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkDescriptorImageInfo.Buffer imageInfos = VkDescriptorImageInfo.calloc(MAX_TEXTURE_SLOTS, stack);
         long sharedSampler = textures.sharedSampler();
         long fallbackView = this.fallbackImageView(textures);

         int slotCount = batch.getSlotCount();
         for (int slot = 0; slot < MAX_TEXTURE_SLOTS; slot++) {
            long view;
            if (slot < slotCount) {
               VulkanTextureManager.VulkanTexture tex = textures.texture(batch.getSlotTexture(slot));
               view = tex != null ? tex.view : fallbackView;
            } else {
               view = fallbackView;
            }
            imageInfos.get(slot)
                  .sampler(sharedSampler)
                  .imageView(view)
                  .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
         }

         VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
         write.get(0)
               .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
               .dstSet(targetSet)
               .dstBinding(SAMPLERS_BINDING)
               .dstArrayElement(0)
               .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
               .descriptorCount(MAX_TEXTURE_SLOTS)
               .pImageInfo(imageInfos);

         VK10.vkUpdateDescriptorSets(this.ctx.device(), write, null);
      }

      return targetSet;
   }

   private long fallbackImageView(VulkanTextureManager textures) {
      if (this.fallbackTextureHandle == 0) {
         this.fallbackTextureHandle = textures.createTexture(1, 1, VulkanTextureManager.FORMAT_RGBA8,
               java.nio.ByteBuffer.allocate(4).put(0, (byte) -1).put(1, (byte) -1).put(2, (byte) -1).put(3, (byte) -1));
      }
      VulkanTextureManager.VulkanTexture tex = textures.texture(this.fallbackTextureHandle);
      return tex != null ? tex.view : 0;
   }

   private int fallbackTextureHandle;

   public void destroy() {
      if (!this.created) {
         return;
      }
      VkDevice device = this.ctx.device();
      if (this.descriptorPool != 0) {
         VK10.vkDestroyDescriptorPool(device, this.descriptorPool, null);
         this.descriptorPool = 0;
      }
      if (this.pipelineLayout != 0) {
         VK10.vkDestroyPipelineLayout(device, this.pipelineLayout, null);
         this.pipelineLayout = 0;
      }
      if (this.set1Layout != 0) {
         VK10.vkDestroyDescriptorSetLayout(device, this.set1Layout, null);
         this.set1Layout = 0;
      }
      if (this.set0Layout != 0) {
         VK10.vkDestroyDescriptorSetLayout(device, this.set0Layout, null);
         this.set0Layout = 0;
      }
      this.created = false;
      System.out.println("[Zero/Vulkan] Descriptors destroyed");
   }

   private static String vulkanError(int err) {
      return VulkanContext.vulkanError(err);
   }
}
