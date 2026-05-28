package ru.zero.module.impl.visuals;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.Arm;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import ru.zero.event.EventInit;
import ru.zero.event.render.WorldRenderEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.MultiBooleanSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.world.WorldRenderer;

@IModule(name = "Projectile", description = "Показывает траекторию снарядов", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class Projectile extends Module {
   private static final int BASE_COLOR = Renderer2D.ColorUtil.getMainColor(1, 1);
   private static final double MAX_TRACK_DISTANCE_SQ = 80.0 * 80.0;
   private static final int MAX_WORLD_PROJECTILES_PER_FRAME = 24;
   public static MultiBooleanSetting projectileTypes = new MultiBooleanSetting(
         "Снаряды",
         new BooleanSetting("Стрелы/арбалет", true),
         new BooleanSetting("Трезубец", true),
         new BooleanSetting("Перл", true),
         new BooleanSetting("Бросаемое", true),
         new BooleanSetting("Остальные", true));
   public static MultiBooleanSetting owners = new MultiBooleanSetting(
         "Кто кидает",
         new BooleanSetting("Мои", true),
         new BooleanSetting("Другие игроки", true),
         new BooleanSetting("Мобы", true));
   public static BooleanSetting showWorldProjectiles = new BooleanSetting("Снаряды в мире", true);
   public static BooleanSetting showAimPrediction = new BooleanSetting("Предикт при прицеливании", true);
   public static BooleanSetting showThirdPerson = new BooleanSetting("Отображать от третьего лица", true);
   public static BooleanSetting showImpactBlock = new BooleanSetting("Подсветка попадания", true);
   public static BooleanSetting showTargetOutline = new BooleanSetting("Обводка цели", true);
   public static BooleanSetting ignoreDepth = new BooleanSetting("Поверх мира", false);
   public static SliderSetting lineWidth = new SliderSetting("Толщина", 0.25F, 0.1F, 1.2F, 0.05F, false);
   public static SliderSetting lineAlpha = new SliderSetting("Прозрачность", 145.0F, 20.0F, 255.0F, 1.0F, false);
   public static SliderSetting simulationSteps = new SliderSetting("Длина траектории", 120.0F, 20.0F, 260.0F, 1.0F, false);
   public static SliderSetting simulationStep = new SliderSetting("Точность", 0.15F, 0.05F, 1.0F, 0.05F, false);

   public Projectile() {
      this.addSettings(new Setting[] {
            projectileTypes,
            owners,
            showWorldProjectiles,
            showAimPrediction,
            showThirdPerson,
            showImpactBlock,
            showTargetOutline,
            ignoreDepth,
            lineWidth,
            lineAlpha,
            simulationSteps,
            simulationStep
      });
   }

   @EventInit
   public void onWorldRender(WorldRenderEvent event) {
      if (mc.world == null || mc.player == null) {
         return;
      }

      WorldRenderer renderer = event.worldRenderer();
      int maxSteps = Math.max(1, (int) simulationSteps.get());
      double step = Math.max(0.05, simulationStep.get());
      double width = Math.max(0.1, lineWidth.get());
      int alpha = Math.max(0, Math.min(255, (int) lineAlpha.get()));
      int startColor = ColorUtil.replAlpha(BASE_COLOR, alpha);
      int endColor = ColorUtil.replAlpha(ColorUtil.multDark(BASE_COLOR, 0.45F), alpha);
      boolean depthTest = !ignoreDepth.get();

      if (showWorldProjectiles.get()) {
         int processed = 0;
         for (Entity entity : mc.world.getEntities()) {
            if (processed >= MAX_WORLD_PROJECTILES_PER_FRAME) {
               break;
            }
            if (!(entity instanceof ProjectileEntity projectile)) {
               continue;
            }

            if (mc.player.squaredDistanceTo(projectile) > MAX_TRACK_DISTANCE_SQ) {
               continue;
            }

            if (this.isLanded(projectile)) {
               continue;
            }

            if (!this.shouldTrack(projectile)) {
               continue;
            }

            Trajectory trajectory = this.simulateEntityTrajectory(projectile, maxSteps, step);
            this.renderTrajectory(renderer, trajectory, width, startColor, endColor, depthTest);
            processed++;
         }
      }

      if (showAimPrediction.get()) {
         if (!showThirdPerson.get() && !mc.options.getPerspective().isFirstPerson()) {
            return;
         }
         Trajectory predicted = this.simulatePlayerPrediction(mc.player, maxSteps, step);
         this.renderTrajectory(renderer, predicted, width, startColor, endColor, depthTest);
      }
   }

   private void renderTrajectory(
         WorldRenderer renderer,
         Trajectory trajectory,
         double width,
         int startColor,
         int endColor,
         boolean depthTest
   ) {
      if (trajectory == null || trajectory.points == null || trajectory.points.size() < 2) {
         return;
      }

      int segments = trajectory.points.size() - 1;
      for (int i = 0; i < segments; i++) {
         float t = segments <= 1 ? 1.0F : (float) i / (float) (segments - 1);
         int color = ColorUtil.fadeBetween(startColor, endColor, t);
         renderer.drawLine(trajectory.points.get(i), trajectory.points.get(i + 1), width, color, depthTest);
      }

      if (showImpactBlock.get() && trajectory.impactBlock != null && trajectory.showImpact) {
         BlockHiliter.renderHighlightForBlock(renderer, mc.world, trajectory.impactBlock, mc.player, BASE_COLOR);
      }

      if (showTargetOutline.get() && trajectory.hitEntity != null && trajectory.showTargetOutline) {
         this.renderEntityOutline(renderer, trajectory.hitEntity, width, startColor, depthTest);
      }
   }

   private void renderEntityOutline(WorldRenderer renderer, Entity entity, double width, int rgbaColor, boolean depthTest) {
      if (entity == null) {
         return;
      }
      Box bb = entity.getBoundingBox().expand(0.02);
      Vec3d b0 = new Vec3d(bb.minX, bb.minY, bb.minZ);
      Vec3d b1 = new Vec3d(bb.maxX, bb.minY, bb.minZ);
      Vec3d b2 = new Vec3d(bb.maxX, bb.minY, bb.maxZ);
      Vec3d b3 = new Vec3d(bb.minX, bb.minY, bb.maxZ);
      Vec3d t0 = new Vec3d(bb.minX, bb.maxY, bb.minZ);
      Vec3d t1 = new Vec3d(bb.maxX, bb.maxY, bb.minZ);
      Vec3d t2 = new Vec3d(bb.maxX, bb.maxY, bb.maxZ);
      Vec3d t3 = new Vec3d(bb.minX, bb.maxY, bb.maxZ);

      renderer.drawLine(b0, b1, width, rgbaColor, depthTest);
      renderer.drawLine(b1, b2, width, rgbaColor, depthTest);
      renderer.drawLine(b2, b3, width, rgbaColor, depthTest);
      renderer.drawLine(b3, b0, width, rgbaColor, depthTest);
      renderer.drawLine(t0, t1, width, rgbaColor, depthTest);
      renderer.drawLine(t1, t2, width, rgbaColor, depthTest);
      renderer.drawLine(t2, t3, width, rgbaColor, depthTest);
      renderer.drawLine(t3, t0, width, rgbaColor, depthTest);
      renderer.drawLine(b0, t0, width, rgbaColor, depthTest);
      renderer.drawLine(b1, t1, width, rgbaColor, depthTest);
      renderer.drawLine(b2, t2, width, rgbaColor, depthTest);
      renderer.drawLine(b3, t3, width, rgbaColor, depthTest);
   }

   private boolean isLanded(ProjectileEntity projectile) {
      if (projectile instanceof PersistentProjectileEntity persistent) {
         return persistent.isOnGround();
      }
      if (projectile instanceof TridentEntity trident) {
         return trident.isOnGround();
      }
      return false;
   }

   private boolean shouldTrack(ProjectileEntity projectile) {
      if (projectile == null || !projectile.isAlive()) {
         return false;
      }

      if (!this.isEnabledType(projectile)) {
         return false;
      }

      Entity owner = projectile.getOwner();
      if (owner == null) {
         return owners.get("Мобы");
      }

      if (owner == mc.player) {
         return owners.get("Мои");
      }

      if (owner instanceof PlayerEntity) {
         return owners.get("Другие игроки");
      }

      return owners.get("Мобы");
   }

   private boolean isEnabledType(ProjectileEntity projectile) {
      if (projectile instanceof EnderPearlEntity) {
         return projectileTypes.get("Перл");
      }

      if (projectile instanceof TridentEntity) {
         return projectileTypes.get("Трезубец");
      }

      if (projectile instanceof PersistentProjectileEntity) {
         return projectileTypes.get("Стрелы/арбалет");
      }

      if (projectile instanceof ThrownItemEntity) {
         return projectileTypes.get("Бросаемое");
      }

      return projectileTypes.get("Остальные");
   }

   private Trajectory simulateEntityTrajectory(ProjectileEntity projectile, int maxSteps, double step) {
      Vec3d position = projectile.getLerpedPos(mc.getRenderTickCounter().getTickProgress(true));
      Vec3d velocity = projectile.getVelocity();
      double gravity = this.resolveGravity(projectile);
      double drag = projectile.isTouchingWater() ? 0.8 : 0.99;
      return this.simulate(position, velocity, projectile.hasNoGravity(), drag, gravity, maxSteps, step, false, false);
   }

   private Trajectory simulatePlayerPrediction(PlayerEntity player, int maxSteps, double step) {
      ItemStack stack = player.getMainHandStack();
      if (stack == null || stack.isEmpty()) {
         return null;
      }

      Item item = stack.getItem();
      boolean using = player.isUsingItem();
      Vec3d start = this.getHandStart(player);
      Vec3d look = player.getRotationVec(1.0F);
      Vec3d velocity;
      double gravity;
      double drag = 0.99;

      boolean shouldShowImpact = true;

      if (item instanceof BowItem) {
         if (!using) {
            return null;
         }
         float pull = BowItem.getPullProgress(stack.getMaxUseTime(player) - player.getItemUseTimeLeft());
         if (pull <= 0.03F) {
            return null;
         }
         velocity = look.multiply(Math.max(0.15F, pull) * 3.0F);
         gravity = 0.05;
      } else if (item instanceof CrossbowItem) {
         boolean charged = CrossbowItem.isCharged(stack);
         if (!charged && !using) {
            return null;
         }
         velocity = look.multiply(3.15);
         gravity = 0.05;
      } else if (item == Items.TRIDENT) {
         if (!using) {
            return null;
         }
         velocity = look.multiply(2.5);
         gravity = 0.05;
      } else if (isThrowableItem(item)) {
         // показываем даже если просто взял в руку
         velocity = look.multiply(1.5);
         gravity = 0.03;
      } else {
         return null;
      }

      return this.simulate(start, velocity, false, drag, gravity, maxSteps, step, shouldShowImpact, true);
   }

   private Vec3d getHandStart(PlayerEntity player) {
      Vec3d eye = player.getEyePos();
      Vec3d look = player.getRotationVec(1.0F).normalize();
      Vec3d right = look.crossProduct(new Vec3d(0.0, 1.0, 0.0));
      if (right.lengthSquared() < 1.0E-4) {
         right = new Vec3d(1.0, 0.0, 0.0);
      } else {
         right = right.normalize();
      }
      Arm arm = player.getMainArm();
      double side = arm == Arm.RIGHT ? 1.0 : -1.0;
      // Старт немного вперед/вниз от глаз, со смещением в сторону рабочей руки.
      return eye.add(look.multiply(0.28)).add(right.multiply(0.20 * side)).add(0.0, -0.16, 0.0);
   }

   private boolean isThrowableItem(Item item) {
      return item == Items.ENDER_PEARL
            || item == Items.SNOWBALL
            || item == Items.EGG
            || item == Items.EXPERIENCE_BOTTLE
            || item == Items.SPLASH_POTION
            || item == Items.LINGERING_POTION
            || item == Items.WIND_CHARGE;
   }

   private Trajectory simulate(
         Vec3d start,
         Vec3d initialVelocity,
         boolean hasNoGravity,
         double drag,
         double gravity,
         int maxSteps,
         double step,
         boolean showImpact,
         boolean allowEntityHit
   ) {
      if (mc.world == null) {
         return null;
      }

      List<Vec3d> points = new java.util.ArrayList<>(maxSteps + 1);
      Vec3d position = start;
      Vec3d velocity = initialVelocity;
      BlockPos impactBlock = null;
      Entity hitEntity = null;
      points.add(position);

      for (int i = 0; i < maxSteps; i++) {
         Vec3d next = position.add(velocity.multiply(step));

         if (allowEntityHit) {
            Box searchBox = new Box(position, next).expand(1.0);
            EntityHitResult ehr = ProjectileUtil.getEntityCollision(
                  mc.world,
                  mc.player,
                  position,
                  next,
                  searchBox,
                  e -> e != null && e.isAlive() && !e.isSpectator() && e != mc.player,
                  0.0F
            );
            if (ehr != null && ehr.getEntity() != null) {
               hitEntity = ehr.getEntity();
               points.add(ehr.getPos());
               break;
            }
         }

         HitResult hit = mc.world.raycast(new RaycastContext(
               position,
               next,
               RaycastContext.ShapeType.COLLIDER,
               RaycastContext.FluidHandling.NONE,
               mc.player
         ));

         if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hit;
            impactBlock = bhr.getBlockPos();
            points.add(bhr.getPos());
            break;
         } else {
            points.add(next);
         }

         velocity = velocity.multiply(Math.pow(drag, step));
         if (!hasNoGravity) {
            velocity = velocity.add(0.0, -gravity * step, 0.0);
         }
         position = next;
         if (position.y < mc.world.getBottomY() - 4.0) {
            break;
         }
      }

      return new Trajectory(points, impactBlock, hitEntity, showImpact, allowEntityHit);
   }

   private double resolveGravity(ProjectileEntity projectile) {
      if (projectile instanceof PersistentProjectileEntity || projectile instanceof TridentEntity) {
         return 0.05;
      }

      if (projectile instanceof EnderPearlEntity || projectile instanceof ThrownItemEntity) {
         return 0.03;
      }

      return 0.03;
   }

   private record Trajectory(List<Vec3d> points, BlockPos impactBlock, Entity hitEntity, boolean showImpact, boolean showTargetOutline) {
   }
}
