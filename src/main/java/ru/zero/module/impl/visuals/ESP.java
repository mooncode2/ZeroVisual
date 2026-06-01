package ru.zero.module.impl.visuals;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.network.AbstractClientPlayerEntity;
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
import ru.zero.util.render.world.WorldRenderer;

@IModule(name = "ESP", description = " ", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class ESP extends Module {
   private static final double MAX_RENDER_DISTANCE = 96.0;
   private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
   private static final int MAX_ENTITIES_PER_FRAME = 48;
   private static final double OUTLINE_WIDTH = 1.5;

   public static MultiBooleanSetting targets = new MultiBooleanSetting("Кого отображать",
         new BooleanSetting("Игроки", true), new BooleanSetting("Мобы", true));
   public static BooleanSetting onlyOnHover = new BooleanSetting("Только при наведении", false);
   public static HueSetting friendColor = new HueSetting("Friend color", 36.0F);
   public static HueSetting targetColor = new HueSetting("Target color", 0.0F);

   public ESP() {
      this.addSettings(new Setting[] { targets, onlyOnHover, friendColor, targetColor });
   }

   @EventInit
   public void render(WorldRenderEvent event) {
      if (mc.world == null || mc.player == null) {
         return;
      }

      WorldRenderer worldRenderer = event.worldRenderer();
      float tickDelta = worldRenderer.tickDelta();
      Box searchBox = mc.player.getBoundingBox().expand(MAX_RENDER_DISTANCE);
      List<Entity> nearby = mc.world.getOtherEntities(mc.player, searchBox, this::isCandidate);
      int rendered = 0;

      for (Entity entity : nearby) {
         if (rendered >= MAX_ENTITIES_PER_FRAME) {
            break;
         }

         if (this.shouldRender(entity)) {
            this.renderBox(worldRenderer, entity, tickDelta);
            rendered++;
         }
      }
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

      if (entity instanceof PlayerEntity) {
         return targets.get("Игроки");
      }

      return entity instanceof LivingEntity && targets.get("Мобы");
   }

   private void renderBox(WorldRenderer worldRenderer, Entity target, float partialTicks) {
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      double x = target.lastRenderX + (target.getX() - target.lastRenderX) * partialTicks;
      double y = target.lastRenderY + (target.getY() - target.lastRenderY) * partialTicks;
      double z = target.lastRenderZ + (target.getZ() - target.lastRenderZ) * partialTicks;
      if (mc.player.squaredDistanceTo(x, y, z) > MAX_RENDER_DISTANCE_SQ) {
         return;
      }

      Box boundingBox = target.getBoundingBox();
      double padding = 0.08;
      double minX = boundingBox.minX - target.getX() + x - padding;
      double minY = boundingBox.minY - target.getY() + y - padding;
      double minZ = boundingBox.minZ - target.getZ() + z - padding;
      double maxX = boundingBox.maxX - target.getX() + x + padding;
      double maxY = boundingBox.maxY - target.getY() + y + padding;
      double maxZ = boundingBox.maxZ - target.getZ() + z + padding;
      int fadeColor;
      if (target instanceof AbstractClientPlayerEntity player) {
         String name = player.getNameForScoreboard();
         if (Zero.get != null && Zero.get.friendManager != null && Zero.get.friendManager.isFriend(name)) {
            fadeColor = ColorUtil.replAlpha(friendColor.getRGB(), 255);
         } else if (Zero.get != null && Zero.get.targetManager != null && Zero.get.targetManager.isTarget(name)) {
            fadeColor = ColorUtil.replAlpha(targetColor.getRGB(), 255);
         } else {
            fadeColor = ColorUtil.fade();
         }
      } else {
         fadeColor = ColorUtil.fade();
      }

      int baseColor = ColorUtil.multAlpha(fadeColor, 1.0F);
      int color1 = ColorUtil.multDark(baseColor, 0.1F);
      int color2 = ColorUtil.multDark(baseColor, 1.0F);
      int color3 = ColorUtil.multDark(baseColor, 0.1F);
      int color4 = ColorUtil.multDark(baseColor, 1.0F);
      int fillColor = ColorUtil.replAlpha(ColorUtil.gradient(color1, color2, 0, 7), 85);
      int outlineColor = ColorUtil.replAlpha(ColorUtil.gradient(color2, color3, 90, 7), 255);
      Vec3d min = new Vec3d(minX, minY, minZ);
      Vec3d max = new Vec3d(maxX, maxY, maxZ);
      worldRenderer.drawCube(min, max, fillColor, false);
      this.drawOutline(worldRenderer, minX, minY, minZ, maxX, maxY, maxZ, outlineColor);
   }

   private void drawOutline(
         WorldRenderer worldRenderer,
         double minX,
         double minY,
         double minZ,
         double maxX,
         double maxY,
         double maxZ,
         int color
   ) {
      worldRenderer.drawLine(new Vec3d(minX, minY, minZ), new Vec3d(maxX, minY, minZ), OUTLINE_WIDTH, color, false);
      worldRenderer.drawLine(new Vec3d(maxX, minY, minZ), new Vec3d(maxX, minY, maxZ), OUTLINE_WIDTH, color, false);
      worldRenderer.drawLine(new Vec3d(maxX, minY, maxZ), new Vec3d(minX, minY, maxZ), OUTLINE_WIDTH, color, false);
      worldRenderer.drawLine(new Vec3d(minX, minY, maxZ), new Vec3d(minX, minY, minZ), OUTLINE_WIDTH, color, false);
      worldRenderer.drawLine(new Vec3d(minX, maxY, minZ), new Vec3d(maxX, maxY, minZ), OUTLINE_WIDTH, color, false);
      worldRenderer.drawLine(new Vec3d(maxX, maxY, minZ), new Vec3d(maxX, maxY, maxZ), OUTLINE_WIDTH, color, false);
      worldRenderer.drawLine(new Vec3d(maxX, maxY, maxZ), new Vec3d(minX, maxY, maxZ), OUTLINE_WIDTH, color, false);
      worldRenderer.drawLine(new Vec3d(minX, maxY, maxZ), new Vec3d(minX, maxY, minZ), OUTLINE_WIDTH, color, false);
      worldRenderer.drawLine(new Vec3d(minX, minY, minZ), new Vec3d(minX, maxY, minZ), OUTLINE_WIDTH, color, false);
      worldRenderer.drawLine(new Vec3d(maxX, minY, minZ), new Vec3d(maxX, maxY, minZ), OUTLINE_WIDTH, color, false);
      worldRenderer.drawLine(new Vec3d(maxX, minY, maxZ), new Vec3d(maxX, maxY, maxZ), OUTLINE_WIDTH, color, false);
      worldRenderer.drawLine(new Vec3d(minX, minY, maxZ), new Vec3d(minX, maxY, maxZ), OUTLINE_WIDTH, color, false);
   }
}
