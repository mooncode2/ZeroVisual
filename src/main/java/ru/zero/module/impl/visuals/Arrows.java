package ru.zero.module.impl.visuals;

import java.awt.Color;
import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventScreen;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.client.ClientLookState;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.player.MoveUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.MathHelper;
import ru.zero.util.render.math.ScaledResolution;
import ru.zero.util.render.math.animation.Animation;
import ru.zero.util.render.math.animation.Direction;
import ru.zero.util.render.math.animation.impl.EaseInOutQuad;

/**
 * Стрелки к игрокам на экране. Исключает цель под прицелом Vanilla; углы — от игрока или камеры при {@link ClientLookState#active}.
 */
@IModule(name = "Arrows", description = " ", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class Arrows extends Module {
   public static ModeSetting mode = new ModeSetting("Mode", "Type 1", "Type 1", "Type 2");
   public static SliderSetting distanceFromCrosshair = new SliderSetting("Отдаление от прицела", 100.0F, 40.0F, 260.0F, 1.0F, false);
   public static SliderSetting arrowSize = new SliderSetting("Размер стрелочек", 32.0F, 12.0F, 64.0F, 1.0F, false);
   public ArrayList<Arrows.ArrowsPlayer> arrowsPlayers = new ArrayList<>();

   public Arrows() {
      this.addSettings(new Setting[] { mode, distanceFromCrosshair, arrowSize });
   }

   @EventInit
   public void onRender(EventScreen e) {
      if (mc.player != null && mc.world != null) {
         for (Entity entity : mc.world.getPlayers()) {
            if (entity != mc.player) {
               boolean alreadyExists = false;

               for (Arrows.ArrowsPlayer arrowsPlayer : this.arrowsPlayers) {
                  if (arrowsPlayer.entity == entity) {
                     alreadyExists = true;
                     break;
                  }
               }

               if (!alreadyExists) {
                  this.arrowsPlayers.add(new Arrows.ArrowsPlayer(entity));
               }
            }
         }

         for (Arrows.ArrowsPlayer arrowsPlayerx : this.arrowsPlayers) {
            arrowsPlayerx.render(e.renderer());
         }

         this.arrowsPlayers.removeIf(
               arrow -> arrow.animation.getDirection() != Direction.FORWARDS && arrow.animation.getOutput() == 0.0F);
      }
   }

   @Environment(EnvType.CLIENT)
   public static class ArrowsPlayer {
      Animation animation = new EaseInOutQuad(300, 1.0);
      Entity entity;
      float animationStep;
      float lastYaw;
      float lastPitch;
      float animatedYaw;
      float animatedPitch;
      float yaw;

      public ArrowsPlayer(Entity entity) {
         this.entity = entity;
      }

      public void update() {
         boolean entityExists = Module.mc.world.getPlayers().contains(this.entity);
         boolean isInWorld = this.entity.isAlive();
         boolean isVisible = Module.mc.player != null && Module.mc.player.canSee(this.entity);
         this.animation
               .setDirection(
                     entityExists && isInWorld && this.entity != Module.mc.player
                           && this.entity != Module.mc.targetedEntity
                           && isVisible
                           ? Direction.FORWARDS
                           : Direction.BACKWARDS);
      }

      public void render(Renderer2D render) {
         this.update();
         ScaledResolution sr = new ScaledResolution(Module.mc);
         float[] movement = MoveUtil.getMovementFromKeys();
         float forward = movement[0];
         float strafe = movement[1];
         this.animatedYaw = MathHelper.fast(this.animatedYaw, strafe * 10.0F, 5.0F);
         this.animatedPitch = MathHelper.fast(this.animatedPitch, forward * 10.0F, 5.0F);
         float realYaw = ClientLookState.active ? Module.mc.gameRenderer.getCamera().getYaw() : Module.mc.player.getYaw();
         this.yaw = MathHelper.fast(this.yaw, realYaw, 10.0F);
         float size = this.animation.getOutput() * Arrows.distanceFromCrosshair.get();
         if (Module.mc.currentScreen instanceof GenericContainerScreen) {
            size += 200.0F;
         }

         if (Module.mc.currentScreen instanceof InventoryScreen) {
            size += 150.0F;
         }

         if (isMoving() || Module.mc.player.isInSneakingPose() || Module.mc.player.isSwimming()
               || Module.mc.currentScreen instanceof ChatScreen) {
            size += 20.0F;
         }

         this.animationStep = MathHelper.fast(this.animationStep, size, 6.0F);
         double x = this.entity.lastX
               + (this.entity.getX() - this.entity.lastX) * Module.mc.gameRenderer.getCamera().getLastTickProgress()
               - Module.mc.gameRenderer.getCamera().getCameraPos().x;
         double y = this.entity.lastY
               + (this.entity.getY() - this.entity.lastY) * Module.mc.gameRenderer.getCamera().getLastTickProgress()
               + this.entity.getHeight() / 2.0F
               - Module.mc.gameRenderer.getCamera().getCameraPos().y
               - Module.mc.player.getEyeHeight(Module.mc.player.getPose());
         double z = this.entity.lastZ
               + (this.entity.getZ() - this.entity.lastZ) * Module.mc.gameRenderer.getCamera().getLastTickProgress()
               - Module.mc.gameRenderer.getCamera().getCameraPos().z;
         double distance = Math.sqrt(x * x + y * y + z * z);
         double cos = (float) Math.cos((float) (this.yaw * (Math.PI / 180.0)));
         double sin = (float) Math.sin((float) (this.yaw * (Math.PI / 180.0)));
         double rotatateYaw = -(z * cos - x * sin);
         double rotatatePitch = -(x * cos + z * sin);
         double angle = Math.atan2(rotatateYaw, rotatatePitch) * 180.0 / Math.PI;
         double distanceFactor = Math.min(1.0, distance / 20.0);
         double xPos = this.animationStep * (float) Math.cos((float) Math.toRadians(angle))
               + sr.getScaledWidth_double();
         double yPos = this.animationStep * (float) Math.sin((float) Math.toRadians(angle))
               + sr.getScaledHeight_double();
         xPos += this.animatedYaw;
         yPos += this.animatedPitch + distanceFactor;
         Identifier texture = Identifier.of("zero",
               Arrows.mode.is("Type 1") ? "/textures/arrows/arrows1.png" : "/textures/arrows/arrows.png");
         if (Module.mc.getTextureManager().getTexture(texture).getGlTexture() instanceof GlTexture glTexture) {
            int id = glTexture.getGlId();
            if (id > 0) {
               int color = ColorUtil.fade();
               if (this.entity instanceof AbstractClientPlayerEntity p) {
                  String name = p.getNameForScoreboard();
                  if (Zero.get != null && Zero.get.friendManager != null && Zero.get.friendManager.isFriend(name)) {
                     color = ColorUtil.GREEN;
                  } else if (Zero.get != null && Zero.get.targetManager != null && Zero.get.targetManager.isTarget(name)) {
                     color = ColorUtil.RED;
                  }
               }
               int alpha = (int) (this.animation.getOutput() * 255.0F);
               Color c1 = Renderer2D.ColorUtil.getColor(
                     Renderer2D.ColorUtil.swapAlpha(Renderer2D.ColorUtil.getMainColor(1, 1),
                           this.animation.getOutput() * 50.0F));
               render.pushTranslation((float) xPos, (float) yPos);
               render.pushRotation((float) (angle - 90.0));
               render.shadow(0.5F, -1.0F, 0.1F, 0.1F, 5.0F, 8.0F, 0.1F, c1.getRGB());
               render.pushAlpha(alpha);
               float drawSize = Arrows.arrowSize.get();
               float half = drawSize / 2.0F;
               render.drawRgbaTexture(id, -half, -half, drawSize, drawSize, color, false);
               render.popAlpha();
               render.popTransform();
               render.popTransform();
               this.lastYaw = ClientLookState.active ? Module.mc.gameRenderer.getCamera().getYaw() : Module.mc.player.getYaw();
               this.lastPitch = ClientLookState.active ? Module.mc.gameRenderer.getCamera().getPitch()
                     : Module.mc.player.getPitch();
            }
         }
      }

      public static boolean isMoving() {
         float[] movement = MoveUtil.getMovementFromKeys();
         return movement[0] != 0.0F || movement[1] != 0.0F;
      }
   }
}
