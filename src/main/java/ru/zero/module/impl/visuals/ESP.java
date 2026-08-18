package ru.zero.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
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
import ru.zero.module.api.setting.impl.MultiBooleanSetting;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.world.WorldRenderUtil;

@IModule(name = "ESP", description = " ", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class ESP extends Module {
   private static final double MAX_RENDER_DISTANCE = 96.0;
   private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

   private static final RenderPipeline BOX_FILL_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
               .withLocation(Identifier.of("zero", "esp_box_fill"))
               .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final RenderPipeline BOX_LINE_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
               .withLocation(Identifier.of("zero", "esp_box_line"))
               .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final RenderLayer BOX_FILL_LAYER = RenderLayer.of(
         "zero_esp_box_fill",
         RenderSetup.builder(BOX_FILL_PIPELINE).expectedBufferSize(1024).translucent().build());
   private static final RenderLayer BOX_LINE_LAYER = RenderLayer.of(
         "zero_esp_box_line",
         RenderSetup.builder(BOX_LINE_PIPELINE).expectedBufferSize(1024).translucent().build());

   public static MultiBooleanSetting targets = new MultiBooleanSetting("Кого отображать",
         new BooleanSetting("Игроки", true), new BooleanSetting("Мобы", true));
   public static BooleanSetting onlyOnHover = new BooleanSetting("Только при наведении", false);
   public static HueSetting friendColor = new HueSetting("Friend color", 36.0F);
   public static HueSetting targetColor = new HueSetting("Target color", 0.0F);

   private final int[] scratchGradient = new int[4];
   private final int[] defaultGradient = new int[4];
   private final java.util.List<Entity> targetBuffer = new java.util.ArrayList<>();

   public ESP() {
      this.addSettings(new Setting[] { targets, onlyOnHover, friendColor, targetColor });
   }

   private static int[] computeGradientInto(int baseColor, int[] out) {
      int dark = ColorUtil.multDark(baseColor, 0.1F);
      out[0] = ColorUtil.gradient(dark, baseColor, 0, 7);
      out[1] = ColorUtil.gradient(baseColor, dark, 90, 7);
      out[2] = ColorUtil.gradient(dark, baseColor, 180, 7);
      out[3] = ColorUtil.gradient(baseColor, dark, 270, 7);
      return out;
   }

   @EventInit
   public void render(WorldRenderEvent event) {
      if (mc.world == null || mc.player == null) {
         return;
      }

      float tickDelta = event.worldRenderer().tickDelta();
      List<Entity> entities = this.collectTargets();
      if (entities.isEmpty()) {
         return;
      }

       MatrixStack matrices = event.matrixStack();
       Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
       Immediate immediate = event.worldRenderer().bufferSource();

      int fadeBase = ColorUtil.fade();
      int[] defaultGradient = ESP.computeGradientInto(fadeBase, this.defaultGradient);

      for (Entity entity : entities) {
         this.renderBox(matrices, immediate, entity, tickDelta, cameraPos, fadeBase, defaultGradient);
      }
   }

    private List<Entity> collectTargets() {
       List<Entity> result = this.targetBuffer;
       result.clear();

       for (Entity entity : mc.world.getEntities()) {
          if (!this.isCandidate(entity) || !this.shouldRender(entity)) {
             continue;
          }

          if (mc.player.squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ()) > MAX_RENDER_DISTANCE_SQ) {
             continue;
          }

          result.add(entity);
       }

       if (result.size() > 1) {
          final double playerX = mc.player.getX();
          final double playerY = mc.player.getY();
          final double playerZ = mc.player.getZ();
          result.sort(Comparator.comparingDouble(
                entity -> {
                   double dx = entity.getX() - playerX;
                   double dy = entity.getY() - playerY;
                   double dz = entity.getZ() - playerZ;
                   return dx * dx + dy * dy + dz * dz;
                }));
       }
       return result;
    }

   private boolean isCandidate(Entity entity) {
      if (entity == null || entity == mc.player || !entity.isAlive()) {
         return false;
      }

      return entity instanceof PlayerEntity || entity instanceof LivingEntity;
   }

   private boolean shouldRender(Entity entity) {
      if (onlyOnHover.get() && mc.targetedEntity != entity) {
         return false;
      }

      if (mc.player != null && !mc.player.canSee(entity)) {
         return false;
      }

      // Hide entities with invisibility effect
      if (entity.isInvisible()) {
         return false;
      }

      if (entity instanceof PlayerEntity) {
         return targets.get("Игроки");
      }

      return entity instanceof LivingEntity && targets.get("Мобы");
   }

   private void renderBox(MatrixStack matrices, Immediate immediate, Entity target, float partialTicks,
         Vec3d cameraPos, int fadeBase, int[] defaultGradient) {
      double x = target.lastRenderX + (target.getX() - target.lastRenderX) * partialTicks;
      double y = target.lastRenderY + (target.getY() - target.lastRenderY) * partialTicks;
      double z = target.lastRenderZ + (target.getZ() - target.lastRenderZ) * partialTicks;

      Box boundingBox = target.getBoundingBox();
      double padding = 0.08;
      double minX = boundingBox.minX - target.getX() + x - padding - cameraPos.x;
      double minY = boundingBox.minY - target.getY() + y - padding - cameraPos.y;
      double minZ = boundingBox.minZ - target.getZ() + z - padding - cameraPos.z;
      double maxX = boundingBox.maxX - target.getX() + x + padding - cameraPos.x;
      double maxY = boundingBox.maxY - target.getY() + y + padding - cameraPos.y;
      double maxZ = boundingBox.maxZ - target.getZ() + z + padding - cameraPos.z;

      int[] gradient;
      if (target instanceof AbstractClientPlayerEntity player) {
         String name = player.getNameForScoreboard();
         if (Zero.get != null && Zero.get.friendManager != null && Zero.get.friendManager.isFriend(name)) {
            gradient = ESP.computeGradientInto(ColorUtil.replAlpha(friendColor.getRGB(), 255), this.scratchGradient);
         } else if (Zero.get != null && Zero.get.targetManager != null && Zero.get.targetManager.isTarget(name)) {
            gradient = ESP.computeGradientInto(ColorUtil.replAlpha(targetColor.getRGB(), 255), this.scratchGradient);
         } else {
            gradient = defaultGradient;
         }
      } else {
         gradient = defaultGradient;
      }

      Matrix4f matrix = matrices.peek().getPositionMatrix();
      VertexConsumer fillBuffer = immediate.getBuffer(BOX_FILL_LAYER);
      WorldRenderUtil.drawBoxFill(fillBuffer, matrix, minX, minY, minZ, maxX, maxY, maxZ, gradient, 85);
      VertexConsumer lineBuffer = immediate.getBuffer(BOX_LINE_LAYER);
      WorldRenderUtil.drawBoxOutline(lineBuffer, matrix, minX, minY, minZ, maxX, maxY, maxZ, gradient, 255, 0.15, 0.08);
   }
}
