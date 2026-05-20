package ru.zero.module.impl.visuals;
import net.minecraft.util.math.Vec3d;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BushBlock;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.block.AirBlock;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ButtonBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventChangeWorld;
import ru.zero.event.player.EventMotion;
import ru.zero.event.render.EventRender3D;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.other.TimerUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.animation.AnimationUtils;
import ru.zero.util.render.math.animation.anim2.Animation;
import ru.zero.util.render.math.animation.anim2.Easing;
import ru.zero.util.render.math.animation.anim2.Interpolator;

@IModule(name = "Trails", description = "Render Trails back Player", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class Trails extends Module {
   public static SliderSetting delay = new SliderSetting("Delay", 1500.0F, 500.0F, 5000.0F, 100.0F, false);
   public static SliderSetting size = new SliderSetting("Size", 1.0F, 0.1F, 3.0F, 0.1F, false);
   public static BooleanSetting firstPerson = new BooleanSetting("FirstPerson", false);
   private final List<Trails.Particle3D> particles = new ArrayList<>();
   public AnimationUtils stateAnim = new AnimationUtils(0.0F, 0.0F, 0.1F);
   private static final int QUAD_BUFFER_SIZE_BYTES = 1024;
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
   private static final RenderLayer BLOOM_LAYER = RenderLayer.of(
         "zero/trails/bloom",
         RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
               .expectedBufferSize(QUAD_BUFFER_SIZE_BYTES)
               .translucent()
               .texture("Sampler0", Identifier.of("zero", "textures/world/firefly.png"))
               .build());
   private static final int MAX_TRAIL_PARTICLES = 200;
   private long lastParticleSpawnTime = 0L;
   private static final long PARTICLE_SPAWN_INTERVAL_MS = 20L;
   private static final float FIXED_TIMESTEP = 0.004166667F;
   private static final long FIXED_TIMESTEP_NS = 4166666L;
   private double lastPlayerX = 0.0;
   private double lastPlayerY = 0.0;
   private double lastPlayerZ = 0.0;
   private long lastParticleUpdateTimeNanos = 0L;

   public Trails() {
      this.addSettings(new Setting[] { delay, size, firstPerson });
   }

   @Override
   public void onEnable() {
      this.stateAnim.to = 1.0F;
      this.particles.clear();
      this.lastParticleSpawnTime = System.currentTimeMillis();
      this.lastParticleUpdateTimeNanos = System.nanoTime();
      super.onEnable();
   }

   @Override
   public void onDisable() {
      this.stateAnim.to = 0.0F;
      super.onDisable();
   }

   @EventInit
   public void onEvent(EventMotion event) {
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         double currentX = player.getX();
         double currentY = player.getY();
         double currentZ = player.getZ();
         double dx = Math.abs(currentX - this.lastPlayerX);
         double dy = Math.abs(currentY - this.lastPlayerY);
         double dz = Math.abs(currentZ - this.lastPlayerZ);
         double threshold = 5.0E-5F;
         this.lastPlayerX = currentX;
         this.lastPlayerY = currentY;
         this.lastPlayerZ = currentZ;
         long currentTime = System.currentTimeMillis();
         if ((dx > threshold || dy > threshold || dz > threshold) && currentTime - this.lastParticleSpawnTime >= 20L) {
            double distance = -(player.getWidth() / 2.0F);
            double yawRad = Math.toRadians(mc.player.renderYaw);
            double xOffset = -Math.sin(yawRad) * distance;
            double zOffset = Math.cos(yawRad) * distance;
            if (this.particles.size() < MAX_TRAIL_PARTICLES) {
               this.particles
                     .add(
                           new Trails.Particle3D(
                                 new Vector3d(currentX + xOffset, currentY + player.getHeight() * 0.4F,
                                       currentZ + zOffset),
                                 new Vector3d(player.getVelocity().x, 0.0, player.getVelocity().z)
                                       .mul(1.5 + Math.random()),
                                 this.particles.size()));
            }
            this.lastParticleSpawnTime = currentTime;
         }

         this.particles.removeIf(particle -> particle.getTimerUtil().hasReached((long) delay.get()));
         this.particles
               .removeIf(particle -> particle.position.distance(player.getX(), player.getY(), player.getZ()) >= 100.0);
      }
   }

   @EventInit
   public void onChange(EventChangeWorld e) {
      this.particles.clear();
   }

   @EventInit
   public void onEvent(EventRender3D event) {
      if (!this.particles.isEmpty()) {
         MatrixStack matrix = event.getMatrixStack();
         long currentTimeNanos = System.nanoTime();
         if (this.lastParticleUpdateTimeNanos == 0L) {
            this.lastParticleUpdateTimeNanos = currentTimeNanos;
         }

         long deltaTimeNanos = currentTimeNanos - this.lastParticleUpdateTimeNanos;
         int updateCount = (int) (deltaTimeNanos / 4166666L);
         if (updateCount > 0) {
            updateCount = Math.min(updateCount, 50);

            for (int i = 0; i < updateCount; i++) {
               this.updateParticles(0.004166667F);
            }

            this.lastParticleUpdateTimeNanos = currentTimeNanos - deltaTimeNanos % 4166666L;
         }

         BufferAllocator allocator = new BufferAllocator(262144);
         try {
            if (mc.options.getPerspective() != Perspective.FIRST_PERSON || firstPerson.get()) {
               Immediate immediate = VertexConsumerProvider.immediate(allocator);
               float pos = size.get();

               for (Trails.Particle3D particle : this.particles) {
                  if ((int) particle.getAnimation().getValue() != 200 && !particle.getTimerUtil().hasReached(400.0)) {
                     particle.getAnimation().run(180.0);
                  }

                  if ((int) particle.getAnimation().getValue() != 0
                        && particle.getTimerUtil().hasReached((long) (delay.get() - 400.0F))) {
                     particle.getAnimation().run(0.0);
                  }

                  int[] color = Renderer2D.ColorUtil.getClientColor(10, (int) particle.getAnimation().getValue());
                  int color1 = color[0];
                  int color2 = color[1];
                  int color3 = color[2];
                  int color4 = color[3];
                  Trails.Particle3D prevParticle = null;
                  int index = this.particles.indexOf(particle);
                  if (index > 0) {
                     prevParticle = this.particles.get(index - 1);
                  }

                  Vector3d prevPosition = prevParticle != null ? prevParticle.position : new Vector3d(particle.position.x, particle.position.y, particle.position.z);
                  Vector3d vec = new Vector3d(particle.position.x, particle.position.y, particle.position.z);
                  float x = (float) vec.x;
                  float y = (float) vec.y;
                  float z = (float) vec.z;
                  matrix.push();
                  Renderer2D.setupOrientationMatrix(matrix, x, y, z);
                  matrix.multiply(mc.getEntityRenderDispatcher().camera.getRotation());
                  matrix.translate(0.0F, pos / 2.0F, 0.0F);
                  float bloomSize = (float) clamp(0.5, 4.0, particle.position.distance(prevPosition) * 6.0);
                  Entry entry = matrix.peek();
                  Matrix4f matrix4f = entry.getPositionMatrix();
                  Matrix3f normalMatrix = entry.getNormalMatrix();
                  VertexConsumer buffer = immediate.getBuffer(BLOOM_LAYER);
                  this.drawTexturedQuad(buffer, matrix4f, normalMatrix, -pos + pos / 2.0F, -pos, pos, pos, color1,
                        color2, color3, color4);
                  matrix.pop();
               }

               immediate.draw();
            }
         } finally {
            allocator.close();
         }
      }
   }

   private void updateParticles(float deltaTime) {
      this.particles.forEach(particle -> particle.update(deltaTime));
      float smooth = 0.05F;

      for (int i = 1; i < this.particles.size(); i++) {
         Trails.Particle3D prevParticle = this.particles.get(i - 1);
         Trails.Particle3D currParticle = this.particles.get(i);
         Vector3d prevPos = prevParticle.position;
         Vector3d currPos = currParticle.position;
         prevPos.set(
               Interpolator.lerp(prevPos.x, currPos.x, smooth), Interpolator.lerp(prevPos.y, currPos.y, smooth),
               Interpolator.lerp(prevPos.z, currPos.z, smooth));
      }
   }

   private void drawTexturedQuad(
         VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix, float x, float y, float width, float height,
         int color1, int color2, int color3, int color4) {
      Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F);
      normalMatrix.transform(normal);
      normal.normalize();
      float x2 = x + width;
      float y2 = y + height;
      int r1 = color1 >> 16 & 0xFF;
      int g1 = color1 >> 8 & 0xFF;
      int b1 = color1 & 0xFF;
      int a1 = color1 >> 24 & 0xFF;
      int r2 = color2 >> 16 & 0xFF;
      int g2 = color2 >> 8 & 0xFF;
      int b2 = color2 & 0xFF;
      int a2 = color2 >> 24 & 0xFF;
      int r3 = color3 >> 16 & 0xFF;
      int g3 = color3 >> 8 & 0xFF;
      int b3 = color3 & 0xFF;
      int a3 = color3 >> 24 & 0xFF;
      int r4 = color4 >> 16 & 0xFF;
      int g4 = color4 >> 8 & 0xFF;
      int b4 = color4 & 0xFF;
      int a4 = color4 >> 24 & 0xFF;
      buffer.vertex(matrix, x, y, 0.0F)
            .color(r1, g1, b1, a1)
            .texture(0.0F, 1.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(normal.x, normal.y, normal.z);
      buffer.vertex(matrix, x2, y, 0.0F)
            .color(r2, g2, b2, a2)
            .texture(1.0F, 1.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(normal.x, normal.y, normal.z);
      buffer.vertex(matrix, x2, y2, 0.0F)
            .color(r3, g3, b3, a3)
            .texture(1.0F, 0.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(normal.x, normal.y, normal.z);
      buffer.vertex(matrix, x, y2, 0.0F)
            .color(r4, g4, b4, a4)
            .texture(0.0F, 0.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(normal.x, normal.y, normal.z);
   }

   public static double clamp(double min, double max, double n) {
      return Math.max(min, Math.min(max, n));
   }

   @Environment(EnvType.CLIENT)
   private static class Particle3D {
      private final int index;
      private final TimerUtil timerUtil = new TimerUtil();
      private final Animation animation = new Animation(Easing.LINEAR, 250L);
      public final Vector3d position;
      private final Vector3d delta;

      public Particle3D(Vector3d position, int index) {
         this.position = position;
         this.delta = new Vector3d((Math.random() * 0.5 - 0.25) * 0.01, Math.random() * 0.25 * 0.01,
               (Math.random() * 0.5 - 0.25) * 0.01);
         this.index = index;
         this.timerUtil.reset();
      }

      public Particle3D(Vector3d position, Vector3d velocity, int index) {
         this.position = position;
         this.delta = new Vector3d(velocity.x * 0.01, velocity.y * 0.01, velocity.z * 0.01);
         this.index = index;
         this.timerUtil.reset();
      }

      public void update(float deltaTimeSeconds) {
         double moveX = this.delta.x * 20.0 * deltaTimeSeconds;
         double moveY = this.delta.y * 20.0 * deltaTimeSeconds;
         double moveZ = this.delta.z * 20.0 * deltaTimeSeconds;
         Block block1 = getBlock(this.position.x, this.position.y, this.position.z + moveZ);
         if (this.isValidBlock(block1)) {
            this.delta.z *= -0.8;
         }

         Block block2 = getBlock(this.position.x, this.position.y + moveY, this.position.z);
         if (this.isValidBlock(block2)) {
            this.delta.x *= 0.999F;
            this.delta.z *= 0.999F;
            this.delta.y *= -0.7;
         }

         Block block3 = getBlock(this.position.x + moveX, this.position.y, this.position.z);
         if (this.isValidBlock(block3)) {
            this.delta.x *= -0.8;
         }

         this.updateWithoutPhysics(deltaTimeSeconds);
      }

      public static Block getBlock(double x, double y, double z) {
         return MinecraftClient.getInstance().world.getBlockState(new BlockPos((int) x, (int) y, (int) z)).getBlock();
      }

      private boolean isValidBlock(Block block) {
         return !(block instanceof AirBlock)
               && !(block instanceof BushBlock)
               && !(block instanceof ButtonBlock)
               && !(block instanceof TorchBlock)
               && !(block instanceof LeverBlock)
               && !(block instanceof AbstractPressurePlateBlock)
               && !(block instanceof CarpetBlock)
               && !(block instanceof FluidBlock);
      }

      public void updateWithoutPhysics(float deltaTimeSeconds) {
         this.position.x = this.position.x + this.delta.x * 20.0 * deltaTimeSeconds;
         this.position.y = this.position.y + this.delta.y * 20.0 * deltaTimeSeconds;
         this.position.z = this.position.z + this.delta.z * 20.0 * deltaTimeSeconds;
         double frictionFactor = Math.pow(0.999999, deltaTimeSeconds * 20.0);
         this.delta.x *= frictionFactor;
         this.delta.y = 0.0;
         this.delta.z *= frictionFactor;
      }

      public int getIndex() {
         return this.index;
      }

      public TimerUtil getTimerUtil() {
         return this.timerUtil;
      }

      public Animation getAnimation() {
         return this.animation;
      }

      public Vector3d getPosition() {
         return this.position;
      }

      public Vector3d getDelta() {
         return this.delta;
      }
   }
}
