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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import ru.zero.util.render.world.WorldRenderer;
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
   private static final int QUAD_BUFFER_SIZE_BYTES = 1024;
   public static MultiBooleanSetting targets = new MultiBooleanSetting("Кого отображать",
         new BooleanSetting("Игроки", true), new BooleanSetting("Мобы", true));
   public static HueSetting friendColor = new HueSetting("Friend color", 36.0F);
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
   private static final RenderLayer BOX_FILL_LAYER = RenderLayer.of(
         "zero_esp_box_fill",
         RenderSetup.builder(BOX_FILL_PIPELINE)
               .expectedBufferSize(1024)
               .translucent()
               .build());
   private static final RenderLayer BOX_LINE_LAYER = RenderLayer.of(
         "zero_esp_box_line",
         RenderSetup.builder(BOX_LINE_PIPELINE)
               .expectedBufferSize(1024)
               .translucent()
               .build());

   public ESP() {
      this.addSettings(new Setting[] { targets, friendColor });
   }

   @EventInit
   public void render(WorldRenderEvent event) {
      if (mc.world != null && mc.player != null) {
         WorldRenderer worldRenderer = event.worldRenderer();
         Immediate immediate = worldRenderer.bufferSource();
         MatrixStack matrices = event.matrixStack();
         float tickDelta = worldRenderer.tickDelta();

         for (Entity ent : mc.world.getEntities()) {
            if (this.shouldRender(ent)) {
               this.renderBox(matrices, immediate, ent, tickDelta);
            }
         }
      }
   }

   private boolean shouldRender(Entity entity) {
      if (entity == mc.player) {
         return false;
      } else if (entity instanceof LivingEntity living
            && (living.isInvisible() || living.hasStatusEffect(StatusEffects.INVISIBILITY))) {
         return false;
      } else if (!mc.player.canSee(entity)) {
         return false;
      } else if (entity instanceof PlayerEntity) {
         return targets.get("Игроки");
      } else {
         return entity instanceof LivingEntity ? targets.get("Мобы") : false;
      }
   }

   private void renderBox(MatrixStack matrices, Immediate immediate, Entity target, float partialTicks) {
      if (target != null) {
         Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
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
         float alphaPC = 1.0F;
         int fadeColor;
         if (target instanceof AbstractClientPlayerEntity p && Zero.get.friendManager.isFriend(p.getNameForScoreboard())) {
            fadeColor = ColorUtil.replAlpha(friendColor.getRGB(), 255);
         } else {
            fadeColor = ColorUtil.fade();
         }
         int baseColor = ColorUtil.multAlpha(fadeColor, alphaPC);
         int color1 = ColorUtil.multDark(baseColor, 0.1F);
         int color2 = ColorUtil.multDark(baseColor, 1.0F);
         int color3 = ColorUtil.multDark(baseColor, 0.1F);
         int color4 = ColorUtil.multDark(baseColor, 1.0F);
         int[] gradientColors = new int[] {
               ColorUtil.gradient(color1, color2, 0, 7),
               ColorUtil.gradient(color2, color3, 90, 7),
               ColorUtil.gradient(color3, color4, 180, 7),
               ColorUtil.gradient(color4, color1, 270, 7)
         };
         Matrix4f matrix = matrices.peek().getPositionMatrix();
         VertexConsumer fillBuffer = immediate.getBuffer(BOX_FILL_LAYER);
         WorldRenderUtil.drawBoxFill(fillBuffer, matrix, minX, minY, minZ, maxX, maxY, maxZ, gradientColors, 85);
         VertexConsumer lineBuffer = immediate.getBuffer(BOX_LINE_LAYER);
         WorldRenderUtil.drawBoxOutline(lineBuffer, matrix, minX, minY, minZ, maxX, maxY, maxZ, gradientColors, 255,
               0.15, 0.08);
      }
   }
}
