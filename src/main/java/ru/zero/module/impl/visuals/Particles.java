package ru.zero.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import ru.zero.util.render.world.WorldRenderer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventChangeWorld;
import ru.zero.event.player.AttackEvent;
import ru.zero.event.player.EventMotion;
import ru.zero.event.render.WorldRenderEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.module.api.setting.impl.MultiBooleanSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.other.Mathf;
import ru.zero.util.other.StopWatch;
import ru.zero.util.player.PlayerUtil;
import ru.zero.util.render.animation.util.Animation;
import ru.zero.util.render.animation.util.Easings;
import ru.zero.util.render.core.Renderer2D;

@IModule(name = "Particles", description = " ", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class Particles extends Module {
   public static MultiBooleanSetting events = new MultiBooleanSetting(
         "Спавнить при", new BooleanSetting("Атаке", true), new BooleanSetting("Бросок", true),
         new BooleanSetting("В мире", false));
   public static ModeSetting particleMode = new ModeSetting(
         "Тип частиц", "Bloom", "Bloom", "Star", "Snow", "Heart", "Dollar", "Triangle", "Sakura", "Genshin", "Rhombus");
   public static SliderSetting size = new SliderSetting("Размер", 0.5F, 0.0F, 1.0F, 0.1F, false);
   private static final int QUAD_BUFFER_SIZE_BYTES = 1024;
   private long lastUpdateTime = System.nanoTime();
   private static final String PIPELINE_NAMESPACE = "zero";
   private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_TEX_COLOR_SNIPPET })
               .withLocation(Identifier.of("zero", "pipeline/world/textured_quads"))
               .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final Map<Particles.ParticleType, RenderLayer> RENDER_LAYER_CACHE = new ConcurrentHashMap<>();
   private static final int MAX_PARTICLES_PER_LIST = 350;
   private static final int MAX_THROW_PARTICLES_PER_TICK = 32;
   private int throwParticleBudget;
   private int worldParticleCooldown;
   private static final Random PARTICLE_RANDOM = Random.create();
   private final List<Particles.Particle> targetParticles = new ArrayList<>();
   private final List<Particles.Particle> worldParticles = new ArrayList<>();
   private final List<Particles.Particle> flameParticles = new ArrayList<>();
   private static final Vector3f REUSABLE_NORMAL = new Vector3f(0.0F, 0.0F, 1.0F);

   public Particles() {
      this.addSettings(new Setting[] { events, particleMode, size });
   }

   private void clear() {
      this.targetParticles.clear();
      this.flameParticles.clear();
      this.worldParticles.clear();
   }

   private void spawnParticle(List<Particles.Particle> particles, Vec3d position, Vec3d velocity) {
      if (particles.size() >= MAX_PARTICLES_PER_LIST) {
         particles.remove(0);
      }

      float particleSize = 0.05F + size.get() * 0.2F;
      int color = ColorUtil.fade(particles.size() * 100);
      String var7 = particleMode.get();

      Particles.ParticleType type = switch (var7) {
         case "Heart" -> Particles.ParticleType.HEART;
         case "Star" -> Particles.ParticleType.STAR;
         case "Snow" -> Particles.ParticleType.SNOW;
         case "Bloom" -> Particles.ParticleType.BLOOM;
         case "Dollar" -> Particles.ParticleType.DOLLAR;
         case "Triangle" -> Particles.ParticleType.TRIANGLE;
         case "Sakura" -> Particles.ParticleType.SAKURA;
         case "Genshin" -> Particles.ParticleType.GEMINI;
         case "Rhombus" -> Particles.ParticleType.SIMS;
         default -> Particles.ParticleType.BLOOM;
      };
      particles.add(
            new Particles.Particle(
                  type,
                  position.add(0.0, particleSize, 0.0),
                  velocity,
                  particles.size(),
                  (int) Mathf.step(Mathf.randomValue(0.0F, 360.0F), 15.0),
                  color,
                  particleSize,
                  0.2F));
   }

   @EventInit
   public void onEvent(AttackEvent event) {
      Entity target = event.getTarget();
      float motion = 6.0F;
      if (events.get("Атаке")) {
         for (int i = 0; i < 35; i++) {
            this.spawnParticle(
                  this.targetParticles,
                  new Vec3d(target.getX(), target.getY() + Mathf.randomValue(0.0F, target.getHeight()), target.getZ()),
                  new Vec3d(Mathf.randomValue(-motion, motion), Mathf.randomValue(-motion, motion),
                        Mathf.randomValue(-motion, motion)));
         }
      }
   }

   @EventInit
   public void onEvent(EventMotion event) {
      this.throwParticleBudget = MAX_THROW_PARTICLES_PER_TICK;
      if (events.get("Бросок")) {
         if (mc.world == null || mc.player == null) {
            return;
         }

         double radiusSq = 64.0;
         Box searchBox = mc.player.getBoundingBox().expand(radiusSq);
         for (Entity entity : mc.world.getOtherEntities(mc.player, searchBox)) {
            if (this.throwParticleBudget <= 0) {
               break;
            }

            if ((entity instanceof EnderPearlEntity || entity instanceof ArrowEntity || entity instanceof TridentEntity)
                  && (!(entity instanceof TridentEntity trident) || !trident.isOnGround())) {
               boolean isMoving = entity.lastX != entity.getX()
                     || entity.lastY != entity.getY()
                     || entity.lastZ != entity.getZ();
               if (isMoving) {
                  Vec3d pos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());

                  for (int i = 0; i < 3 && this.throwParticleBudget > 0; i++) {
                     this.spawnParticle(
                           this.flameParticles,
                           new Vec3d(
                                 pos.x + MathHelper.nextDouble(PARTICLE_RANDOM, -0.2, 0.2),
                                 pos.y + MathHelper.nextDouble(PARTICLE_RANDOM, -0.2, 0.2),
                                 pos.z + MathHelper.nextDouble(PARTICLE_RANDOM, -0.2, 0.2)),
                           new Vec3d(
                                 MathHelper.nextDouble(PARTICLE_RANDOM, -1.0, 1.0),
                                 MathHelper.nextDouble(PARTICLE_RANDOM, -0.3, 0.3),
                                 MathHelper.nextDouble(PARTICLE_RANDOM, -1.0, 1.0)));
                     this.throwParticleBudget--;
                  }
               }
            }
         }
      }

      if (events.get("В мире")) {
         if (mc.world == null || mc.player == null) {
            return;
         }

         if (++this.worldParticleCooldown < 3) {
            return;
         }
         this.worldParticleCooldown = 0;

         int r = 12;

         for (int i = 0; i < 4; i++) {
            Vec3d additional = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                  .add(Mathf.randomValue((float) (-r), (float) r), 0.0, Mathf.randomValue((float) (-r), (float) r));
            BlockPos topPos = mc.world.getTopPosition(Type.MOTION_BLOCKING, BlockPos.ofFloored(additional));
            double x = topPos.getX() + Mathf.randomValue(0.0F, 1.0F);
            double z = topPos.getZ() + Mathf.randomValue(0.0F, 1.0F);
            double y = mc.player.getY() + Mathf.randomValue(mc.player.getHeight(), (float) r);
            Vec3d spawnPos = new Vec3d(x, y, z);

            while (!mc.world.isAir(BlockPos.ofFloored(spawnPos)) && spawnPos.y < mc.world.getTopYInclusive()) {
               spawnPos = spawnPos.add(0.0, 1.0, 0.0);
            }

            this.spawnParticle(
                  this.worldParticles,
                  spawnPos,
                  new Vec3d(
                        mc.player.getVelocity().x + Mathf.randomValue(-2.0F, 2.0F),
                        Mathf.randomValue(-0.2, 0.2),
                        mc.player.getVelocity().z + Mathf.randomValue(-2.0F, 2.0F)));
         }
      }

      this.removeExpiredParticles(this.targetParticles, 2000L);
      this.removeExpiredParticles(this.flameParticles, 2000L);
      this.removeExpiredParticles(this.worldParticles, 4000L);
   }

   @EventInit
   public void onWorldRender(WorldRenderEvent event) {
      if (this.targetParticles.isEmpty() && this.flameParticles.isEmpty() && this.worldParticles.isEmpty()) {
         return;
      }

      WorldRenderer worldRenderer = event.worldRenderer();
      MatrixStack matrix = event.matrixStack();
      Immediate immediate = worldRenderer.bufferSource();
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      long now = System.nanoTime();
      double deltaTime = (now - this.lastUpdateTime) / 1.0E9;
      this.lastUpdateTime = now;
      this.renderParticles(matrix, immediate, cameraPos, this.targetParticles, 400L, 600L, deltaTime);
      this.renderParticles(matrix, immediate, cameraPos, this.flameParticles, 700L, 1200L, deltaTime);
      this.renderParticles(matrix, immediate, cameraPos, this.worldParticles, 1500L, 2200L, deltaTime);
   }

   private void removeExpiredParticles(List<Particles.Particle> particles, long lifespan) {
      particles.removeIf(particle -> particle.time().finished(lifespan));
   }

   private void renderParticles(
         MatrixStack matrix, Immediate immediate, Vec3d cameraPos, List<Particles.Particle> particles, long fadeInTime,
         long fadeOutTime, double deltaTime) {
      if (!particles.isEmpty()) {
         matrix.push();

         for (Particles.Particle particle : particles) {
            particle.update(true, deltaTime);
            boolean notFinishedFadeIn = !particle.time().finished(fadeInTime);
            boolean finishedFadeOut = particle.time().finished(fadeOutTime);
            if (notFinishedFadeIn) {
               particle.animation().run(1.0, 0.4, Easings.QUAD_OUT, true);
            } else if (finishedFadeOut) {
               particle.animation().run(0.0, 0.4, Easings.QUAD_OUT, true);
            }

            if (particle.animation.isAlive()) {
               particle.animation.update();
            }

            float animValue = particle.animation.get();
            int alpha = (int) (animValue * 255.0F);
            if (alpha > 0) {
               int color = ColorUtil.replAlpha(particle.color(), alpha);
               Vec3d v = particle.position();
               this.renderParticle(matrix, immediate, particle, (float) v.x, (float) v.y, (float) v.z, particle.size,
                     color, alpha);
            }
         }

         matrix.pop();
      }
   }

   private void renderParticle(MatrixStack matrix, Immediate immediate, Particles.Particle particle, float x, float y,
         float z, float pos, int color, int alpha) {
      matrix.push();
      Renderer2D.setupOrientationMatrix(matrix, x, y, z);
      matrix.multiply(mc.gameRenderer.getCamera().getRotation());
      matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(particle.rotate()));
      RenderLayer renderLayer = RENDER_LAYER_CACHE.computeIfAbsent(
            particle.type(),
            type -> {
               Identifier texture = type.texture();
               return RenderLayer.of(texture.toString(), RenderSetup.builder(TEXTURED_QUADS_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", texture).build());
            });
      Entry entry = matrix.peek();
      Matrix4f matrix4f = entry.getPositionMatrix();
      Matrix3f normalMatrix = entry.getNormalMatrix();
      VertexConsumer buffer = immediate.getBuffer(renderLayer);
      this.drawTexturedQuad(buffer, matrix4f, normalMatrix, -pos, -pos, pos * 2.0F, pos * 2.0F, color, alpha);
      if (particle.type == Particles.ParticleType.BLOOM) {
         this.drawTexturedQuad(buffer, matrix4f, normalMatrix, -pos / 2.0F, -pos / 2.0F, pos, pos, color, alpha);
      }

      matrix.pop();
   }

   private void drawTexturedQuad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix, float x, float y,
         float width, float height, int color, int alpha) {
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      REUSABLE_NORMAL.set(0.0F, 0.0F, 1.0F);
      normalMatrix.transform(REUSABLE_NORMAL);
      REUSABLE_NORMAL.normalize();
      float x2 = x + width;
      float y2 = y + height;
      buffer.vertex(matrix, x, y, 0.0F)
            .color(r, g, b, alpha)
            .texture(0.0F, 1.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
      buffer.vertex(matrix, x2, y, 0.0F)
            .color(r, g, b, alpha)
            .texture(1.0F, 1.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
      buffer.vertex(matrix, x2, y2, 0.0F)
            .color(r, g, b, alpha)
            .texture(1.0F, 0.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
      buffer.vertex(matrix, x, y2, 0.0F)
            .color(r, g, b, alpha)
            .texture(0.0F, 0.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
   }

   @Override
   public void toggle() {
      super.toggle();
      this.clear();
   }

   @EventInit
   public void onEvent(EventChangeWorld event) {
      this.clear();
   }

   @Environment(EnvType.CLIENT)
   public static class Particle {
      private Box box;
      private final Particles.ParticleType type;
      private Vec3d position;
      private Vec3d velocity;
      private final int index;
      private final int rotate;
      private final int color;
      private final float size;
      private static final double BASE_VELOCITY = 0.05;
      private final double speedMultiplier;
      private final StopWatch time = new StopWatch();
      private final Animation animation = new Animation();

      public Particle(Particles.ParticleType type, Vec3d position, Vec3d velocity, int index, int rotate, int color,
            float size, double speedMultiplier) {
         double halfSize = size / 2.0;
         this.box = new Box(
               new Vec3d(position.x - halfSize, position.y - halfSize, position.z - halfSize),
               new Vec3d(position.x + halfSize, position.y + halfSize, position.z + halfSize));
         this.type = type;
         this.position = position;
         this.velocity = velocity.multiply(0.05);
         this.index = index;
         this.rotate = rotate;
         this.color = color;
         this.size = size;
         this.speedMultiplier = speedMultiplier;
         this.time.reset();
      }

      public void update(boolean physic, double deltaTime) {
         if (physic && Module.mc.world != null) {
            double velMagSq = this.velocity.x * this.velocity.x
                  + this.velocity.y * this.velocity.y
                  + this.velocity.z * this.velocity.z;
            if (velMagSq > 1.0E-4) {
               if (PlayerUtil.isBlockSolid(this.position.x, this.position.y, this.position.z + this.velocity.z)) {
                  this.velocity = new Vec3d(this.velocity.x * 1.35F, this.velocity.y * 1.35F, this.velocity.z * -1.1);
               }

               if (PlayerUtil.isBlockSolid(this.position.x, this.position.y + this.velocity.y, this.position.z)) {
                  this.velocity = new Vec3d(this.velocity.x * 1.35F, this.velocity.y * -1.1, this.velocity.z * 1.35F);
               }

               if (PlayerUtil.isBlockSolid(this.position.x + this.velocity.x, this.position.y, this.position.z)) {
                  this.velocity = new Vec3d(this.velocity.x * -1.1, this.velocity.y * 1.35F, this.velocity.z * 1.35F);
               }
            }

            double friction = Math.pow(0.999, deltaTime * 60.0);
            this.velocity = this.velocity.multiply(friction).subtract(0.0, 2.0E-5, 0.0);
         }

         double deltaMultiplier = deltaTime * 60.0 * this.speedMultiplier;
         this.position = new Vec3d(
               this.position.x + this.velocity.x * deltaMultiplier,
               this.position.y + this.velocity.y * deltaMultiplier,
               this.position.z + this.velocity.z * deltaMultiplier);
         double halfSize = this.size / 2.0;
         this.box = new Box(
               new Vec3d(this.position.x - halfSize, this.position.y - halfSize, this.position.z - halfSize),
               new Vec3d(this.position.x + halfSize, this.position.y + halfSize, this.position.z + halfSize));
      }

      @Generated
      public Box box() {
         return this.box;
      }

      @Generated
      public Particles.ParticleType type() {
         return this.type;
      }

      @Generated
      public Vec3d position() {
         return this.position;
      }

      @Generated
      public Vec3d velocity() {
         return this.velocity;
      }

      @Generated
      public int index() {
         return this.index;
      }

      @Generated
      public int rotate() {
         return this.rotate;
      }

      @Generated
      public int color() {
         return this.color;
      }

      @Generated
      public float size() {
         return this.size;
      }

      @Generated
      public double speedMultiplier() {
         return this.speedMultiplier;
      }

      @Generated
      public StopWatch time() {
         return this.time;
      }

      @Generated
      public Animation animation() {
         return this.animation;
      }
   }

   @Environment(EnvType.CLIENT)
   static enum ParticleType {
      HEART("heart", true),
      STAR("star", true),
      SNOW("snowflake", true),
      BLOOM("firefly", true),
      DOLLAR("dollar", true),
      TRIANGLE("triangle", true),
      SAKURA("sakura", true),
      GEMINI("genshin", true),
      SIMS("rhombus", true);

      private final Identifier texture;
      private final boolean rotatable;

      private ParticleType(String name, boolean rotatable) {
         this.texture = Identifier.of("zero", "textures/world/" + name + ".png");
         this.rotatable = rotatable;
      }

      @Generated
      public Identifier texture() {
         return this.texture;
      }

      @Generated
      public boolean rotatable() {
         return this.rotatable;
      }
   }
}
