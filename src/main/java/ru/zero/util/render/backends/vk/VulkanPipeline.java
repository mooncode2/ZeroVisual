package ru.zero.util.render.backends.vk;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

@Environment(EnvType.CLIENT)
public final class VulkanPipeline {
   private final VulkanContext ctx;
   private long vertModule;
   private long fragModule;
   private long pipeline;
   private boolean created;

   public VulkanPipeline(VulkanContext ctx) {
      this.ctx = ctx;
   }

   public long pipeline() {
      return this.pipeline;
   }

   public void create(long pipelineLayout, long renderPass, ByteBuffer vertSpv, ByteBuffer fragSpv) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         this.vertModule = this.createShaderModule(stack, vertSpv);
         this.fragModule = this.createShaderModule(stack, fragSpv);

         VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
         stages.get(0)
               .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
               .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
               .module(this.vertModule)
               .pName(stack.UTF8("main"));
         stages.get(1)
               .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
               .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
               .module(this.fragModule)
               .pName(stack.UTF8("main"));

         VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
         vertexInput.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);

         VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
         inputAssembly.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
         inputAssembly.topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
         inputAssembly.primitiveRestartEnable(false);

         VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack);
         viewportState.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
         viewportState.viewportCount(1);
         viewportState.scissorCount(1);

         VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack);
         rasterizer.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
         rasterizer.depthClampEnable(false);
         rasterizer.rasterizerDiscardEnable(false);
         rasterizer.polygonMode(VK10.VK_POLYGON_MODE_FILL);
         rasterizer.cullMode(VK10.VK_CULL_MODE_NONE);
         rasterizer.frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE);
         rasterizer.lineWidth(1.0F);

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

         java.nio.IntBuffer pDynamicStates = stack.mallocInt(2);
         pDynamicStates.put(0, VK10.VK_DYNAMIC_STATE_VIEWPORT);
         pDynamicStates.put(1, VK10.VK_DYNAMIC_STATE_SCISSOR);

         VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack);
         dynamicState.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO);
         dynamicState.pDynamicStates(pDynamicStates);

         org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.Buffer pPipelines =
               org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.calloc(1, stack);
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
               .layout(pipelineLayout)
               .renderPass(renderPass)
               .subpass(0);

         LongBuffer pPipeline = stack.mallocLong(1);
         int err = VK10.vkCreateGraphicsPipelines(this.ctx.device(), 0, pPipelines, null, pPipeline);
         if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkCreateGraphicsPipelines failed: " + vulkanError(err));
         }
         this.pipeline = pPipeline.get(0);
      }
      this.created = true;
      System.out.println("[Zero/Vulkan] Graphics pipeline created (handle=" + this.pipeline + ")");
   }

   private long createShaderModule(MemoryStack stack, ByteBuffer spirv) {
      VkShaderModuleCreateInfo ci = VkShaderModuleCreateInfo.calloc(stack);
      ci.sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
      ci.pCode(spirv);

      LongBuffer pModule = stack.mallocLong(1);
      int err = VK10.vkCreateShaderModule(this.ctx.device(), ci, null, pModule);
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException("vkCreateShaderModule failed: " + vulkanError(err));
      }
      return pModule.get(0);
   }

   public void destroy() {
      if (!this.created) {
         return;
      }
      VkDevice device = this.ctx.device();
      if (this.pipeline != 0) {
         VK10.vkDestroyPipeline(device, this.pipeline, null);
         this.pipeline = 0;
      }
      if (this.fragModule != 0) {
         VK10.vkDestroyShaderModule(device, this.fragModule, null);
         this.fragModule = 0;
      }
      if (this.vertModule != 0) {
         VK10.vkDestroyShaderModule(device, this.vertModule, null);
         this.vertModule = 0;
      }
      this.created = false;
      System.out.println("[Zero/Vulkan] Pipeline destroyed");
   }

   private static String vulkanError(int err) {
      return VulkanContext.vulkanError(err);
   }
}
