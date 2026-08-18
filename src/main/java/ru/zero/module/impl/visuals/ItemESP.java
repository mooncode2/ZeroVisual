package ru.zero.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.OptionalDouble;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import org.joml.Matrix4f;
import ru.zero.event.EventInit;
import ru.zero.event.render.WorldRenderEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.world.WorldRenderUtil;

@IModule(name = "Item ESP", description = " ", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class ItemESP extends Module {
      private static final int QUAD_BUFFER_SIZE_BYTES = 1024;
      private static final Identifier GLOW_TEXTURE_C = Identifier.of("zero", "textures/world/dashbloom.png");
      private static final Identifier GLOW_TEXTURE_G = Identifier.of("zero", "textures/world/dashbloomsample.png");
      private static final String PIPELINE_NAMESPACE = "zero";
      private static final RenderPipeline BOX_FILL_PIPELINE = RenderPipelines.register(
                  RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                              .withLocation(Identifier.of("minecraft", "rendertype_lequal_depth_test"))
                              .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
                              .withCull(false)
                              .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                              .withDepthWrite(false)
                              .withBlend(BlendFunction.LIGHTNING)
                              .build());
      private static final RenderPipeline BOX_LINE_PIPELINE = RenderPipelines.register(
                  RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                              .withLocation(Identifier.of("minecraft", "rendertype_lines"))
                              .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
                              .withCull(false)
                              .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                              .withDepthWrite(false)
                              .withBlend(BlendFunction.LIGHTNING)
                              .build());
      private static final RenderLayer BOX_FILL_LAYER = RenderLayer.of("zero_itemesp_box_fill", RenderSetup.builder(BOX_FILL_PIPELINE).expectedBufferSize(1024).translucent().build());
      private static final RenderLayer BOX_LINE_LAYER = RenderLayer.of(
                  "night_itemesp_box_line",
                  RenderSetup.builder(BOX_LINE_PIPELINE)
                        .expectedBufferSize(1024)
                        .translucent()
                        .build());
      private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
                  RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_TEX_COLOR_SNIPPET })
                              .withLocation(Identifier.of("zero", "itemesp_glow"))
                              .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                              .withCull(false)
                              .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                              .withDepthWrite(false)
                              .withBlend(BlendFunction.LIGHTNING)
                              .build());
      private static final RenderLayer GLOW_LAYER = RenderLayer.of("zero_itemesp_glow", RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", GLOW_TEXTURE_C).build());
      private static final RenderLayer GLOW_LAYER_G = RenderLayer.of("zero_itemesp_glow_g", RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", GLOW_TEXTURE_G).build());
      private final int[] gradientColors = new int[4];

      @EventInit
      public void render(WorldRenderEvent event) {
            if (mc.world != null && mc.player != null) {
                  Immediate immediate = event.worldRenderer().bufferSource();
                  float tickDelta = event.worldRenderer().tickDelta();
                  Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
                  int fadeColor = ColorUtil.fade();
                  int baseColor = ColorUtil.multAlpha(fadeColor, 1.0F);
                  int color1 = ColorUtil.multDark(baseColor, 0.1F);
                  int color2 = ColorUtil.multDark(baseColor, 1.0F);
                  int[] gradientColors = this.gradientColors;
                  gradientColors[0] = ColorUtil.gradient(color1, color2, 0, 7);
                  gradientColors[1] = ColorUtil.gradient(color2, color1, 90, 7);
                  gradientColors[2] = ColorUtil.gradient(color1, color2, 180, 7);
                  gradientColors[3] = ColorUtil.gradient(color2, color1, 270, 7);
                  float rotation = (float) (System.currentTimeMillis() % 5400L / 15.0);
                  float cameraYaw = mc.gameRenderer.getCamera().getYaw();
                  float cameraPitch = mc.gameRenderer.getCamera().getPitch();

                  for (Entity ent : mc.world.getEntities()) {
                        if (ent instanceof ItemEntity && mc.player.canSee(ent)) {
                              this.renderBox(event.matrixStack(), immediate, ent, tickDelta, cameraPos, gradientColors, rotation, fadeColor, cameraYaw, cameraPitch);
                        }
                  }
            }
      }

      private void renderBox(MatrixStack matrices, Immediate immediate, Entity target, float partialTicks,
            Vec3d cameraPos, int[] gradientColors, float rotation, int fadeColor, float cameraYaw, float cameraPitch) {
            if (target != null) {
                  double x = target.lastRenderX + (target.getX() - target.lastRenderX) * partialTicks;
                  double y = target.lastRenderY + (target.getY() - target.lastRenderY) * partialTicks;
                  double z = target.lastRenderZ + (target.getZ() - target.lastRenderZ) * partialTicks;
                  double size = 0.4;
                  double halfSize = size / 2.0;
                  double offsetY = 0.25;
                  double minX = x - halfSize - cameraPos.x;
                  double minY = y - halfSize + offsetY - cameraPos.y;
                  double minZ = z - halfSize - cameraPos.z;
                  double maxX = x + halfSize - cameraPos.x;
                  double maxY = y + halfSize + offsetY - cameraPos.y;
                  double maxZ = z + halfSize - cameraPos.z;
                  matrices.push();
                  matrices.translate((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
                  matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation));
                  matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
                  matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
                  matrices.translate(-(minX + maxX) / 2.0, -(minY + maxY) / 2.0, -(minZ + maxZ) / 2.0);
                  Matrix4f matrix1 = matrices.peek().getPositionMatrix();
                  VertexConsumer fillBuffer1 = immediate.getBuffer(BOX_FILL_LAYER);
                  WorldRenderUtil.drawBoxFill(fillBuffer1, matrix1, minX, minY, minZ, maxX, maxY, maxZ, gradientColors,
                              60);
                  VertexConsumer lineBuffer1 = immediate.getBuffer(BOX_LINE_LAYER);
                  WorldRenderUtil.drawBoxOutline(lineBuffer1, matrix1, minX, minY, minZ, maxX, maxY, maxZ,
                              gradientColors, 200,
                              0.25, 0.08);
                  matrices.pop();
                  matrices.push();
                  matrices.translate((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
                  matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-rotation));
                  matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-rotation));
                  matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-rotation));
                  matrices.translate(-(minX + maxX) / 2.0, -(minY + maxY) / 2.0, -(minZ + maxZ) / 2.0);
                  Matrix4f matrix2 = matrices.peek().getPositionMatrix();
                  VertexConsumer fillBuffer2 = immediate.getBuffer(BOX_FILL_LAYER);
                  WorldRenderUtil.drawBoxFill(fillBuffer2, matrix2, minX, minY, minZ, maxX, maxY, maxZ, gradientColors,
                              60);
                  VertexConsumer lineBuffer2 = immediate.getBuffer(BOX_LINE_LAYER);
                  WorldRenderUtil.drawBoxOutline(lineBuffer2, matrix2, minX, minY, minZ, maxX, maxY, maxZ,
                              gradientColors, 200,
                              0.25, 0.08);
                  matrices.pop();
                  double centerX = (minX + maxX) / 2.0;
                  double centerY = (minY + maxY) / 2.0;
                  double centerZ = (minZ + maxZ) / 2.0;
                  matrices.push();
                  matrices.translate(centerX, centerY, centerZ);
                  matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
                  matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
                  Matrix4f glowMatrix = matrices.peek().getPositionMatrix();
                  int glowColor = ColorUtil.multAlpha(fadeColor, 1.0F);
                  float glowSize = (float) Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) * 0.8F;
                  WorldRenderUtil.drawGlow(immediate.getBuffer(GLOW_LAYER), glowMatrix, glowColor, 160,
                              glowSize * 3.0F);
                  WorldRenderUtil.drawGlow(immediate.getBuffer(GLOW_LAYER_G), glowMatrix, glowColor, 140, glowSize);
                  matrices.pop();
            }
      }
}
