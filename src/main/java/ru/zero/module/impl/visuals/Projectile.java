package ru.zero.module.impl.visuals;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.LlamaSpitEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.Arm;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
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
   private static final Identifier BLOOM_TEXTURE = Identifier.of("zero", "textures/world/bloom.png");
   private static final double MAX_TRACK_DISTANCE_SQ = 80.0 * 80.0;
   private static final int MAX_WORLD_PROJECTILES_PER_FRAME = 24;
   private static final double GRAVITY_ARROW = 0.05;
   private static final double DRAG_ARROW = 0.99;
   private static final double GRAVITY_THROWN = 0.03;
   private static final double DRAG_THROWN = 0.99;
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
      if (trajectory == null || trajectory.points == null || trajectory.points.isEmpty()) {
         return;
      }

      List<Vec3d> points = trajectory.points;
      int size = points.size();
      float stepT = 1.0F / Math.max(1, size - 1);

      for (int i = 0; i < size - 1; i++) {
         Vec3d p0 = points.get(i);
         Vec3d p1 = points.get(i + 1);
         if (p0 == null || p1 == null) {
            continue;
         }
         int color = ColorUtil.interpolate(startColor, endColor, i * stepT);
         renderer.drawLine(p0, p1, width, color, depthTest);
      }

      this.renderTrajectoryParticles(renderer, points, startColor, endColor, stepT, depthTest);

      if (showImpactBlock.get() && trajectory.impactBlock != null && trajectory.showImpact) {
         BlockOverlay.renderHighlightForBlock(renderer, mc.world, trajectory.impactBlock, mc.player, BlockOverlay.resolveBaseColor());
      }

      if (showTargetOutline.get() && trajectory.hitEntity != null && trajectory.showTargetOutline) {
         this.renderEntityOutline(renderer, trajectory.hitEntity, width, startColor, depthTest);
      }
   }

   private void renderTrajectoryParticles(
         WorldRenderer renderer,
         List<Vec3d> points,
         int startColor,
         int endColor,
         float stepT,
         boolean depthTest
   ) {
      int size = points.size();
      double step = Math.max(0.05, simulationStep.get());
      int stride = Math.max(1, (int) Math.round(0.5 / step));
      Camera camera = renderer.camera();
      Vec3d cameraPos = camera.getCameraPos();

      for (int i = 0; i < size; i += stride) {
         Vec3d point = points.get(i);
         if (point == null) {
            continue;
         }
         float t = i * stepT;
         int color = ColorUtil.interpolate(startColor, endColor, t);
         double halfSize = Math.max(0.05, 0.13 * (1.0 - t * 0.5));
         this.emitBillboard(renderer, point, cameraPos, halfSize, color, depthTest);
      }

      Vec3d head = points.get(0);
      if (head != null) {
         this.emitBillboard(renderer, head, cameraPos, 0.17, startColor, depthTest);
      }

      Vec3d impact = points.get(size - 1);
      if (impact != null) {
         this.emitBillboard(
               renderer,
               impact,
               cameraPos,
               0.23,
               ColorUtil.replAlpha(endColor, Math.min(255, ColorUtil.alpha(endColor) + 40)),
               depthTest
         );
      }
   }

   private void emitBillboard(WorldRenderer renderer, Vec3d pos, Vec3d cameraPos, double halfSize, int color, boolean depthTest) {
      Vec3d toPoint = pos.subtract(cameraPos);
      double dist = toPoint.length();
      Vec3d dir = dist < 1.0E-4 ? new Vec3d(0.0, 0.0, 1.0) : toPoint.multiply(1.0 / dist);
      Vec3d right = dir.crossProduct(new Vec3d(0.0, 1.0, 0.0));
      if (right.lengthSquared() < 1.0E-6) {
         right = new Vec3d(1.0, 0.0, 0.0);
      } else {
         right = right.normalize();
      }
      Vec3d up = right.crossProduct(dir).normalize();
      Vec3d r = right.multiply(halfSize);
      Vec3d u = up.multiply(halfSize);
      Vec3d v0 = pos.subtract(r).add(u);
      Vec3d v1 = pos.add(r).add(u);
      Vec3d v2 = pos.add(r).subtract(u);
      Vec3d v3 = pos.subtract(r).subtract(u);
      renderer.drawTexturedQuad(BLOOM_TEXTURE, v0, v1, v2, v3, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, color, depthTest);
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
      return projectile.isOnGround();
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
      if (projectile instanceof TridentEntity) {
         return projectileTypes.get("Трезубец");
      }

      if (projectile instanceof EnderPearlEntity) {
         return projectileTypes.get("Перл");
      }

      if (projectile instanceof PersistentProjectileEntity) {
         return projectileTypes.get("Стрелы/арбалет");
      }

      if (projectile instanceof ThrownEntity) {
         return projectileTypes.get("Бросаемое");
      }

      return projectileTypes.get("Остальные");
   }

   private Trajectory simulateEntityTrajectory(ProjectileEntity projectile, int maxSteps, double step) {
      Vec3d position = projectile.getLerpedPos(mc.getRenderTickCounter().getTickProgress(true));
      Vec3d velocity = projectile.getVelocity();
      double[] physics = this.resolvePhysics(projectile);
      double gravity = physics[0];
      double drag = projectile.isTouchingWater() && physics[1] < 1.0 ? 0.6 : physics[1];
      return this.simulate(position, velocity, gravity <= 0.0, drag, gravity, maxSteps, step, false, false);
   }

   private double[] resolvePhysics(ProjectileEntity projectile) {
      if (projectile instanceof ExplosiveProjectileEntity
            || projectile instanceof ShulkerBulletEntity
            || projectile instanceof LlamaSpitEntity) {
         return new double[] { 0.0, 1.0 };
      }

      if (projectile instanceof PersistentProjectileEntity) {
         return new double[] { GRAVITY_ARROW, DRAG_ARROW };
      }

      if (projectile instanceof ThrownEntity) {
         return new double[] { GRAVITY_THROWN, DRAG_THROWN };
      }

      return new double[] { projectile.hasNoGravity() ? 0.0 : GRAVITY_ARROW, DRAG_ARROW };
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

      if (item instanceof BowItem) {
         if (!using) {
            return null;
         }
         float pull = BowItem.getPullProgress(stack.getMaxUseTime(player) - player.getItemUseTimeLeft());
         if (pull <= 0.03F) {
            return null;
         }
         velocity = look.multiply(Math.max(0.15F, pull) * 3.0F);
         gravity = GRAVITY_ARROW;
      } else if (item instanceof CrossbowItem) {
         if (!CrossbowItem.isCharged(stack) && !using) {
            return null;
         }
         velocity = look.multiply(3.15);
         gravity = GRAVITY_ARROW;
      } else if (item == Items.TRIDENT) {
         if (!using) {
            return null;
         }
         velocity = look.multiply(2.5);
         gravity = GRAVITY_ARROW;
      } else if (item == Items.ENDER_PEARL || item == Items.SNOWBALL || item == Items.EGG) {
         velocity = look.multiply(1.5);
         gravity = GRAVITY_THROWN;
      } else if (item == Items.EXPERIENCE_BOTTLE || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
         velocity = look.multiply(0.5);
         gravity = GRAVITY_THROWN;
      } else if (item == Items.WIND_CHARGE) {
         velocity = look.multiply(1.5);
         gravity = 0.0;
         drag = 1.0;
      } else {
         return null;
      }

      return this.simulate(start, velocity, gravity <= 0.0, drag, gravity, maxSteps, step, true, true);
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
      return eye.add(look.multiply(0.28)).add(right.multiply(0.20 * side)).add(0.0, -0.16, 0.0);
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

   private record Trajectory(List<Vec3d> points, BlockPos impactBlock, Entity hitEntity, boolean showImpact, boolean showTargetOutline) {
   }
}
