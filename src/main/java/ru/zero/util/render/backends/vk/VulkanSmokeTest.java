package ru.zero.util.render.backends.vk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkViewport;
import ru.zero.util.render.backends.ShapeInstanceBatch;
import ru.zero.util.render.backends.gl.ResourceUtils;

/**
 * Standalone smoke-test Stage 2: полный Vulkan draw path без Minecraft.
 * Создаёт pipeline, рисует красный квадрат в offscreen, readback, проверяет пиксели.
 * Запуск: {@code gradlew runSmokeTest}.
 */
@Environment(EnvType.CLIENT)
public final class VulkanSmokeTest {
   private static final int W = 128;
   private static final int H = 128;
   private static final long INSTANCE_BUFFER_BYTES = 4096L * 144L;

   private VulkanSmokeTest() {
   }

   public static void main(String[] args) {
      System.out.println("[smoke] Stage 2: full Vulkan draw path test...");
      VulkanContext ctx = new VulkanContext();
      VulkanOffscreen offscreen = null;
      VulkanShaderSystem shaders = null;
      VulkanTextureManager textures = null;
      VulkanDescriptors descriptors = null;
      VulkanPipeline pipeline = null;
      VulkanInstanceBuffer instanceBuffer = null;
      VulkanFrameSync frameSync = null;
      try {
         ctx.create();
         frameSync = new VulkanFrameSync(ctx);
         frameSync.create();
         offscreen = new VulkanOffscreen(ctx);
         offscreen.ensure(W, H);
         shaders = new VulkanShaderSystem();
         shaders.create();
         ByteBuffer vertSpv = shaders.compileVertex(ResourceUtils.readText("assets/zero/shaders/shape_vk.vert"),
               "shape_vk.vert");
         ByteBuffer fragSpv = shaders.compileFragment(ResourceUtils.readText("assets/zero/shaders/shape_vk.frag"),
               "shape_vk.frag");
         System.out.println("[smoke] SPIR-V compiled: vert=" + vertSpv.remaining() + "B, frag=" + fragSpv.remaining()
               + "B");
         textures = new VulkanTextureManager(ctx);
         textures.create();
         descriptors = new VulkanDescriptors(ctx);
         descriptors.create();
         instanceBuffer = new VulkanInstanceBuffer(ctx);
         instanceBuffer.create(INSTANCE_BUFFER_BYTES);
         descriptors.updateSsboBinding(instanceBuffer.buffer(), INSTANCE_BUFFER_BYTES);
         pipeline = new VulkanPipeline(ctx);
         pipeline.create(descriptors.pipelineLayout(), offscreen.renderPass(), vertSpv, fragSpv);
         System.out.println("[smoke] Pipeline created. Drawing red rect 32,32..96,96...");

         ShapeInstanceBatch batch = new ShapeInstanceBatch();
         batch.beginFrame(W, H);
         batch.enqueueRect(32.0F, 32.0F, 64.0F, 64.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0xFFFF0000, null);

         ByteBuffer src = batch.prepareFlushBuffer();
         ByteBuffer dst = instanceBuffer.mapped();
         dst.clear();
         dst.put(src);
         dst.flip();
          descriptors.updateSamplerBindings(batch, textures);

         recordDraw(ctx, frameSync, offscreen, descriptors, pipeline, instanceBuffer, batch.getInstanceCount());

         ByteBuffer pixels = offscreen.readbackRGBA();
         if (pixels == null) {
            throw new IllegalStateException("readback returned null");
         }
         int center = ((H / 2) * W + (W / 2)) * 4;
         int r = pixels.get(center) & 0xFF;
         int g = pixels.get(center + 1) & 0xFF;
         int b = pixels.get(center + 2) & 0xFF;
         int a = pixels.get(center + 3) & 0xFF;
         System.out.println("[smoke] center pixel RGBA = (" + r + "," + g + "," + b + "," + a + ")");
         int corner = 0;
         int cr = pixels.get(corner) & 0xFF;
         int cg = pixels.get(corner + 1) & 0xFF;
         int cb = pixels.get(corner + 2) & 0xFF;
         int ca = pixels.get(corner + 3) & 0xFF;
         System.out.println("[smoke] corner pixel RGBA = (" + cr + "," + cg + "," + cb + "," + ca + ")");

         if (r > 200 && g < 60 && b < 60 && a > 200 && ca < 10) {
            System.out.println("[smoke] SUCCESS — Vulkan 2D draw path works (red rect rendered, corner clear).");
         } else {
            System.out.println("[smoke] FAILURE — 2D pixel check failed (expected red center, clear corner).");
            System.exit(3);
         }

         System.out.println("[smoke] Stage 3: world render path test (3D pipeline + projection + vertex consumer)...");
         VulkanWorldRenderer worldRenderer = new VulkanWorldRenderer(ctx);
         worldRenderer.create(textures);
         float[] identityProj = new float[16];
         new org.joml.Matrix4f().identity().get(identityProj);
         worldRenderer.beginFrame(W, H, identityProj);
         VulkanVertexConsumer worldConsumer = new VulkanVertexConsumer(worldRenderer, VulkanVertexConsumer.QUADS);
         worldConsumer.vertex(-0.5F, -0.5F, 0.0F).color(0, 255, 0, 255);
         worldConsumer.vertex(0.5F, -0.5F, 0.0F).color(0, 255, 0, 255);
         worldConsumer.vertex(0.5F, 0.5F, 0.0F).color(0, 255, 0, 255);
         worldConsumer.vertex(-0.5F, 0.5F, 0.0F).color(0, 255, 0, 255);
         worldConsumer.next();
         worldConsumer.flushTo(textures, 0, 0, 0);
         worldRenderer.flush();
         ByteBuffer worldPixels = worldRenderer.readbackRGBA();
         if (worldPixels == null) {
            System.out.println("[smoke] FAILURE — world readback returned null");
            System.exit(4);
         }
         int wcCenter = ((H / 2) * W + (W / 2)) * 4;
         int wr = worldPixels.get(wcCenter) & 0xFF;
         int wg = worldPixels.get(wcCenter + 1) & 0xFF;
         int wb = worldPixels.get(wcCenter + 2) & 0xFF;
         int wa = worldPixels.get(wcCenter + 3) & 0xFF;
         System.out.println("[smoke] world center pixel RGBA = (" + wr + "," + wg + "," + wb + "," + wa + ")");
         if (wg > 200 && wr < 60 && wb < 60 && wa > 200) {
            System.out.println("[smoke] SUCCESS — Vulkan world draw path works (green quad rendered via 3D pipeline).");
         } else {
            System.out.println("[smoke] FAILURE — world pixel check failed (expected green center).");
            System.exit(5);
         }
         worldRenderer.destroy();
         System.out.println("[smoke] All stages passed.");
      } catch (Throwable t) {
         System.out.println("[smoke] FAILURE — " + t.getClass().getSimpleName() + ": " + t.getMessage());
         t.printStackTrace();
         System.exit(2);
      } finally {
         if (pipeline != null) pipeline.destroy();
         if (instanceBuffer != null) instanceBuffer.destroy();
         if (descriptors != null) descriptors.destroy();
         if (textures != null) textures.destroy();
         if (shaders != null) shaders.destroy();
         if (frameSync != null) frameSync.destroy();
         if (offscreen != null) offscreen.destroy();
         ctx.destroy();
         System.out.println("[smoke] Stage 2 done.");
      }
   }

   private static void recordDraw(VulkanContext ctx, VulkanFrameSync frameSync, VulkanOffscreen offscreen,
         VulkanDescriptors descriptors, VulkanPipeline pipeline, VulkanInstanceBuffer instanceBuffer,
         int instanceCount) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         int err = VK10.vkResetCommandBuffer(frameSync.commandBuffer(), 0);
         check(err, "vkResetCommandBuffer");

         VkCommandBufferBeginInfo bi = VkCommandBufferBeginInfo.calloc(stack);
         bi.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
         bi.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
         err = VK10.vkBeginCommandBuffer(frameSync.commandBuffer(), bi);
         check(err, "vkBeginCommandBuffer");

         VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
         clearValues.get(0).color().float32(0, 0.0F).float32(1, 0.0F).float32(2, 0.0F).float32(3, 0.0F);

         VkRenderPassBeginInfo rpbi = VkRenderPassBeginInfo.calloc(stack);
         rpbi.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
         rpbi.renderPass(offscreen.renderPass());
         rpbi.framebuffer(offscreen.framebuffer());
         rpbi.renderArea().offset().set(0, 0);
         rpbi.renderArea().extent().set(W, H);
         rpbi.pClearValues(clearValues);

         VK10.vkCmdBeginRenderPass(frameSync.commandBuffer(), rpbi, VK10.VK_SUBPASS_CONTENTS_INLINE);
         VK10.vkCmdBindPipeline(frameSync.commandBuffer(), VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
               pipeline.pipeline());

         VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
         viewport.get(0).x(0.0F).y(0.0F).width(W).height(H).minDepth(0.0F).maxDepth(1.0F);
         VK10.vkCmdSetViewport(frameSync.commandBuffer(), 0, viewport);

         VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
         scissor.get(0).offset().set(0, 0);
         scissor.get(0).extent().set(W, H);
         VK10.vkCmdSetScissor(frameSync.commandBuffer(), 0, scissor);

         ByteBuffer push = stack.malloc(8).order(ByteOrder.nativeOrder());
         push.putFloat(0, W);
         push.putFloat(4, H);
         VK10.vkCmdPushConstants(frameSync.commandBuffer(), descriptors.pipelineLayout(),
               VK10.VK_SHADER_STAGE_VERTEX_BIT, 0, push);

         java.nio.LongBuffer pSet0 = stack.mallocLong(1);
         pSet0.put(0, descriptors.ssboSet());
         VK10.vkCmdBindDescriptorSets(frameSync.commandBuffer(), VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
               descriptors.pipelineLayout(), 0, pSet0, null);

         java.nio.LongBuffer pSet1 = stack.mallocLong(1);
         pSet1.put(0, descriptors.samplerSet());
         VK10.vkCmdBindDescriptorSets(frameSync.commandBuffer(), VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
               descriptors.pipelineLayout(), 1, pSet1, null);

         VK10.vkCmdDraw(frameSync.commandBuffer(), instanceCount * 6, 1, 0, 0);
         VK10.vkCmdEndRenderPass(frameSync.commandBuffer());
         err = VK10.vkEndCommandBuffer(frameSync.commandBuffer());
         check(err, "vkEndCommandBuffer");

         VkSubmitInfo si = VkSubmitInfo.calloc(stack);
         si.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);
         PointerBuffer pCb = stack.mallocPointer(1);
         pCb.put(0, frameSync.commandBuffer());
         si.pCommandBuffers(pCb);
         err = VK10.vkQueueSubmit(ctx.graphicsQueue(), si, frameSync.frameFence());
         check(err, "vkQueueSubmit");
         frameSync.waitForFence();
      }
   }

   private static void check(int err, String what) {
      if (err != VK10.VK_SUCCESS) {
         throw new IllegalStateException(what + " failed: " + VulkanContext.vulkanError(err));
      }
   }
}
