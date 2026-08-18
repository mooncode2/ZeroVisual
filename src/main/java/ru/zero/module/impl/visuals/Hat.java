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
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import org.joml.Matrix4f;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.render.WorldRenderEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.util.color.ColorUtil;

@IModule(name = "Hat", description = " ", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class Hat extends Module {
   private static final int BUFFER_SIZE = 65536;
   public static ModeSetting mode = new ModeSetting("Mode", "China Hat", "China Hat", "Nimb");
   public static BooleanSetting fdg = new BooleanSetting("Прикреп к бошке", true);
   public static BooleanSetting friendsHat = new BooleanSetting("Hat for friends", true);
   public static HueSetting friendColor = new HueSetting("Friend color", 36.0F).hidden(() -> !friendsHat.get());
   public static BooleanSetting targetsHat = new BooleanSetting("Hat for targets", true);
   public static HueSetting targetColor = new HueSetting("Target color", 0.0F).hidden(() -> !targetsHat.get());
   private static final RenderPipeline HAT_FILL_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
               .withLocation(Identifier.of("zero", "hat_fill"))
               .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
    private static RenderPipeline HAT_LINE_PIPELINE = RenderPipelines.register(
          RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                .withLocation(Identifier.of("zero", "hat_line"))
                .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
                .withCull(false)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withBlend(BlendFunction.LIGHTNING)
                .build());
   private static final RenderLayer HAT_FILL_LAYER = RenderLayer.of("zero_hat_fill",
         RenderSetup.builder(HAT_FILL_PIPELINE)
               .expectedBufferSize(65536)
               .translucent()
               .build());
   private static final RenderLayer HAT_LINE_LAYER = RenderLayer.of("zero_hat_line",
         RenderSetup.builder(HAT_LINE_PIPELINE)
               .expectedBufferSize(65536)
               .translucent()
               .build());

   public Hat() {
      this.addSettings(new Setting[] { mode, fdg, friendsHat, friendColor, targetsHat, targetColor });
   }

   @EventInit
   public void onRender(WorldRenderEvent event) {
      if (mc.world != null && mc.player != null) {
         Immediate immediate = event.worldRenderer().bufferSource();
         MatrixStack matrices = event.matrixStack();
         float partialTicks = event.worldRenderer().tickDelta();
         Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

         for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == null || !p.isAlive()) continue;
            boolean isSelf = p == mc.player;
            String playerName = p.getName().getString();
            boolean isFriend = !isSelf && Zero.get != null && Zero.get.friendManager != null
                  && Zero.get.friendManager.isFriend(playerName);
            boolean isTarget = !isSelf && Zero.get != null && Zero.get.targetManager != null
                  && Zero.get.targetManager.isTarget(playerName);
            if (!isSelf) {
               if (isFriend && friendsHat.get()) {
                  // friend hat below
               } else if (isTarget && targetsHat.get()) {
                  // target hat below
               } else {
                  continue;
               }
            }

            if (isSelf && mc.options.getPerspective() == Perspective.FIRST_PERSON) continue;

            double x = p.lastRenderX + (p.getX() - p.lastRenderX) * partialTicks;
            double y = p.lastRenderY + (p.getY() - p.lastRenderY) * partialTicks;
            double z = p.lastRenderZ + (p.getZ() - p.lastRenderZ) * partialTicks;
            double hatY = y + p.getHeight() - (p.isSneaking() ? 0.25F : 0.05F);

            matrices.push();
            matrices.translate(x - cameraPos.x, hatY - cameraPos.y, z - cameraPos.z);
            if (fdg.get()) {
               float yaw = p.getYaw(partialTicks);
               float pitch = p.getPitch(partialTicks);
               matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
               matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
               float pitchAbs = Math.abs(pitch);
               float compensationY = pitchAbs / 90.0F * 0.3F;
               matrices.translate(0.0F, compensationY, 0.05F);
            }

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            HueSetting customColor = null;
            if (isFriend) {
               customColor = friendColor;
            } else if (isTarget) {
               customColor = targetColor;
            }

            if (mode.is("China Hat")) {
               this.renderChinaHat(immediate, matrix, customColor);
            } else if (mode.is("Nimb")) {
               this.renderNimb(immediate, matrix, customColor);
            }

            matrices.pop();
         }
      }
   }

   private void renderChinaHat(Immediate immediate, Matrix4f matrix, HueSetting customColor) {
      int segments = 120;
      float radius = 0.55F;
      float height = 0.25F;
      float alpha = 0.7058824F;
      VertexConsumer fillBuffer = immediate.getBuffer(this.getHatFillLayer());

      for (int i = 0; i < segments; i++) {
         float angle1 = (float) Math.toRadians(i * (360.0 / segments));
         float angle2 = (float) Math.toRadians((i + 1) * (360.0 / segments));
         int c1;
         int c2;
         if (customColor != null) {
            int base = ColorUtil.replAlpha(customColor.getRGB(), 255);
            int softA = ColorUtil.overCol(base, ColorUtil.multDark(base, 0.75F), 0.35F);
            int softB = ColorUtil.overCol(ColorUtil.multBright(base, 1.12F), base, 0.25F);
            int g1 = ColorUtil.gradient(ColorUtil.multDark(softB, 0.65F), softB, i * 4, 1);
            int g2 = ColorUtil.gradient(ColorUtil.multDark(softB, 0.65F), softB, (i + 1) * 4, 1);
            int mix1 = ColorUtil.overCol(ColorUtil.fade(i), g1, 0.75F);
            int mix2 = ColorUtil.overCol(ColorUtil.fade(i + 1), g2, 0.75F);
            c1 = ColorUtil.multAlpha(ColorUtil.overCol(mix1, softA, 0.25F), alpha);
            c2 = ColorUtil.multAlpha(ColorUtil.overCol(mix2, softA, 0.25F), alpha);
         } else {
            c1 = ColorUtil.multAlpha(ColorUtil.fade(i), alpha);
            c2 = ColorUtil.multAlpha(ColorUtil.fade(i + 1), alpha);
         }
         fillBuffer.vertex(matrix, 0.0F, height, 0.0F).color(c1);
         fillBuffer.vertex(matrix, (float) Math.cos(angle1) * radius, 0.0F, (float) Math.sin(angle1) * radius)
               .color(c1);
         fillBuffer.vertex(matrix, (float) Math.cos(angle2) * radius, 0.0F, (float) Math.sin(angle2) * radius)
               .color(c2);
      }

      VertexConsumer lineBuffer = immediate.getBuffer(this.getHatLineLayer());
      int lineCol;
      if (customColor != null) {
         int base = ColorUtil.replAlpha(customColor.getRGB(), 255);
         int softA = ColorUtil.overCol(base, ColorUtil.multDark(base, 0.75F), 0.35F);
         int softB = ColorUtil.overCol(ColorUtil.multBright(base, 1.12F), base, 0.25F);
         int g = ColorUtil.gradient(ColorUtil.multDark(softB, 0.7F), softB, 0, 1);
         lineCol = ColorUtil.replAlpha(ColorUtil.overCol(ColorUtil.fade(), ColorUtil.overCol(g, softA, 0.35F), 0.75F), 255);
      } else {
         lineCol = ColorUtil.replAlpha(ColorUtil.fade(), 255);
      }

      for (int i = 0; i < segments; i++) {
         float angle1 = (float) Math.toRadians(i * (360.0 / segments));
         float angle2 = (float) Math.toRadians((i + 1) * (360.0 / segments));
         lineBuffer.vertex(matrix, (float) Math.cos(angle1) * radius, 0.0F, (float) Math.sin(angle1) * radius)
               .color(lineCol);
         lineBuffer.vertex(matrix, (float) Math.cos(angle2) * radius, 0.0F, (float) Math.sin(angle2) * radius)
               .color(lineCol);
      }
   }

   private void renderNimb(Immediate immediate, Matrix4f matrix, HueSetting customColor) {
      int segments = 120;
      float radius = 0.4F;
      VertexConsumer lineBuffer = immediate.getBuffer(this.getHatLineLayer());
      int color;
      if (customColor != null) {
         int base = ColorUtil.replAlpha(customColor.getRGB(), 255);
         int softA = ColorUtil.overCol(base, ColorUtil.multDark(base, 0.75F), 0.35F);
         int softB = ColorUtil.overCol(ColorUtil.multBright(base, 1.12F), base, 0.25F);
         int g = ColorUtil.gradient(ColorUtil.multDark(softB, 0.7F), softB, 0, 1);
         color = ColorUtil.replAlpha(ColorUtil.overCol(ColorUtil.fade(), ColorUtil.overCol(g, softA, 0.35F), 0.75F), 255);
      } else {
         color = ColorUtil.replAlpha(ColorUtil.fade(), 255);
      }

      for (int i = 0; i < segments; i++) {
         float angle1 = (float) Math.toRadians(i * (360.0 / segments));
         float angle2 = (float) Math.toRadians((i + 1) * (360.0 / segments));
         lineBuffer.vertex(matrix, (float) Math.cos(angle1) * radius, 0.1F, (float) Math.sin(angle1) * radius)
               .color(color);
         lineBuffer.vertex(matrix, (float) Math.cos(angle2) * radius, 0.1F, (float) Math.sin(angle2) * radius)
               .color(color);
      }
   }

   private RenderLayer getHatFillLayer() {
      return HAT_FILL_LAYER;
   }

   private RenderLayer getHatLineLayer() {
      return HAT_LINE_LAYER;
   }
}
