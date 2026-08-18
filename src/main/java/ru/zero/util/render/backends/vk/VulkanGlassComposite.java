package ru.zero.util.render.backends.vk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent3D;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;

@Environment(EnvType.CLIENT)
public final class VulkanGlassComposite {
   private static final int MAX_BLUR_RADII = 5;
   private static final long UBO_SAMPLER_INFO = 16L;
   private static final long UBO_CUSTOM = 256L;
   private static final long UBO_WIDGET = 12304L;
   private static final long UBO_BG = 16L;
   private static final long UBO_BLUR_CONFIG = 3104L;

   private final VulkanContext ctx;
   private final VulkanShaderSystem shaders;
   private final VulkanTextureManager textures;
   private long samplerInfoMem;
   private long customMem;
   private long widgetMem;
   private long bgMem;
   private long blurConfigXMem;
   private long blurConfigYMem;
   private long samplerInfoBuffer;
   private long customBuffer;
   private long widgetBuffer;
   private long bgBuffer;
   private long blurConfigXBuffer;
   private long blurConfigYBuffer;
   private long vertexBuffer;
   private long vertexMemory;
   private long linearSampler;
   private long blurSetLayout;
   private long glassSetLayout;
   private long pipelineLayout;
   private long blurPipelineLayout;
   private long blurRenderPass;
   private long glassRenderPass;
   private long blurPipeline;
   private long glassPipeline;
   private long descriptorPool;
   private long blurSet;
   private long glassSet;
   private long vertModule;
   private long blurFragModule;
   private long glassFragModule;
   private boolean available;
   private int fbWidth;
   private int fbHeight;
   private long blurTempImage;
   private long blurTempMemory;
   private long blurTempView;
   private long blurTempFb;
   private long backdropImage;
   private long backdropMemory;
   private long backdropView;
   private int backdropW;
   private int backdropH;
   private final Map<Integer, long[]> blurredByRadius = new HashMap<>();

   public VulkanGlassComposite(VulkanContext ctx, VulkanShaderSystem shaders, VulkanTextureManager textures) {
      this.ctx = ctx;
      this.shaders = shaders;
      this.textures = textures;
   }

   public boolean isAvailable() {
      return this.available;
   }

   public boolean init() {
      if (this.available) {
         return true;
      }
      try {
         this.compileShaders();
         this.createLinearSampler();
         this.createUBOBuffers();
         this.createVertexBuffer();
         this.createRenderPasses();
         this.createDescriptorLayouts();
         this.createPipelines();
         this.createDescriptorPoolAndSets();
         this.available = true;
         System.out.println("[Zero/Vulkan] VulkanGlassComposite ready (glass + blur pipelines)");
      } catch (Throwable t) {
         this.available = false;
         this.destroyPartial();
         System.err.println("[Zero/Vulkan] VulkanGlassComposite init failed: " + t.getMessage());
         t.printStackTrace();
      }
      return this.available;
   }

   private void compileShaders() {
      ByteBuffer vert = this.shaders.compileVertex(VulkanGlassShaders.BLIT_VERT, "glass_blit.vert");
      ByteBuffer blurFrag = this.shaders.compileFragment(VulkanGlassShaders.BLUR_FRAG, "glass_blur.frag");
      ByteBuffer glassFrag = this.shaders.compileFragment(VulkanGlassShaders.GLASS_FRAG, "liquid_glass_gui.fsh");
      this.vertModule = this.createShaderModule(vert);
      this.blurFragModule = this.createShaderModule(blurFrag);
      this.glassFragModule = this.createShaderModule(glassFrag);
      MemoryUtil.memFree(vert);
      MemoryUtil.memFree(blurFrag);
      MemoryUtil.memFree(glassFrag);
   }

   private long createShaderModule(ByteBuffer spirv) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkShaderModuleCreateInfo ci = VkShaderModuleCreateInfo.calloc(stack);
         ci.sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
         ci.pCode(spirv);
         LongBuffer pMod = stack.mallocLong(1);
         int err = VK10.vkCreateShaderModule(this.ctx.device(), ci, null, pMod);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkCreateShaderModule failed: " + VulkanContext.vulkanError(err));
         }
         return pMod.get(0);
      }
   }

   private void createLinearSampler() {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkSamplerCreateInfo ci = VkSamplerCreateInfo.calloc(stack);
         ci.sType(VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);
         ci.magFilter(VK10.VK_FILTER_LINEAR);
         ci.minFilter(VK10.VK_FILTER_LINEAR);
         ci.addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
         ci.addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
         ci.addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
         LongBuffer pS = stack.mallocLong(1);
         int err = VK10.vkCreateSampler(this.ctx.device(), ci, null, pS);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vkCreateSampler failed: " + VulkanContext.vulkanError(err));
         }
         this.linearSampler = pS.get(0);
      }
   }

   private void createUBOBuffers() {
      this.samplerInfoBuffer = this.createUBO(UBO_SAMPLER_INFO);
      this.samplerInfoMem = this.bindUBO(this.samplerInfoBuffer);
      this.customBuffer = this.createUBO(UBO_CUSTOM);
      this.customMem = this.bindUBO(this.customBuffer);
      this.widgetBuffer = this.createUBO(UBO_WIDGET);
      this.widgetMem = this.bindUBO(this.widgetBuffer);
      this.bgBuffer = this.createUBO(UBO_BG);
      this.bgMem = this.bindUBO(this.bgBuffer);
      this.blurConfigXBuffer = this.createUBO(UBO_BLUR_CONFIG);
      this.blurConfigXMem = this.bindUBO(this.blurConfigXBuffer);
      this.blurConfigYBuffer = this.createUBO(UBO_BLUR_CONFIG);
      this.blurConfigYMem = this.bindUBO(this.blurConfigYBuffer);
   }

   private long createUBO(long size) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkBufferCreateInfo ci = VkBufferCreateInfo.calloc(stack);
         ci.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
         ci.size(size);
         ci.usage(VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
         ci.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
         LongBuffer pB = stack.mallocLong(1);
         int err = VK10.vkCreateBuffer(this.ctx.device(), ci, null, pB);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vkCreateBuffer failed: " + VulkanContext.vulkanError(err));
         }
         return pB.get(0);
      }
   }

   private long bindUBO(long buffer) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
         VK10.vkGetBufferMemoryRequirements(this.ctx.device(), buffer, req);
         int type = this.ctx.findMemoryType((int) req.memoryTypeBits(),
               VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
         if (type < 0) {
            throw new IllegalStateException("glass UBO: no host-visible memory type");
         }
         VkMemoryAllocateInfo ai = VkMemoryAllocateInfo.calloc(stack);
         ai.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
         ai.allocationSize(req.size());
         ai.memoryTypeIndex(type);
         LongBuffer pM = stack.mallocLong(1);
         int err = VK10.vkAllocateMemory(this.ctx.device(), ai, null, pM);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vkAllocateMemory failed: " + VulkanContext.vulkanError(err));
         }
         long mem = pM.get(0);
         err = VK10.vkBindBufferMemory(this.ctx.device(), buffer, mem, 0);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vkBindBufferMemory failed: " + VulkanContext.vulkanError(err));
         }
         return mem;
      }
   }

   private void uploadUBO(long mem, ByteBuffer data, long size) {
      PointerBuffer pData = MemoryStack.stackMallocPointer(1);
      int err = VK10.vkMapMemory(this.ctx.device(), mem, 0, size, 0, pData);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("glass vkMapMemory failed: " + VulkanContext.vulkanError(err));
      }
      ByteBuffer mapped = MemoryUtil.memByteBuffer(pData.get(0), (int) size);
      mapped.clear();
      int pos = data.position();
      data.limit(pos + (int) Math.min(data.remaining(), size));
      mapped.put(data);
      data.position(pos);
      VK10.vkUnmapMemory(this.ctx.device(), mem);
   }

   private void createVertexBuffer() {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkBufferCreateInfo ci = VkBufferCreateInfo.calloc(stack);
         ci.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
         ci.size(24L);
         ci.usage(VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
         ci.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
         LongBuffer pB = stack.mallocLong(1);
         int err = VK10.vkCreateBuffer(this.ctx.device(), ci, null, pB);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vertex vkCreateBuffer failed: " + VulkanContext.vulkanError(err));
         }
         this.vertexBuffer = pB.get(0);
         VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
         VK10.vkGetBufferMemoryRequirements(this.ctx.device(), this.vertexBuffer, req);
         int type = this.ctx.findMemoryType((int) req.memoryTypeBits(),
               VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
         if (type < 0) {
            throw new IllegalStateException("glass vertex: no host-visible memory");
         }
         VkMemoryAllocateInfo ai = VkMemoryAllocateInfo.calloc(stack);
         ai.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
         ai.allocationSize(req.size());
         ai.memoryTypeIndex(type);
         LongBuffer pM = stack.mallocLong(1);
         err = VK10.vkAllocateMemory(this.ctx.device(), ai, null, pM);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vertex vkAllocateMemory failed: " + VulkanContext.vulkanError(err));
         }
         this.vertexMemory = pM.get(0);
         err = VK10.vkBindBufferMemory(this.ctx.device(), this.vertexBuffer, this.vertexMemory, 0);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vertex vkBindBufferMemory failed: " + VulkanContext.vulkanError(err));
         }
         PointerBuffer pData = stack.mallocPointer(1);
         VK10.vkMapMemory(this.ctx.device(), this.vertexMemory, 0, 24L, 0, pData);
         ByteBuffer mapped = MemoryUtil.memByteBuffer(pData.get(0), 24);
         mapped.putFloat(0, -1.0F).putFloat(4, -1.0F).putFloat(8, 3.0F).putFloat(12, -1.0F)
               .putFloat(16, -1.0F).putFloat(20, 3.0F);
         VK10.vkUnmapMemory(this.ctx.device(), this.vertexMemory);
      }
   }

   private long createRenderPass(int finalLayout) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkAttachmentDescription.Buffer atts = VkAttachmentDescription.calloc(1, stack);
         atts.get(0)
               .format(VulkanOffscreen.COLOR_FORMAT)
               .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
               .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
               .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
               .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
               .finalLayout(finalLayout);
         VkAttachmentReference.Buffer refs = VkAttachmentReference.calloc(1, stack);
         refs.get(0).attachment(0).layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
         VkSubpassDescription.Buffer sub = VkSubpassDescription.calloc(1, stack);
         sub.get(0).pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS).colorAttachmentCount(1)
               .pColorAttachments(refs);
         VkRenderPassCreateInfo ci = VkRenderPassCreateInfo.calloc(stack);
         ci.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
         ci.pAttachments(atts);
         ci.pSubpasses(sub);
         LongBuffer pRp = stack.mallocLong(1);
         int err = VK10.vkCreateRenderPass(this.ctx.device(), ci, null, pRp);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vkCreateRenderPass failed: " + VulkanContext.vulkanError(err));
         }
         return pRp.get(0);
      }
   }

   private void createRenderPasses() {
      this.blurRenderPass = this.createRenderPass(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
      this.glassRenderPass = this.createRenderPass(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
   }

   private void createDescriptorLayouts() {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         // Blur set: 0 sampler + 1 SamplerInfo UBO + 2 Config UBO
         VkDescriptorSetLayoutBinding.Buffer blurBinds = VkDescriptorSetLayoutBinding.calloc(3, stack);
         blurBinds.get(0).binding(0).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
               .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
         blurBinds.get(1).binding(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
               .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
         blurBinds.get(2).binding(2).descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
               .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
         this.blurSetLayout = this.createSetLayout(stack, blurBinds);

         // Glass set: 0-5 samplers + 6 SamplerInfo + 7 Custom + 8 Widget + 9 Bg
         VkDescriptorSetLayoutBinding.Buffer glassBinds = VkDescriptorSetLayoutBinding.calloc(10, stack);
         for (int i = 0; i < 6; i++) {
            glassBinds.get(i).binding(i).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                  .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
         }
         glassBinds.get(6).binding(6).descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1)
               .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
         glassBinds.get(7).binding(7).descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1)
               .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
         glassBinds.get(8).binding(8).descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1)
               .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
         glassBinds.get(9).binding(9).descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1)
               .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
         this.glassSetLayout = this.createSetLayout(stack, glassBinds);

         VkPipelineLayoutCreateInfo pli = VkPipelineLayoutCreateInfo.calloc(stack);
         pli.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
         LongBuffer pSets = stack.mallocLong(1);
         pSets.put(0, this.glassSetLayout);
         pli.pSetLayouts(pSets);
         LongBuffer pPl = stack.mallocLong(1);
         int err = VK10.vkCreatePipelineLayout(this.ctx.device(), pli, null, pPl);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vkCreatePipelineLayout failed: " + VulkanContext.vulkanError(err));
         }
         this.pipelineLayout = pPl.get(0);

         VkPipelineLayoutCreateInfo bpli = VkPipelineLayoutCreateInfo.calloc(stack);
         bpli.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
         LongBuffer pBSets = stack.mallocLong(1);
         pBSets.put(0, this.blurSetLayout);
         bpli.pSetLayouts(pBSets);
         LongBuffer pBPl = stack.mallocLong(1);
         err = VK10.vkCreatePipelineLayout(this.ctx.device(), bpli, null, pBPl);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("blur vkCreatePipelineLayout failed: " + VulkanContext.vulkanError(err));
         }
         this.blurPipelineLayout = pBPl.get(0);
      }
   }

   private long createSetLayout(MemoryStack stack, VkDescriptorSetLayoutBinding.Buffer binds) {
      VkDescriptorSetLayoutCreateInfo ci = VkDescriptorSetLayoutCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
      ci.pBindings(binds);
      LongBuffer pL = stack.mallocLong(1);
      int err = VK10.vkCreateDescriptorSetLayout(this.ctx.device(), ci, null, pL);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateDescriptorSetLayout failed: " + VulkanContext.vulkanError(err));
      }
      return pL.get(0);
   }

   private void createPipelines() {
      this.blurPipeline = this.createPipeline(this.blurPipelineLayout, this.blurRenderPass, this.vertModule,
            this.blurFragModule);
      this.glassPipeline = this.createPipeline(this.pipelineLayout, this.glassRenderPass, this.vertModule,
            this.glassFragModule);
   }

   private long createPipeline(long layout, long renderPass, long vert, long frag) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
         stages.get(0).sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
               .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT).module(vert).pName(stack.UTF8("main"));
         stages.get(1).sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
               .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT).module(frag).pName(stack.UTF8("main"));

         VkVertexInputBindingDescription.Buffer vbind = VkVertexInputBindingDescription.calloc(1, stack);
         vbind.get(0).binding(0).stride(8).inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
         VkVertexInputAttributeDescription.Buffer vattr = VkVertexInputAttributeDescription.calloc(1, stack);
         vattr.get(0).location(0).binding(0).format(VK10.VK_FORMAT_R32G32_SFLOAT).offset(0);
         VkPipelineVertexInputStateCreateInfo vis = VkPipelineVertexInputStateCreateInfo.calloc(stack);
         vis.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
         vis.pVertexBindingDescriptions(vbind);
         vis.pVertexAttributeDescriptions(vattr);

         VkPipelineInputAssemblyStateCreateInfo ias = VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
         ias.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
         ias.topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

         VkPipelineViewportStateCreateInfo vs = VkPipelineViewportStateCreateInfo.calloc(stack);
         vs.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
         vs.viewportCount(1);
         vs.scissorCount(1);

         VkPipelineRasterizationStateCreateInfo rs = VkPipelineRasterizationStateCreateInfo.calloc(stack);
         rs.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
         rs.polygonMode(VK10.VK_POLYGON_MODE_FILL);
         rs.cullMode(VK10.VK_CULL_MODE_NONE);
         rs.frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE);
         rs.lineWidth(1.0F);

         VkPipelineColorBlendAttachmentState.Buffer att = VkPipelineColorBlendAttachmentState.calloc(1, stack);
         att.get(0).blendEnable(false).colorWriteMask(0xF);
         VkPipelineColorBlendStateCreateInfo cbs = VkPipelineColorBlendStateCreateInfo.calloc(stack);
         cbs.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
         cbs.pAttachments(att);

         int[] dynStates = new int[]{VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR};
         VkPipelineRasterizationStateCreateInfo unused = rs;
         org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo ds =
               org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo.calloc(stack);
         ds.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO);
         ds.pDynamicStates(stack.ints(dynStates));

         VkGraphicsPipelineCreateInfo ci = VkGraphicsPipelineCreateInfo.calloc(stack);
         ci.sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
         ci.pStages(stages);
         ci.pVertexInputState(vis);
         ci.pInputAssemblyState(ias);
         ci.pViewportState(vs);
         ci.pRasterizationState(unused);
         ci.pColorBlendState(cbs);
         ci.pDynamicState(ds);
         ci.layout(layout);
         ci.renderPass(renderPass);

         VkGraphicsPipelineCreateInfo.Buffer cis = VkGraphicsPipelineCreateInfo.calloc(1, stack);
         cis.get(0).set(ci);
         LongBuffer pP = stack.mallocLong(1);
         int err = VK10.vkCreateGraphicsPipelines(this.ctx.device(), 0L, cis, null, pP);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkCreateGraphicsPipelines failed: " + VulkanContext.vulkanError(err));
         }
         return pP.get(0);
      }
   }

   private void createDescriptorPoolAndSets() {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(2, stack);
         sizes.get(0).type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(16);
         sizes.get(1).type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(16);
         VkDescriptorPoolCreateInfo pci = VkDescriptorPoolCreateInfo.calloc(stack);
         pci.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
         pci.pPoolSizes(sizes);
         pci.maxSets(8);
         pci.flags(VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT);
         LongBuffer pPool = stack.mallocLong(1);
         int err = VK10.vkCreateDescriptorPool(this.ctx.device(), pci, null, pPool);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vkCreateDescriptorPool failed: " + VulkanContext.vulkanError(err));
         }
         this.descriptorPool = pPool.get(0);

         this.blurSet = this.allocSet(stack, this.blurSetLayout);
         this.glassSet = this.allocSet(stack, this.glassSetLayout);
      }
   }

   private long allocSet(MemoryStack stack, long layout) {
      VkDescriptorSetAllocateInfo ai = VkDescriptorSetAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
      ai.descriptorPool(this.descriptorPool);
      LongBuffer pLayouts = stack.mallocLong(1);
      pLayouts.put(0, layout);
      ai.pSetLayouts(pLayouts);
      LongBuffer pSet = stack.mallocLong(1);
      int err = VK10.vkAllocateDescriptorSets(this.ctx.device(), ai, pSet);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkAllocateDescriptorSets failed: " + VulkanContext.vulkanError(err));
      }
      return pSet.get(0);
   }

   // Uploads the std140 UBO payloads built by AetherialGlassUniforms.
   public void uploadUniforms(ByteBuffer samplerInfo, ByteBuffer custom, ByteBuffer widget, ByteBuffer bg) {
      this.uploadUBO(this.samplerInfoMem, samplerInfo, UBO_SAMPLER_INFO);
      this.uploadUBO(this.customMem, custom, UBO_CUSTOM);
      this.uploadUBO(this.widgetMem, widget, UBO_WIDGET);
      this.uploadUBO(this.bgMem, bg, UBO_BG);
   }

   public void uploadBlurConfig(boolean xAxis, ByteBuffer config) {
      this.uploadUBO(xAxis ? this.blurConfigXMem : this.blurConfigYMem, config, UBO_BLUR_CONFIG);
   }

   // Build the std140 blur Config UBO for a radius + direction (matches the GL
   // AetherialGlassPrecomputeRuntime.uploadBlur layout: vec4(dx,dy,radius,0) + 193
   // floats each 16-byte aligned). Computes the gaussian kernel inline.
   private void buildAndUploadBlurConfig(boolean xAxis, int radius) {
      int r = Math.max(0, Math.min(radius, 192));
      float[] weights = gaussianKernel(r);
      ByteBuffer cfg = ByteBuffer.allocateDirect((int) UBO_BLUR_CONFIG).order(ByteOrder.nativeOrder());
      float dx = xAxis ? 1.0F : 0.0F;
      float dy = xAxis ? 0.0F : 1.0F;
      cfg.putFloat(0, dx).putFloat(4, dy).putFloat(8, (float) r).putFloat(12, 0.0F);
      int off = 16;
      for (int i = 0; i <= 192; ++i) {
         float w = i <= r ? weights[i] : 0.0F;
         cfg.putFloat(off, w);
         off += 16;
      }
      cfg.position(0).limit((int) UBO_BLUR_CONFIG);
      this.uploadUBO(xAxis ? this.blurConfigXMem : this.blurConfigYMem, cfg, UBO_BLUR_CONFIG);
   }

   private static float[] gaussianKernel(int radius) {
      if (radius == 0) {
         return new float[]{1.0F};
      }
      float sigma = (float) radius / 2.0F;
      float[] kernel = new float[radius + 1];
      float sum = 0.0F;
      for (int i = 0; i <= radius; ++i) {
         float w = (float) Math.exp(-0.5F * (float) i * (float) i / (sigma * sigma));
         kernel[i] = w;
         sum += i == 0 ? w : 2.0F * w;
      }
      for (int i = 0; i <= radius; ++i) {
         kernel[i] /= sum;
      }
      return kernel;
   }

   // Write the glass descriptor set with the backdrop + blurred views and the four UBO
   // buffers. Must be called before the glass draw so the set actually binds resources.
   public void writeGlassDescriptors(long backdropView, Map<Integer, long[]> blurredViews,
         List<Integer> activeRadii) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         long[] views = new long[6];
         views[0] = backdropView;
         for (int i = 0; i < 5; i++) {
            if (i < activeRadii.size()) {
               long[] arr = blurredViews.get(activeRadii.get(i));
               views[i + 1] = arr != null ? arr[0] : backdropView;
            } else {
               views[i + 1] = backdropView;
            }
         }
         org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer imageInfos =
               org.lwjgl.vulkan.VkDescriptorImageInfo.calloc(6, stack);
         for (int i = 0; i < 6; i++) {
            imageInfos.get(i).sampler(this.linearSampler).imageView(views[i])
                  .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
         }
         org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer bufInfos =
               org.lwjgl.vulkan.VkDescriptorBufferInfo.calloc(4, stack);
         bufInfos.get(0).buffer(this.samplerInfoBuffer).offset(0).range(UBO_SAMPLER_INFO);
         bufInfos.get(1).buffer(this.customBuffer).offset(0).range(UBO_CUSTOM);
         bufInfos.get(2).buffer(this.widgetBuffer).offset(0).range(UBO_WIDGET);
         bufInfos.get(3).buffer(this.bgBuffer).offset(0).range(UBO_BG);

         org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer writes =
               org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(10, stack);
         for (int i = 0; i < 6; i++) {
            writes.get(i).sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET).dstSet(this.glassSet)
                  .dstBinding(i).dstArrayElement(0).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                  .descriptorCount(1).pImageInfo(imageInfos.slice(i, 1));
         }
         int[] uboBindings = new int[]{6, 7, 8, 9};
         for (int i = 0; i < 4; i++) {
            writes.get(6 + i).sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET).dstSet(this.glassSet)
                  .dstBinding(uboBindings[i]).dstArrayElement(0).descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                  .descriptorCount(1).pBufferInfo(bufInfos.slice(i, 1));
         }
         VK10.vkUpdateDescriptorSets(this.ctx.device(), writes, null);
      }
   }

   public void writeBlurDescriptors(long srcView, boolean xAxis) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer imageInfos =
               org.lwjgl.vulkan.VkDescriptorImageInfo.calloc(1, stack);
         imageInfos.get(0).sampler(this.linearSampler).imageView(srcView)
               .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
         org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer bufInfos =
               org.lwjgl.vulkan.VkDescriptorBufferInfo.calloc(2, stack);
         bufInfos.get(0).buffer(this.samplerInfoBuffer).offset(0).range(UBO_SAMPLER_INFO);
         bufInfos.get(1).buffer(xAxis ? this.blurConfigXBuffer : this.blurConfigYBuffer).offset(0)
               .range(UBO_BLUR_CONFIG);
         org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer writes =
               org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(3, stack);
         writes.get(0).sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET).dstSet(this.blurSet)
               .dstBinding(0).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1)
               .pImageInfo(imageInfos);
         writes.get(1).sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET).dstSet(this.blurSet)
               .dstBinding(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1)
               .pBufferInfo(bufInfos.slice(0, 1));
         writes.get(2).sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET).dstSet(this.blurSet)
               .dstBinding(2).descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1)
               .pBufferInfo(bufInfos.slice(1, 1));
         VK10.vkUpdateDescriptorSets(this.ctx.device(), writes, null);
      }
   }

   // Composite the glass effect: backdrop (captured MC FB) + per-radius blurred backdrops
   // → output. The output image is rendered to with the glass pipeline and left in
   // SHADER_READ_ONLY for GL sampling via the interop shared texture.
    public void composite(int fbWidth, int fbHeight, long backdropImage, long backdropView,
          long outputImage, long outputView, long outputFb, List<Integer> radii,
          Map<Integer, int[]> bboxByRadius, Map<Integer, long[]> blurredViews) {
      if (!this.available) {
         return;
      }
      this.fbWidth = fbWidth;
      this.fbHeight = fbHeight;
      try {
         this.ensureBlurTemp(fbWidth, fbHeight);
         List<Integer> activeRadii = new ArrayList<>();
         for (int r : radii) {
            if (r > 0) {
               activeRadii.add(r);
            }
         }
         // Precompute blurred backdrop per radius into blurredViews.
         for (int radius : activeRadii) {
            long[] views = this.ensureBlurredTarget(fbWidth, fbHeight, radius);
            blurredViews.put(radius, views);
            this.runBlurPass(radius, true, backdropView, this.blurTempView, this.blurTempFb, bboxByRadius);
            this.runBlurPass(radius, false, this.blurTempView, views[0], views[1], bboxByRadius);
         }
         this.writeGlassDescriptors(backdropView, blurredViews, activeRadii);
         this.runGlassComposite(backdropView, blurredViews, activeRadii, outputImage, outputView, outputFb);
      } catch (Throwable t) {
         System.err.println("[Zero/Vulkan] glass composite failed: " + t.getMessage());
         t.printStackTrace();
         this.available = false;
      }
   }

   private void runBlurPass(int radius, boolean xAxis, long srcView, long dstView, long dstFb,
          Map<Integer, int[]> bboxByRadius) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkCommandBuffer cb = this.beginOneTime(stack);
         VkRenderPassBeginInfo rpbi = VkRenderPassBeginInfo.calloc(stack);
         rpbi.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
         rpbi.renderPass(this.blurRenderPass);
         rpbi.framebuffer(dstFb);
         rpbi.renderArea().offset().set(0, 0);
         rpbi.renderArea().extent().set(this.fbWidth, this.fbHeight);
         org.lwjgl.vulkan.VkClearValue.Buffer clears =
               org.lwjgl.vulkan.VkClearValue.calloc(1, stack);
         rpbi.pClearValues(clears);
         VK10.vkCmdBeginRenderPass(cb, rpbi, VK10.VK_SUBPASS_CONTENTS_INLINE);
         VK10.vkCmdBindPipeline(cb, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.blurPipeline);
         this.buildAndUploadBlurConfig(xAxis, radius);
         this.writeBlurDescriptors(srcView, xAxis);
         org.lwjgl.vulkan.VkViewport.Buffer vp = org.lwjgl.vulkan.VkViewport.calloc(1, stack);
         vp.get(0).x(0.0F).y(0.0F).width(this.fbWidth).height(this.fbHeight).minDepth(0.0F).maxDepth(1.0F);
         VK10.vkCmdSetViewport(cb, 0, vp);
         org.lwjgl.vulkan.VkRect2D.Buffer sc = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
         sc.get(0).offset().set(0, 0);
         sc.get(0).extent().set(this.fbWidth, this.fbHeight);
         VK10.vkCmdSetScissor(cb, 0, sc);
         LongBuffer pSets = stack.mallocLong(1);
         pSets.put(0, this.blurSet);
         VK10.vkCmdBindDescriptorSets(cb, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.blurPipelineLayout, 0, pSets,
               null);
         LongBuffer pVb = stack.mallocLong(1);
         pVb.put(0, this.vertexBuffer);
         VK10.vkCmdBindVertexBuffers(cb, 0, pVb, stack.mallocLong(1).put(0, 0L).flip());
         VK10.vkCmdDraw(cb, 3, 1, 0, 0);
         VK10.vkCmdEndRenderPass(cb);
         this.endAndSubmitWait(stack, cb);
      }
   }

   private void runGlassComposite(long backdropView, Map<Integer, long[]> blurredViews,
         List<Integer> activeRadii, long outputImage, long outputView, long outputFb) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         this.transitionImage(stack, outputImage, VK10.VK_IMAGE_LAYOUT_UNDEFINED,
               VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, 0, VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
               VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
         VkCommandBuffer cb = this.beginOneTime(stack);
         VkRenderPassBeginInfo rpbi = VkRenderPassBeginInfo.calloc(stack);
         rpbi.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
         rpbi.renderPass(this.glassRenderPass);
         rpbi.framebuffer(outputFb);
         rpbi.renderArea().offset().set(0, 0);
         rpbi.renderArea().extent().set(this.fbWidth, this.fbHeight);
         org.lwjgl.vulkan.VkClearValue.Buffer clears = org.lwjgl.vulkan.VkClearValue.calloc(1, stack);
         rpbi.pClearValues(clears);
         VK10.vkCmdBeginRenderPass(cb, rpbi, VK10.VK_SUBPASS_CONTENTS_INLINE);
         VK10.vkCmdBindPipeline(cb, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.glassPipeline);
         org.lwjgl.vulkan.VkViewport.Buffer vp = org.lwjgl.vulkan.VkViewport.calloc(1, stack);
         vp.get(0).x(0.0F).y(0.0F).width(this.fbWidth).height(this.fbHeight).minDepth(0.0F).maxDepth(1.0F);
         VK10.vkCmdSetViewport(cb, 0, vp);
         org.lwjgl.vulkan.VkRect2D.Buffer sc = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
         sc.get(0).offset().set(0, 0);
         sc.get(0).extent().set(this.fbWidth, this.fbHeight);
         VK10.vkCmdSetScissor(cb, 0, sc);
         LongBuffer pSets = stack.mallocLong(1);
         pSets.put(0, this.glassSet);
         VK10.vkCmdBindDescriptorSets(cb, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout, 0, pSets, null);
         LongBuffer pVb = stack.mallocLong(1);
         pVb.put(0, this.vertexBuffer);
         VK10.vkCmdBindVertexBuffers(cb, 0, pVb, stack.mallocLong(1).put(0, 0L).flip());
         VK10.vkCmdDraw(cb, 3, 1, 0, 0);
         VK10.vkCmdEndRenderPass(cb);
         this.endAndSubmitWait(stack, cb);
      }
   }

   private long[] ensureBlurredTarget(int w, int h, int radius) {
      long[] existing = this.blurredByRadius.get(radius);
      if (existing != null) {
         return existing;
      }
      long image = this.createColorImage(w, h, VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
            | VK10.VK_IMAGE_USAGE_SAMPLED_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT);
      long mem = this.bindImageDeviceLocal(image);
      long view = this.createView(image);
      long fb = this.createFramebuffer(this.blurRenderPass, view, w, h);
      long[] arr = new long[]{view, fb, image, mem};
      this.blurredByRadius.put(radius, arr);
      return arr;
   }

   private void ensureBlurTemp(int w, int h) {
      if (this.blurTempImage != 0 && this.fbWidth == w && this.fbHeight == h) {
         return;
      }
      this.destroyBlurTemp();
      this.blurTempImage = this.createColorImage(w, h, VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
            | VK10.VK_IMAGE_USAGE_SAMPLED_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT);
      this.blurTempMemory = this.bindImageDeviceLocal(this.blurTempImage);
      this.blurTempView = this.createView(this.blurTempImage);
      this.blurTempFb = this.createFramebuffer(this.blurRenderPass, this.blurTempView, w, h);
   }

   // Backdrop = the Minecraft framebuffer content captured into a Vulkan image so the
   // glass shader can sample it as Sampler0. Capture is via GL readback (glReadPixels)
   // → staging buffer → vkCmdCopyBufferToImage. CPU roundtrip, but correct and simple;
   // a reverse-GL-interop zero-copy path can replace it later.
   public long backdropView() {
      return this.backdropView;
   }

   public void ensureBackdrop(int w, int h) {
      if (this.backdropImage != 0 && this.backdropW == w && this.backdropH == h) {
         return;
      }
      this.destroyBackdrop();
      this.backdropImage = this.createColorImage(w, h, VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
            | VK10.VK_IMAGE_USAGE_SAMPLED_BIT);
      this.backdropMemory = this.bindImageDeviceLocal(this.backdropImage);
      this.backdropView = this.createView(this.backdropImage);
      this.backdropW = w;
      this.backdropH = h;
   }

   public void uploadBackdrop(ByteBuffer rgba, int w, int h) {
      if (this.backdropImage == 0 || rgba == null) {
         return;
      }
      try (MemoryStack stack = MemoryStack.stackPush()) {
         this.transitionImage(stack, this.backdropImage, VK10.VK_IMAGE_LAYOUT_UNDEFINED,
               VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 0, VK10.VK_ACCESS_TRANSFER_WRITE_BIT,
               VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT);
         long staging = this.createStagingBuffer(stack, (long) w * h * 4);
         long stagingMem = this.bindStagingHostVisible(stack, staging, (long) w * h * 4);
         ByteBuffer mapped = this.mapStaging(stack, stagingMem, (long) w * h * 4);
         mapped.put(rgba);
         mapped.flip();
         VK10.vkUnmapMemory(this.ctx.device(), stagingMem);

         VkCommandBuffer cb = this.beginOneTime(stack);
         org.lwjgl.vulkan.VkBufferImageCopy.Buffer region =
               org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
         region.get(0).bufferOffset(0).bufferRowLength(w).bufferImageHeight(h)
               .imageSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0)
               .baseArrayLayer(0).layerCount(1);
         region.get(0).imageOffset().set(0, 0, 0);
         region.get(0).imageExtent().set(w, h, 1);
         VK10.vkCmdCopyBufferToImage(cb, staging, this.backdropImage,
               VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
         this.endAndSubmitWait(stack, cb);
         VK10.vkDestroyBuffer(this.ctx.device(), staging, null);
         VK10.vkFreeMemory(this.ctx.device(), stagingMem, null);
         this.transitionImage(stack, this.backdropImage, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
               VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK10.VK_ACCESS_TRANSFER_WRITE_BIT,
               VK10.VK_ACCESS_SHADER_READ_BIT, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
               VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
      } catch (Throwable t) {
         System.err.println("[Zero/Vulkan] uploadBackdrop failed: " + t.getMessage());
      }
   }

   private long createStagingBuffer(MemoryStack stack, long size) {
      VkBufferCreateInfo ci = VkBufferCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
      ci.size(size);
      ci.usage(VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
      ci.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
      LongBuffer pB = stack.mallocLong(1);
      int err = VK10.vkCreateBuffer(this.ctx.device(), ci, null, pB);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("backdrop vkCreateBuffer failed: " + VulkanContext.vulkanError(err));
      }
      return pB.get(0);
   }

   private long bindStagingHostVisible(MemoryStack stack, long buffer, long size) {
      VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
      VK10.vkGetBufferMemoryRequirements(this.ctx.device(), buffer, req);
      int type = this.ctx.findMemoryType((int) req.memoryTypeBits(),
            VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
      if (type < 0) {
         throw new IllegalStateException("backdrop: no host-visible memory");
      }
      VkMemoryAllocateInfo ai = VkMemoryAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
      ai.allocationSize(req.size());
      ai.memoryTypeIndex(type);
      LongBuffer pM = stack.mallocLong(1);
      int err = VK10.vkAllocateMemory(this.ctx.device(), ai, null, pM);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("backdrop vkAllocateMemory failed: " + VulkanContext.vulkanError(err));
      }
      long mem = pM.get(0);
      err = VK10.vkBindBufferMemory(this.ctx.device(), buffer, mem, 0);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("backdrop vkBindBufferMemory failed: " + VulkanContext.vulkanError(err));
      }
      return mem;
   }

   private ByteBuffer mapStaging(MemoryStack stack, long memory, long size) {
      PointerBuffer pData = stack.mallocPointer(1);
      int err = VK10.vkMapMemory(this.ctx.device(), memory, 0, size, 0, pData);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("backdrop vkMapMemory failed: " + VulkanContext.vulkanError(err));
      }
      return MemoryUtil.memByteBuffer(pData.get(0), (int) size);
   }

   private void destroyBackdrop() {
      VkDevice device = this.ctx.device();
      if (this.backdropView != 0) {
         VK10.vkDestroyImageView(device, this.backdropView, null);
      }
      if (this.backdropImage != 0) {
         VK10.vkDestroyImage(device, this.backdropImage, null);
      }
      if (this.backdropMemory != 0) {
         VK10.vkFreeMemory(device, this.backdropMemory, null);
      }
      this.backdropView = 0;
      this.backdropImage = 0;
      this.backdropMemory = 0;
      this.backdropW = 0;
      this.backdropH = 0;
   }

   private long createColorImage(int w, int h, int usage) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkExtent3D extent = VkExtent3D.calloc(stack);
         extent.width(w).height(h).depth(1);
         VkImageCreateInfo ci = VkImageCreateInfo.calloc(stack);
         ci.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
         ci.imageType(VK10.VK_IMAGE_TYPE_2D);
         ci.format(VulkanOffscreen.COLOR_FORMAT);
         ci.extent(extent);
         ci.mipLevels(1);
         ci.arrayLayers(1);
         ci.samples(VK10.VK_SAMPLE_COUNT_1_BIT);
         ci.tiling(VK10.VK_IMAGE_TILING_OPTIMAL);
         ci.usage(usage);
         ci.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
         ci.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
         LongBuffer pI = stack.mallocLong(1);
         int err = VK10.vkCreateImage(this.ctx.device(), ci, null, pI);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vkCreateImage failed: " + VulkanContext.vulkanError(err));
         }
         return pI.get(0);
      }
   }

   private long bindImageDeviceLocal(long image) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
         VK10.vkGetImageMemoryRequirements(this.ctx.device(), image, req);
         int type = this.ctx.findMemoryType((int) req.memoryTypeBits(), VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
         if (type < 0) {
            throw new IllegalStateException("glass image: no device-local memory");
         }
         VkMemoryAllocateInfo ai = VkMemoryAllocateInfo.calloc(stack);
         ai.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
         ai.allocationSize(req.size());
         ai.memoryTypeIndex(type);
         LongBuffer pM = stack.mallocLong(1);
         int err = VK10.vkAllocateMemory(this.ctx.device(), ai, null, pM);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass image vkAllocateMemory failed: " + VulkanContext.vulkanError(err));
         }
         long mem = pM.get(0);
         err = VK10.vkBindImageMemory(this.ctx.device(), image, mem, 0);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vkBindImageMemory failed: " + VulkanContext.vulkanError(err));
         }
         return mem;
      }
   }

   private long createView(long image) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack);
         range.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1).baseArrayLayer(0)
               .layerCount(1);
         VkImageViewCreateInfo ci = VkImageViewCreateInfo.calloc(stack);
         ci.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
         ci.image(image);
         ci.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);
         ci.format(VulkanOffscreen.COLOR_FORMAT);
         ci.subresourceRange(range);
         LongBuffer pV = stack.mallocLong(1);
         int err = VK10.vkCreateImageView(this.ctx.device(), ci, null, pV);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vkCreateImageView failed: " + VulkanContext.vulkanError(err));
         }
         return pV.get(0);
      }
   }

   private long createFramebuffer(long renderPass, long view, int w, int h) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         LongBuffer pAtt = stack.mallocLong(1);
         pAtt.put(0, view);
         org.lwjgl.vulkan.VkFramebufferCreateInfo ci = org.lwjgl.vulkan.VkFramebufferCreateInfo.calloc(stack);
         ci.sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
         ci.renderPass(renderPass);
         ci.attachmentCount(1);
         ci.pAttachments(pAtt);
         ci.width(w);
         ci.height(h);
         ci.layers(1);
         LongBuffer pF = stack.mallocLong(1);
         int err = VK10.vkCreateFramebuffer(this.ctx.device(), ci, null, pF);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("glass vkCreateFramebuffer failed: " + VulkanContext.vulkanError(err));
         }
         return pF.get(0);
      }
   }

   private void transitionImage(MemoryStack stack, long image, int oldLayout, int newLayout, int srcAccess,
         int dstAccess, int srcStage, int dstStage) {
      if (oldLayout == newLayout) {
         return;
      }
      VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack);
      range.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
      VkImageMemoryBarrier.Buffer bar = VkImageMemoryBarrier.calloc(1, stack);
      bar.get(0).sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER).oldLayout(oldLayout).newLayout(newLayout)
            .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
            .image(image).subresourceRange(range).srcAccessMask(srcAccess).dstAccessMask(dstAccess);
      VkCommandBuffer cb = this.beginOneTime(stack);
      VK10.vkCmdPipelineBarrier(cb, srcStage, dstStage, 0, null, null, bar);
      this.endAndSubmitWait(stack, cb);
   }

   private VkCommandBuffer beginOneTime(MemoryStack stack) {
      org.lwjgl.vulkan.VkCommandBufferAllocateInfo ai = org.lwjgl.vulkan.VkCommandBufferAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
      ai.commandPool(this.ctx.commandPoolHandle());
      ai.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
      ai.commandBufferCount(1);
      PointerBuffer pCb = stack.mallocPointer(1);
      int err = VK10.vkAllocateCommandBuffers(this.ctx.device(), ai, pCb);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("glass vkAllocateCommandBuffers failed: " + VulkanContext.vulkanError(err));
      }
      VkCommandBuffer cb = new VkCommandBuffer(pCb.get(0), this.ctx.device());
      VkCommandBufferBeginInfo bi = VkCommandBufferBeginInfo.calloc(stack);
      bi.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
      bi.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
      err = VK10.vkBeginCommandBuffer(cb, bi);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("glass vkBeginCommandBuffer failed: " + VulkanContext.vulkanError(err));
      }
      return cb;
   }

   private void endAndSubmitWait(MemoryStack stack, VkCommandBuffer cb) {
      int err = VK10.vkEndCommandBuffer(cb);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("glass vkEndCommandBuffer failed: " + VulkanContext.vulkanError(err));
      }
      VkSubmitInfo si = VkSubmitInfo.calloc(stack);
      si.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);
      PointerBuffer pCb = stack.mallocPointer(1);
      pCb.put(0, cb);
      si.pCommandBuffers(pCb);
      LongBuffer pFence = stack.mallocLong(1);
      err = VK10.vkCreateFence(this.ctx.device(),
            org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack).sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO), null,
            pFence);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("glass vkCreateFence failed: " + VulkanContext.vulkanError(err));
      }
      long fence = pFence.get(0);
      err = VK10.vkQueueSubmit(this.ctx.graphicsQueue(), si, fence);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("glass vkQueueSubmit failed: " + VulkanContext.vulkanError(err));
      }
      err = VK10.vkWaitForFences(this.ctx.device(), fence, true, 1_000_000_000L);
      if (err != VK10.VK_SUCCESS) {
         System.err.println("[Zero/Vulkan] glass vkWaitForFences failed: " + VulkanContext.vulkanError(err));
      }
      VK10.vkDestroyFence(this.ctx.device(), fence, null);
      VK10.vkFreeCommandBuffers(this.ctx.device(), this.ctx.commandPoolHandle(), cb);
   }

   public void destroy() {
      this.destroyPartial();
   }

   private void destroyBlurTemp() {
      VkDevice device = this.ctx.device();
      if (this.blurTempFb != 0) {
         VK10.vkDestroyFramebuffer(device, this.blurTempFb, null);
      }
      if (this.blurTempView != 0) {
         VK10.vkDestroyImageView(device, this.blurTempView, null);
      }
      if (this.blurTempImage != 0) {
         VK10.vkDestroyImage(device, this.blurTempImage, null);
      }
      if (this.blurTempMemory != 0) {
         VK10.vkFreeMemory(device, this.blurTempMemory, null);
      }
      this.blurTempFb = 0;
      this.blurTempView = 0;
      this.blurTempImage = 0;
      this.blurTempMemory = 0;
   }

    private void destroyPartial() {
       VkDevice device = this.ctx.device();
       if (device == null) {
          return;
       }
       this.destroyBlurTemp();
       this.destroyBackdrop();
      for (long[] arr : this.blurredByRadius.values()) {
         if (arr[1] != 0) {
            VK10.vkDestroyFramebuffer(device, arr[1], null);
         }
         if (arr[0] != 0) {
            VK10.vkDestroyImageView(device, arr[0], null);
         }
         if (arr[2] != 0) {
            VK10.vkDestroyImage(device, arr[2], null);
         }
         if (arr[3] != 0) {
            VK10.vkFreeMemory(device, arr[3], null);
         }
      }
      this.blurredByRadius.clear();
      if (this.descriptorPool != 0) {
         VK10.vkDestroyDescriptorPool(device, this.descriptorPool, null);
      }
      if (this.glassPipeline != 0) {
         VK10.vkDestroyPipeline(device, this.glassPipeline, null);
      }
      if (this.blurPipeline != 0) {
         VK10.vkDestroyPipeline(device, this.blurPipeline, null);
      }
      if (this.pipelineLayout != 0) {
         VK10.vkDestroyPipelineLayout(device, this.pipelineLayout, null);
      }
      if (this.blurPipelineLayout != 0) {
         VK10.vkDestroyPipelineLayout(device, this.blurPipelineLayout, null);
      }
      if (this.glassSetLayout != 0) {
         VK10.vkDestroyDescriptorSetLayout(device, this.glassSetLayout, null);
      }
      if (this.blurSetLayout != 0) {
         VK10.vkDestroyDescriptorSetLayout(device, this.blurSetLayout, null);
      }
      if (this.glassRenderPass != 0) {
         VK10.vkDestroyRenderPass(device, this.glassRenderPass, null);
      }
      if (this.blurRenderPass != 0) {
         VK10.vkDestroyRenderPass(device, this.blurRenderPass, null);
      }
      if (this.glassFragModule != 0) {
         VK10.vkDestroyShaderModule(device, this.glassFragModule, null);
      }
      if (this.blurFragModule != 0) {
         VK10.vkDestroyShaderModule(device, this.blurFragModule, null);
      }
      if (this.vertModule != 0) {
         VK10.vkDestroyShaderModule(device, this.vertModule, null);
      }
      if (this.linearSampler != 0) {
         VK10.vkDestroySampler(device, this.linearSampler, null);
      }
      long[] bufs = new long[]{this.vertexBuffer, this.samplerInfoBuffer, this.customBuffer, this.widgetBuffer,
            this.bgBuffer, this.blurConfigXBuffer, this.blurConfigYBuffer};
      long[] mems = new long[]{this.vertexMemory, this.samplerInfoMem, this.customMem, this.widgetMem, this.bgMem,
            this.blurConfigXMem, this.blurConfigYMem};
      for (long b : bufs) {
         if (b != 0) {
            VK10.vkDestroyBuffer(device, b, null);
         }
      }
      for (long m : mems) {
         if (m != 0) {
            VK10.vkFreeMemory(device, m, null);
         }
      }
      this.descriptorPool = 0;
      this.glassPipeline = 0;
      this.blurPipeline = 0;
      this.pipelineLayout = 0;
      this.blurPipelineLayout = 0;
      this.glassSetLayout = 0;
      this.blurSetLayout = 0;
      this.glassRenderPass = 0;
      this.blurRenderPass = 0;
      this.glassFragModule = 0;
      this.blurFragModule = 0;
      this.vertModule = 0;
      this.linearSampler = 0;
      this.vertexBuffer = 0;
      this.vertexMemory = 0;
      this.samplerInfoBuffer = 0;
      this.samplerInfoMem = 0;
      this.customBuffer = 0;
      this.customMem = 0;
      this.widgetBuffer = 0;
      this.widgetMem = 0;
      this.bgBuffer = 0;
      this.bgMem = 0;
      this.blurConfigXBuffer = 0;
      this.blurConfigXMem = 0;
      this.blurConfigYBuffer = 0;
      this.blurConfigYMem = 0;
      this.available = false;
   }
}
