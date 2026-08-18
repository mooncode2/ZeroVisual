package ru.zero.util.render.backends.vk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import ru.zero.util.render.backends.gl.ResourceUtils;

@Environment(EnvType.CLIENT)
public final class VulkanWorldRenderer {
   private static final int VERTEX_STRIDE_FLOATS = 9;
   private static final int VERTEX_STRIDE_BYTES = VERTEX_STRIDE_FLOATS * 4;
   private static final int MAX_VERTICES = 65536;
   private static final long VERTEX_BUFFER_BYTES = (long) MAX_VERTICES * VERTEX_STRIDE_BYTES;
   private static final int PUSH_CONSTANT_SIZE = 64;

   private final VulkanContext ctx;
   private VulkanOffscreen offscreen;
   private long commandPool;
   private VkCommandBuffer commandBuffer;
   private long frameFence;
   private long vertexBuffer;
   private long vertexMemory;
   private ByteBuffer mappedVertices;
   private long samplerSetLayout;
   private long samplerSet;
   private long descriptorPool;
   private long pipelineLayout;
   private long vertModule;
   private long fragColorModule;
   private long fragTexturedModule;
   private long linesPipeline;
   private long triColorPipeline;
   private long triTexturedPipeline;
   private boolean created;

   private final FloatBuffer lineVerts = allocateFloatBuffer(MAX_VERTICES * VERTEX_STRIDE_FLOATS);
   private int lineVertCount;
   private final FloatBuffer triColorVerts = allocateFloatBuffer(MAX_VERTICES * VERTEX_STRIDE_FLOATS);
   private int triColorVertCount;
   private final FloatBuffer triTexVerts = allocateFloatBuffer(MAX_VERTICES * VERTEX_STRIDE_FLOATS);
   private int triTexVertCount;
   private int boundTexSlot = -1;
   private final Map<Integer, Integer> glTexToVkSlot = new HashMap<>();
   private final Map<Integer, Long> glTexToDescriptorSet = new HashMap<>();
   private final java.util.List<VulkanWorldRenderer.TexturedBatch> texturedBatches = new java.util.ArrayList<>();

   private record TexturedBatch(int glTexture, int vkHandle, int baseVertex, int vertexCount) {
   }

   private final float[] projection = new float[16];
   private int frameWidth;
   private int frameHeight;
   private boolean frameActive;

   public VulkanWorldRenderer(VulkanContext ctx) {
      this.ctx = ctx;
   }

   private static FloatBuffer allocateFloatBuffer(int capacity) {
      return ByteBuffer.allocateDirect(capacity * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
   }

   public boolean isCreated() {
      return this.created;
   }

   public java.nio.ByteBuffer readbackRGBA() {
      return this.offscreen != null ? this.offscreen.readbackRGBA() : null;
   }

   public void create(VulkanTextureManager textures) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         this.offscreen = new VulkanOffscreen(ctx);
         this.offscreen.ensure(8, 8);
         this.createSamplerDescriptor(stack, textures);
         this.createPipelineLayout(stack);
         this.compileShaders(stack);
         this.createPipelines(stack, this.offscreen.renderPass());
         this.createVertexBuffer(stack);
         this.createCommandPoolAndFence(stack);
      }
      this.created = true;
      System.out.println("[Zero/Vulkan] WorldRenderer ready (lines+tri-color+tri-textured pipelines)");
   }

   private void createSamplerDescriptor(MemoryStack stack, VulkanTextureManager textures) {
      VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
      bindings.get(0)
            .binding(0)
            .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(1)
            .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);

      VkDescriptorSetLayoutCreateInfo slci = VkDescriptorSetLayoutCreateInfo.calloc(stack);
      slci.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
      slci.pBindings(bindings);
      LongBuffer pLayout = stack.mallocLong(1);
      int err = VK10.vkCreateDescriptorSetLayout(this.ctx.device(), slci, null, pLayout);
      check(err, "vkCreateDescriptorSetLayout (world sampler)");
      this.samplerSetLayout = pLayout.get(0);

      VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(1, stack);
      sizes.get(0).type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(16);

      VkDescriptorPoolCreateInfo pci = VkDescriptorPoolCreateInfo.calloc(stack);
      pci.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
      pci.maxSets(16);
      pci.pPoolSizes(sizes);
      LongBuffer pPool = stack.mallocLong(1);
      err = VK10.vkCreateDescriptorPool(this.ctx.device(), pci, null, pPool);
      check(err, "vkCreateDescriptorPool (world)");
      this.descriptorPool = pPool.get(0);

      VkDescriptorSetAllocateInfo ai = VkDescriptorSetAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
      ai.descriptorPool(this.descriptorPool);
      LongBuffer pLayouts = stack.mallocLong(1);
      pLayouts.put(0, this.samplerSetLayout);
      ai.pSetLayouts(pLayouts);
      LongBuffer pSet = stack.mallocLong(1);
      err = VK10.vkAllocateDescriptorSets(this.ctx.device(), ai, pSet);
      check(err, "vkAllocateDescriptorSets (world sampler)");
      this.samplerSet = pSet.get(0);
   }

   private void createPipelineLayout(MemoryStack stack) {
      VkPushConstantRange.Buffer pcr = VkPushConstantRange.calloc(1, stack);
      pcr.get(0).stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT).offset(0).size(PUSH_CONSTANT_SIZE);

      LongBuffer pSetLayouts = stack.mallocLong(1);
      pSetLayouts.put(0, this.samplerSetLayout);

      VkPipelineLayoutCreateInfo ci = VkPipelineLayoutCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
      ci.pSetLayouts(pSetLayouts);
      ci.pPushConstantRanges(pcr);
      LongBuffer pLayout = stack.mallocLong(1);
      int err = VK10.vkCreatePipelineLayout(this.ctx.device(), ci, null, pLayout);
      check(err, "vkCreatePipelineLayout (world)");
      this.pipelineLayout = pLayout.get(0);
   }

   private void compileShaders(MemoryStack stack) {
      this.vertModule = this.compileModule(stack,
            ResourceUtils.readText("assets/zero/shaders/world_vk.vert"), "world_vk.vert");
      this.fragColorModule = this.compileModule(stack,
            ResourceUtils.readText("assets/zero/shaders/world_color_vk.frag"), "world_color_vk.frag");
      this.fragTexturedModule = this.compileModule(stack,
            ResourceUtils.readText("assets/zero/shaders/world_textured_vk.frag"), "world_textured_vk.frag");
   }

   private long compileModule(MemoryStack stack, String source, String name) {
      long compiler = org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize();
      long options = org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_initialize();
      org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_set_target_env(options,
            org.lwjgl.util.shaderc.Shaderc.shaderc_target_env_vulkan,
            org.lwjgl.util.shaderc.Shaderc.shaderc_env_version_vulkan_1_1);
      org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_set_optimization_level(options,
            org.lwjgl.util.shaderc.Shaderc.shaderc_optimization_level_performance);
      int kind = name.endsWith(".vert") ? org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_vertex_shader
            : org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_fragment_shader;
      long result = org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv(compiler, source, kind, name, "main",
            options);
      org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release(compiler);
      org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_release(options);
      if (result == 0L) {
         throw new IllegalStateException("shaderc returned null for " + name);
      }
      try {
         int status = org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status(result);
         if (status != org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success) {
            String err = org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message(result);
            throw new IllegalStateException("World shader compile failed " + name + ": " + err);
         }
         ByteBuffer spirv = org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes(result);
         VkShaderModuleCreateInfo ci = VkShaderModuleCreateInfo.calloc(stack);
         ci.sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
         ci.pCode(spirv);
         LongBuffer pModule = stack.mallocLong(1);
         int err = VK10.vkCreateShaderModule(this.ctx.device(), ci, null, pModule);
         check(err, "vkCreateShaderModule " + name);
         return pModule.get(0);
      } finally {
         org.lwjgl.util.shaderc.Shaderc.shaderc_result_release(result);
      }
   }

   private void createPipelines(MemoryStack stack, long renderPass) {
      this.linesPipeline = this.makePipeline(stack, renderPass, VK10.VK_PRIMITIVE_TOPOLOGY_LINE_LIST,
            this.vertModule, this.fragColorModule, false);
      this.triColorPipeline = this.makePipeline(stack, renderPass, VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
            this.vertModule, this.fragColorModule, false);
      this.triTexturedPipeline = this.makePipeline(stack, renderPass, VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
            this.vertModule, this.fragTexturedModule, true);
   }

   private long makePipeline(MemoryStack stack, long renderPass, int topology, long vert, long frag, boolean textured) {
      VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
      stages.get(0).sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT).module(vert).pName(stack.UTF8("main"));
      stages.get(1).sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT).module(frag).pName(stack.UTF8("main"));

      VkVertexInputBindingDescription.Buffer bindings = VkVertexInputBindingDescription.calloc(1, stack);
      bindings.get(0).binding(0).stride(VERTEX_STRIDE_BYTES).inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);

      VkVertexInputAttributeDescription.Buffer attrs = VkVertexInputAttributeDescription.calloc(3, stack);
      attrs.get(0).location(0).binding(0).format(VK10.VK_FORMAT_R32G32B32_SFLOAT).offset(0);
      attrs.get(1).location(1).binding(0).format(VK10.VK_FORMAT_R32G32B32A32_SFLOAT).offset(12);
      attrs.get(2).location(2).binding(0).format(VK10.VK_FORMAT_R32G32_SFLOAT).offset(28);

      VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
      vertexInput.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
      vertexInput.pVertexBindingDescriptions(bindings);
      vertexInput.pVertexAttributeDescriptions(attrs);

      VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
      inputAssembly.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
      inputAssembly.topology(topology);

      VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack);
      viewportState.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
      viewportState.viewportCount(1);
      viewportState.scissorCount(1);

      VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack);
      rasterizer.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
      rasterizer.polygonMode(VK10.VK_POLYGON_MODE_FILL);
      rasterizer.cullMode(VK10.VK_CULL_MODE_NONE);
      rasterizer.frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE);
      rasterizer.lineWidth(2.0F);

      VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack);
      multisampling.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
      multisampling.rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);

      VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
            VkPipelineColorBlendAttachmentState.calloc(1, stack);
      blendAttachments.get(0)
            .blendEnable(true)
            .srcColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE)
            .dstColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
            .colorBlendOp(VK10.VK_BLEND_OP_ADD)
            .srcAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE)
            .dstAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
            .alphaBlendOp(VK10.VK_BLEND_OP_ADD)
            .colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT | VK10.VK_COLOR_COMPONENT_G_BIT
                  | VK10.VK_COLOR_COMPONENT_B_BIT | VK10.VK_COLOR_COMPONENT_A_BIT);

      VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack);
      colorBlending.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
      colorBlending.pAttachments(blendAttachments);

      IntBuffer pDynamicStates = stack.mallocInt(2);
      pDynamicStates.put(0, VK10.VK_DYNAMIC_STATE_VIEWPORT);
      pDynamicStates.put(1, VK10.VK_DYNAMIC_STATE_SCISSOR);
      VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack);
      dynamicState.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO);
      dynamicState.pDynamicStates(pDynamicStates);

      VkGraphicsPipelineCreateInfo.Buffer pPipelines = VkGraphicsPipelineCreateInfo.calloc(1, stack);
      pPipelines.get(0)
            .sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
            .pStages(stages)
            .pVertexInputState(vertexInput)
            .pInputAssemblyState(inputAssembly)
            .pViewportState(viewportState)
            .pRasterizationState(rasterizer)
            .pMultisampleState(multisampling)
            .pColorBlendState(colorBlending)
            .pDynamicState(dynamicState)
            .layout(this.pipelineLayout)
            .renderPass(renderPass)
            .subpass(0);

      LongBuffer pPipeline = stack.mallocLong(1);
      int err = VK10.vkCreateGraphicsPipelines(this.ctx.device(), 0, pPipelines, null, pPipeline);
      check(err, "vkCreateGraphicsPipelines (world)");
      return pPipeline.get(0);
   }

   private void createVertexBuffer(MemoryStack stack) {
      VkBufferCreateInfo ci = VkBufferCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
      ci.size(VERTEX_BUFFER_BYTES);
      ci.usage(VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
      ci.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
      LongBuffer pBuffer = stack.mallocLong(1);
      int err = VK10.vkCreateBuffer(this.ctx.device(), ci, null, pBuffer);
      check(err, "vkCreateBuffer (world vertex)");
      this.vertexBuffer = pBuffer.get(0);

      VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
      VK10.vkGetBufferMemoryRequirements(this.ctx.device(), this.vertexBuffer, req);
      int type = this.ctx.findMemoryType((int) req.memoryTypeBits(),
            VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
      if (type < 0) {
         throw new IllegalStateException("No host-visible memory for world vertex buffer");
      }
      VkMemoryAllocateInfo ai = VkMemoryAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
      ai.allocationSize(req.size());
      ai.memoryTypeIndex(type);
      LongBuffer pMem = stack.mallocLong(1);
      err = VK10.vkAllocateMemory(this.ctx.device(), ai, null, pMem);
      check(err, "vkAllocateMemory (world vertex)");
      this.vertexMemory = pMem.get(0);
      err = VK10.vkBindBufferMemory(this.ctx.device(), this.vertexBuffer, this.vertexMemory, 0);
      check(err, "vkBindBufferMemory (world vertex)");

      PointerBuffer pData = stack.mallocPointer(1);
      err = VK10.vkMapMemory(this.ctx.device(), this.vertexMemory, 0, VERTEX_BUFFER_BYTES, 0, pData);
      check(err, "vkMapMemory (world vertex)");
      this.mappedVertices = MemoryUtil.memByteBuffer(pData.get(0), (int) VERTEX_BUFFER_BYTES);
   }

   private void createCommandPoolAndFence(MemoryStack stack) {
      org.lwjgl.vulkan.VkCommandPoolCreateInfo pci = org.lwjgl.vulkan.VkCommandPoolCreateInfo.calloc(stack);
      pci.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
      pci.queueFamilyIndex(this.ctx.graphicsQueueFamily());
      pci.flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
      LongBuffer pPool = stack.mallocLong(1);
      int err = VK10.vkCreateCommandPool(this.ctx.device(), pci, null, pPool);
      check(err, "vkCreateCommandPool (world)");
      this.commandPool = pPool.get(0);

      VkCommandBufferAllocateInfo ai = VkCommandBufferAllocateInfo.calloc(stack);
      ai.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
      ai.commandPool(this.commandPool);
      ai.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
      ai.commandBufferCount(1);
      PointerBuffer pCb = stack.mallocPointer(1);
      err = VK10.vkAllocateCommandBuffers(this.ctx.device(), ai, pCb);
      check(err, "vkAllocateCommandBuffers (world)");
      this.commandBuffer = new VkCommandBuffer(pCb.get(0), this.ctx.device());

      VkFenceCreateInfo fci = VkFenceCreateInfo.calloc(stack);
      fci.sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
      fci.flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);
      LongBuffer pFence = stack.mallocLong(1);
      err = VK10.vkCreateFence(this.ctx.device(), fci, null, pFence);
      check(err, "vkCreateFence (world)");
      this.frameFence = pFence.get(0);
   }

   public void beginFrame(int width, int height, float[] projMatrix16) {
      if (!this.created) {
         return;
      }
      this.frameWidth = width;
      this.frameHeight = height;
      System.arraycopy(projMatrix16, 0, this.projection, 0, 16);
      this.offscreen.ensure(width, height);
      this.lineVertCount = 0;
      this.triColorVertCount = 0;
      this.triTexVertCount = 0;
      this.boundTexSlot = -1;
      this.texturedBatches.clear();
      this.frameActive = true;
   }

    public int reserveLineVertices(int count) {
       if (this.lineVertCount + count > MAX_VERTICES) {
          int space = MAX_VERTICES - this.lineVertCount;
          if (space <= 0) {
             return this.lineVertCount;
          }
          count = space;
       }
       int offset = this.lineVertCount;
       this.lineVertCount += count;
       return offset;
    }

    public int reserveTriColorVertices(int count) {
       if (this.triColorVertCount + count > MAX_VERTICES) {
          int space = MAX_VERTICES - this.triColorVertCount;
          if (space <= 0) {
             return this.triColorVertCount;
          }
          count = space;
       }
       int offset = this.triColorVertCount;
       this.triColorVertCount += count;
       return offset;
    }

    public int reserveTriTexVertices(int count) {
       if (this.triTexVertCount + count > MAX_VERTICES) {
          int space = MAX_VERTICES - this.triTexVertCount;
          if (space <= 0) {
             return this.triTexVertCount;
          }
          count = space;
       }
       int offset = this.triTexVertCount;
       this.triTexVertCount += count;
       return offset;
    }

    public int reserveTexturedVertices(int count, int glTexture, int vkHandle) {
       if (this.triTexVertCount + count > MAX_VERTICES) {
          int space = MAX_VERTICES - this.triTexVertCount;
          if (space <= 0) {
             return this.triTexVertCount;
          }
          count = space;
       }
       int offset = this.triTexVertCount;
       this.triTexVertCount += count;
       this.texturedBatches.add(new VulkanWorldRenderer.TexturedBatch(glTexture, vkHandle, offset, count));
       return offset;
    }

   public FloatBuffer lineVerts() {
      return this.lineVerts;
   }

   public FloatBuffer triColorVerts() {
      return this.triColorVerts;
   }

   public FloatBuffer triTexVerts() {
      return this.triTexVerts;
   }

   public void setTextureBinding(int glTexture, int vkTextureHandle, VulkanTextureManager textures) {
      if (this.boundTexSlot == vkTextureHandle) {
         return;
      }
      this.boundTexSlot = vkTextureHandle;
      VulkanTextureManager.VulkanTexture tex = textures.texture(vkTextureHandle);
      if (tex == null) {
         return;
      }
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
         imageInfo.get(0)
               .sampler(textures.sharedSampler())
               .imageView(tex.view)
               .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
         VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
         write.get(0)
               .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
               .dstSet(this.samplerSet)
               .dstBinding(0)
               .dstArrayElement(0)
               .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
               .descriptorCount(1)
               .pImageInfo(imageInfo);
         VK10.vkUpdateDescriptorSets(this.ctx.device(), write, null);
      }
   }

   private long getOrCreateSamplerSet(int glTexture, int vkHandle, VulkanTextureManager textures) {
      Long cached = this.glTexToDescriptorSet.get(glTexture);
      if (cached != null) {
         return cached;
      }
      VulkanTextureManager.VulkanTexture tex = textures.texture(vkHandle);
      if (tex == null) {
         return this.samplerSet;
      }
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkDescriptorSetAllocateInfo ai = VkDescriptorSetAllocateInfo.calloc(stack);
         ai.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
         ai.descriptorPool(this.descriptorPool);
         LongBuffer pLayouts = stack.mallocLong(1);
         pLayouts.put(0, this.samplerSetLayout);
         ai.pSetLayouts(pLayouts);
         LongBuffer pSet = stack.mallocLong(1);
         int err = VK10.vkAllocateDescriptorSets(this.ctx.device(), ai, pSet);
         if (err != VK10.VK_SUCCESS) {
            return this.samplerSet;
         }
         long set = pSet.get(0);
         VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
         imageInfo.get(0)
               .sampler(textures.sharedSampler())
               .imageView(tex.view)
               .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
         VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
         write.get(0)
               .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
               .dstSet(set)
               .dstBinding(0)
               .dstArrayElement(0)
               .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
               .descriptorCount(1)
               .pImageInfo(imageInfo);
         VK10.vkUpdateDescriptorSets(this.ctx.device(), write, null);
         this.glTexToDescriptorSet.put(glTexture, set);
         return set;
      }
   }

   public long importGlTexture(int glTexture, int width, int height, VulkanTextureManager textures) {
      Integer existing = this.glTexToVkSlot.get(glTexture);
      if (existing != null) {
         return existing;
      }
      org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, glTexture);
      ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
      org.lwjgl.opengl.GL11.glGetTexImage(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA,
            org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, pixels);
      org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0);
      int vkHandle = textures.createTexture(width, height, VulkanTextureManager.FORMAT_RGBA8, pixels);
      this.glTexToVkSlot.put(glTexture, vkHandle);
      return vkHandle;
   }

   public void flush() {
      if (!this.frameActive) {
         return;
      }
      this.recordAndSubmit();
      this.frameActive = false;
      this.lineVertCount = 0;
      this.triColorVertCount = 0;
      this.triTexVertCount = 0;
      this.boundTexSlot = -1;
      this.texturedBatches.clear();
   }

   public void compositeToFramebuffer() {
      ByteBuffer pixels = this.offscreen.readbackRGBA();
      if (pixels == null) {
         return;
      }
      int w = this.frameWidth;
      int h = this.frameHeight;
      if (this.compositeGlTex == 0 || this.compositeGlW != w || this.compositeGlH != h) {
         if (this.compositeGlTex != 0) {
            org.lwjgl.opengl.GL11.glDeleteTextures(this.compositeGlTex);
         }
         this.compositeGlTex = org.lwjgl.opengl.GL11.glGenTextures();
         this.compositeGlW = w;
         this.compositeGlH = h;
         org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, this.compositeGlTex);
         org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
               org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
         org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
               org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
         org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
               org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
         org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
               org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
         org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA8,
               w, h, 0, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, pixels);
      } else {
         org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, this.compositeGlTex);
         org.lwjgl.opengl.GL11.glTexSubImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, 0, 0, w, h,
               org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, pixels);
      }
      org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0);
   }

   private void recordAndSubmit() {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         int err = VK10.vkResetFences(this.ctx.device(), this.frameFence);
         check(err, "vkResetFences (world)");
         err = VK10.vkResetCommandBuffer(this.commandBuffer, 0);
         check(err, "vkResetCommandBuffer (world)");

         VkCommandBufferBeginInfo bi = VkCommandBufferBeginInfo.calloc(stack);
         bi.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
         bi.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
         err = VK10.vkBeginCommandBuffer(this.commandBuffer, bi);
         check(err, "vkBeginCommandBuffer (world)");

         VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
         clearValues.get(0).color().float32(0, 0.0F).float32(1, 0.0F).float32(2, 0.0F).float32(3, 0.0F);

         VkRenderPassBeginInfo rpbi = VkRenderPassBeginInfo.calloc(stack);
         rpbi.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
         rpbi.renderPass(this.offscreen.renderPass());
         rpbi.framebuffer(this.offscreen.framebuffer());
         rpbi.renderArea().offset().set(0, 0);
         rpbi.renderArea().extent().set(this.frameWidth, this.frameHeight);
         rpbi.pClearValues(clearValues);
         VK10.vkCmdBeginRenderPass(this.commandBuffer, rpbi, VK10.VK_SUBPASS_CONTENTS_INLINE);

         VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
         viewport.get(0).x(0.0F).y((float) this.frameHeight).width(this.frameWidth).height(-this.frameHeight)
               .minDepth(0.0F).maxDepth(1.0F);
         VK10.vkCmdSetViewport(this.commandBuffer, 0, viewport);
         VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
         scissor.get(0).offset().set(0, 0);
         scissor.get(0).extent().set(this.frameWidth, this.frameHeight);
         VK10.vkCmdSetScissor(this.commandBuffer, 0, scissor);

         ByteBuffer push = stack.malloc(PUSH_CONSTANT_SIZE).order(ByteOrder.nativeOrder());
         push.asFloatBuffer().put(this.projection);
         VK10.vkCmdPushConstants(this.commandBuffer, this.pipelineLayout, VK10.VK_SHADER_STAGE_VERTEX_BIT, 0, push);

         LongBuffer pVertexBuffers = stack.mallocLong(1);
         pVertexBuffers.put(0, this.vertexBuffer);
         LongBuffer pOffsets = stack.mallocLong(1);
         pOffsets.put(0, 0L);
         VK10.vkCmdBindVertexBuffers(this.commandBuffer, 0, pVertexBuffers, pOffsets);

         this.uploadAndDraw(stack);
         VK10.vkCmdEndRenderPass(this.commandBuffer);
         err = VK10.vkEndCommandBuffer(this.commandBuffer);
         check(err, "vkEndCommandBuffer (world)");

         VkSubmitInfo si = VkSubmitInfo.calloc(stack);
         si.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);
         PointerBuffer pCb = stack.mallocPointer(1);
         pCb.put(0, this.commandBuffer);
         si.pCommandBuffers(pCb);
         err = VK10.vkQueueSubmit(this.ctx.graphicsQueue(), si, this.frameFence);
         check(err, "vkQueueSubmit (world)");
         err = VK10.vkWaitForFences(this.ctx.device(), this.frameFence, true, 5_000_000_000L);
         if (err != VK10.VK_SUCCESS) {
            System.err.println("[Zero/Vulkan] world vkWaitForFences failed: " + VulkanContext.vulkanError(err));
         }
      }
   }

   private void uploadAndDraw(MemoryStack stack) {
      int totalFloats = (this.lineVertCount + this.triColorVertCount + this.triTexVertCount) * VERTEX_STRIDE_FLOATS;
      if (totalFloats == 0) {
         return;
      }
      this.lineVerts.limit(this.lineVertCount * VERTEX_STRIDE_FLOATS).position(0);
      this.triColorVerts.limit(this.triColorVertCount * VERTEX_STRIDE_FLOATS).position(0);
      this.triTexVerts.limit(this.triTexVertCount * VERTEX_STRIDE_FLOATS).position(0);

      ByteBuffer mapped = this.mappedVertices;
      mapped.clear();
      long lineBytes = this.lineVertCount * (long) VERTEX_STRIDE_BYTES;
      long triColorBytes = this.triColorVertCount * (long) VERTEX_STRIDE_BYTES;
      long triTexBytes = this.triTexVertCount * (long) VERTEX_STRIDE_BYTES;

      if (lineBytes > 0) {
         FloatBuffer mappedF = mapped.asFloatBuffer();
         mappedF.put(this.lineVerts);
         mapped.position((int) lineBytes);
      }
      if (triColorBytes > 0) {
         FloatBuffer mappedF = mapped.asFloatBuffer();
         mappedF.put(this.triColorVerts);
         mapped.position((int) (lineBytes + triColorBytes));
      }
      if (triTexBytes > 0) {
         FloatBuffer mappedF = mapped.asFloatBuffer();
         mappedF.put(this.triTexVerts);
      }
      mapped.flip();

      long lineOffset = 0;
      long triColorOffset = lineBytes;
      long triTexOffset = lineBytes + triColorBytes;

      if (this.lineVertCount > 0) {
         VK10.vkCmdBindPipeline(this.commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.linesPipeline);
         LongBuffer pOffsets = stack.mallocLong(1);
         pOffsets.put(0, lineOffset);
         LongBuffer pVertexBuffers = stack.mallocLong(1);
         pVertexBuffers.put(0, this.vertexBuffer);
         VK10.vkCmdBindVertexBuffers(this.commandBuffer, 0, pVertexBuffers, pOffsets);
         VK10.vkCmdDraw(this.commandBuffer, this.lineVertCount, 1, 0, 0);
      }
      if (this.triColorVertCount > 0) {
         VK10.vkCmdBindPipeline(this.commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.triColorPipeline);
         LongBuffer pOffsets = stack.mallocLong(1);
         pOffsets.put(0, triColorOffset);
         LongBuffer pVertexBuffers = stack.mallocLong(1);
         pVertexBuffers.put(0, this.vertexBuffer);
         VK10.vkCmdBindVertexBuffers(this.commandBuffer, 0, pVertexBuffers, pOffsets);
         VK10.vkCmdDraw(this.commandBuffer, this.triColorVertCount, 1, 0, 0);
      }
      if (this.triTexVertCount > 0) {
         VK10.vkCmdBindPipeline(this.commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.triTexturedPipeline);
         LongBuffer pOffsets = stack.mallocLong(1);
         pOffsets.put(0, triTexOffset);
         LongBuffer pVertexBuffers = stack.mallocLong(1);
         pVertexBuffers.put(0, this.vertexBuffer);
         VK10.vkCmdBindVertexBuffers(this.commandBuffer, 0, pVertexBuffers, pOffsets);
         VulkanTextureManager textures = ru.zero.Zero.getVulkanBackend() != null
               ? ru.zero.Zero.getVulkanBackend().textures() : null;
         if (textures != null && !this.texturedBatches.isEmpty()) {
            for (VulkanWorldRenderer.TexturedBatch batch : this.texturedBatches) {
               if (batch.vertexCount() <= 0) {
                  continue;
               }
               long set = this.getOrCreateSamplerSet(batch.glTexture(), batch.vkHandle(), textures);
               LongBuffer pSets = stack.mallocLong(1);
               pSets.put(0, set);
               VK10.vkCmdBindDescriptorSets(this.commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                     this.pipelineLayout, 0, pSets, null);
               VK10.vkCmdDraw(this.commandBuffer, batch.vertexCount(), 1, batch.baseVertex(), 0);
            }
         } else {
            LongBuffer pSets = stack.mallocLong(1);
            pSets.put(0, this.samplerSet);
            VK10.vkCmdBindDescriptorSets(this.commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                  this.pipelineLayout, 0, pSets, null);
            VK10.vkCmdDraw(this.commandBuffer, this.triTexVertCount, 1, 0, 0);
         }
      }
   }

   public int compositeGlTexture() {
      return this.compositeGlTex;
   }

   public int compositeWidth() {
      return this.compositeGlW;
   }

   public int compositeHeight() {
      return this.compositeGlH;
   }

   private int compositeGlTex;
   private int compositeGlW;
   private int compositeGlH;

   public void destroy() {
      if (!this.created) {
         return;
      }
      if (this.compositeGlTex != 0) {
         org.lwjgl.opengl.GL11.glDeleteTextures(this.compositeGlTex);
         this.compositeGlTex = 0;
      }
      if (this.commandPool != 0) {
         if (this.commandBuffer != null) {
            VK10.vkFreeCommandBuffers(this.ctx.device(), this.commandPool, this.commandBuffer);
         }
         VK10.vkDestroyCommandPool(this.ctx.device(), this.commandPool, null);
         this.commandPool = 0;
      }
      if (this.frameFence != 0) {
         VK10.vkDestroyFence(this.ctx.device(), this.frameFence, null);
         this.frameFence = 0;
      }
      if (this.mappedVertices != null) {
         VK10.vkUnmapMemory(this.ctx.device(), this.vertexMemory);
         this.mappedVertices = null;
      }
      if (this.vertexBuffer != 0) {
         VK10.vkDestroyBuffer(this.ctx.device(), this.vertexBuffer, null);
         this.vertexBuffer = 0;
      }
      if (this.vertexMemory != 0) {
         VK10.vkFreeMemory(this.ctx.device(), this.vertexMemory, null);
         this.vertexMemory = 0;
      }
      for (long p : new long[] {this.linesPipeline, this.triColorPipeline, this.triTexturedPipeline}) {
         if (p != 0) {
            VK10.vkDestroyPipeline(this.ctx.device(), p, null);
         }
      }
      this.linesPipeline = this.triColorPipeline = this.triTexturedPipeline = 0;
      for (long m : new long[] {this.vertModule, this.fragColorModule, this.fragTexturedModule}) {
         if (m != 0) {
            VK10.vkDestroyShaderModule(this.ctx.device(), m, null);
         }
      }
      this.vertModule = this.fragColorModule = this.fragTexturedModule = 0;
      if (this.pipelineLayout != 0) {
         VK10.vkDestroyPipelineLayout(this.ctx.device(), this.pipelineLayout, null);
         this.pipelineLayout = 0;
      }
      if (this.descriptorPool != 0) {
         VK10.vkDestroyDescriptorPool(this.ctx.device(), this.descriptorPool, null);
         this.descriptorPool = 0;
      }
      if (this.samplerSetLayout != 0) {
         VK10.vkDestroyDescriptorSetLayout(this.ctx.device(), this.samplerSetLayout, null);
         this.samplerSetLayout = 0;
      }
      if (this.offscreen != null) {
         this.offscreen.destroy();
         this.offscreen = null;
      }
      this.created = false;
      System.out.println("[Zero/Vulkan] WorldRenderer destroyed");
   }

   private static void check(int err, String what) {
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException(what + " failed: " + VulkanContext.vulkanError(err));
      }
   }
}
