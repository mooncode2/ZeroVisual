package ru.zero.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import org.joml.Matrix4f;
import ru.zero.event.EventInit;
import ru.zero.event.player.EventMotion;
import ru.zero.event.render.EventRender3D;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.other.TimerUtil;
import ru.zero.util.render.math.animation.Animation;
import ru.zero.util.render.math.animation.Direction;
import ru.zero.util.render.math.animation.impl.EaseInOutQuad;
import ru.zero.util.render.world.WorldRenderUtil;

@IModule(name = "Svetych", description = "Floating cubes with physics and outlines", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class Svetych extends Module {
   private final List<Svetych.Particle> particles = new ArrayList<>();
   private final TimerUtil.satosTime timer = new TimerUtil.satosTime();
   private static final Identifier GLOW_TEXTURE_C = Identifier.of("zero", "textures/world/dashbloom.png");
   private static final Identifier GLOW_TEXTURE_G = Identifier.of("zero", "textures/world/dashbloomsample.png");
   public static SliderSetting cube = new SliderSetting("Кол кубиков", 100.0F, 50.0F, 300.0F, 1.0F, false);
   private static final float CUBE_SIZE = 0.3F;
   private static final float CUBE_HALF = 0.15F;
   private static final RenderPipeline COLOR_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
               .withLocation(Identifier.of("zero", "svetych_phys_color"))
               .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final RenderLayer COLOR_QUADS_LAYER = RenderLayer.of("svetych_phys_cube", RenderSetup.builder(COLOR_PIPELINE).expectedBufferSize(1024).translucent().build());
   private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
               .withLocation(Identifier.of("zero", "svetych_lines"))
               .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final RenderLayer COLOR_LINES_LAYER = RenderLayer.of("svetych_lines", RenderSetup.builder(LINES_PIPELINE).expectedBufferSize(1024).translucent().build());
   private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_TEX_COLOR_SNIPPET })
               .withLocation(Identifier.of("zero", "svetych_phys_glow"))
               .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final RenderLayer GLOW_LAYER = RenderLayer.of("svetych_phys_glow", RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", GLOW_TEXTURE_C).build());
   private static final RenderLayer GLOW_LAYER_G = RenderLayer.of("svetych_phys_glow_g", RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", GLOW_TEXTURE_G).build());

   public Svetych() {
      this.addSettings(new Setting[] { cube });
   }

   @EventInit
   public void onUpdate(EventMotion e) {
      if (mc.player != null && mc.world != null) {
         if (this.particles.size() < cube.get() && this.timer.hasReached(200L)) {
            this.particles.add(new Svetych.Particle(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), mc.player.getHeight()));
            this.timer.reset();
         }
      }
   }

   @EventInit
   public void onRender3D(EventRender3D e) {
      if (!this.particles.isEmpty()) {
         Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
         MatrixStack matrices = e.getMatrixStack();
         Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
         long now = System.currentTimeMillis();
         float cameraYaw = mc.gameRenderer.getCamera().getYaw();
         float cameraPitch = mc.gameRenderer.getCamera().getPitch();
         float rotation = (float) (now % 9000L) / 9000.0F * 360.0F;
         int baseColor = ColorUtil.fade();
         Iterator<Svetych.Particle> iterator = this.particles.iterator();

         while (iterator.hasNext()) {
            Svetych.Particle p = iterator.next();
            p.update(now);
            if (p.shouldRemove()) {
               iterator.remove();
            }
         }

         for (Svetych.Particle p : this.particles) {
            p.render(matrices, immediate, cameraPos, now, baseColor, rotation, cameraYaw, cameraPitch);
         }

         immediate.draw();
      }
   }

   private static void drawCube(VertexConsumer b, Matrix4f m, int color, float s) {
      float h = s == 0.3F ? 0.15F : s / 2.0F;
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int bl = color & 0xFF;
      int a = color >> 24 & 0xFF;
      b.vertex(m, -h, h, -h).color(r, g, bl, a);
      b.vertex(m, -h, h, h).color(r, g, bl, a);
      b.vertex(m, h, h, h).color(r, g, bl, a);
      b.vertex(m, h, h, -h).color(r, g, bl, a);
      b.vertex(m, -h, -h, -h).color(r, g, bl, a);
      b.vertex(m, h, -h, -h).color(r, g, bl, a);
      b.vertex(m, h, -h, h).color(r, g, bl, a);
      b.vertex(m, -h, -h, h).color(r, g, bl, a);
      b.vertex(m, -h, h, h).color(r, g, bl, a);
      b.vertex(m, -h, -h, h).color(r, g, bl, a);
      b.vertex(m, h, -h, h).color(r, g, bl, a);
      b.vertex(m, h, h, h).color(r, g, bl, a);
      b.vertex(m, -h, h, -h).color(r, g, bl, a);
      b.vertex(m, h, h, -h).color(r, g, bl, a);
      b.vertex(m, h, -h, -h).color(r, g, bl, a);
      b.vertex(m, -h, -h, -h).color(r, g, bl, a);
      b.vertex(m, -h, h, -h).color(r, g, bl, a);
      b.vertex(m, -h, -h, -h).color(r, g, bl, a);
      b.vertex(m, -h, -h, h).color(r, g, bl, a);
      b.vertex(m, -h, h, h).color(r, g, bl, a);
      b.vertex(m, h, h, -h).color(r, g, bl, a);
      b.vertex(m, h, h, h).color(r, g, bl, a);
      b.vertex(m, h, -h, h).color(r, g, bl, a);
      b.vertex(m, h, -h, -h).color(r, g, bl, a);
   }

   private static void drawLines(VertexConsumer b, Matrix4f m, int c, float s) {
      float h = s == 0.3F ? 0.15F : s / 2.0F;
      int r = c >> 16 & 0xFF;
      int g = c >> 8 & 0xFF;
      int bl = c & 0xFF;
      int a = c >> 24 & 0xFF;
      line(b, m, -h, -h, -h, h, -h, -h, r, g, bl, a);
      line(b, m, h, -h, -h, h, -h, h, r, g, bl, a);
      line(b, m, h, -h, h, -h, -h, h, r, g, bl, a);
      line(b, m, -h, -h, h, -h, -h, -h, r, g, bl, a);
      line(b, m, -h, h, -h, h, h, -h, r, g, bl, a);
      line(b, m, h, h, -h, h, h, h, r, g, bl, a);
      line(b, m, h, h, h, -h, h, h, r, g, bl, a);
      line(b, m, -h, h, h, -h, h, -h, r, g, bl, a);
      line(b, m, -h, -h, -h, -h, h, -h, r, g, bl, a);
      line(b, m, h, -h, -h, h, h, -h, r, g, bl, a);
      line(b, m, h, -h, h, h, h, h, r, g, bl, a);
      line(b, m, -h, -h, h, -h, h, h, r, g, bl, a);
   }

   private static void line(VertexConsumer b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
         int r, int g, int bl, int a) {
      b.vertex(m, x1, y1, z1).color(r, g, bl, a);
      b.vertex(m, x2, y2, z2).color(r, g, bl, a);
   }

   @Environment(EnvType.CLIENT)
   public static class Particle {
      double x;
      double y;
      double z;
      double mX;
      double mY;
      double mZ;
      long start;
      float phase;
      Animation animation = new EaseInOutQuad(1200, 1.0);
      float cachedAlpha = 1.0F;
      long lastAlphaUpdate = 0L;
      private static final MinecraftClient mc = MinecraftClient.getInstance();
      private static final long ALPHA_CACHE_DURATION = 16L;

      public Particle(Vec3d pos, float h) {
         this.start = System.currentTimeMillis();
         this.phase = (float) (Math.random() * 100.0);
         double radius = 2.0 + Math.random() * 3.0;
         double angle = Math.random() * Math.PI * 2.0;
         this.x = pos.x + Math.cos(angle) * radius;
         this.z = pos.z + Math.sin(angle) * radius;
         this.y = pos.y + 2.0 + Math.random() * (h + 2.0);
         this.mX = (Math.random() - 0.5) * 0.06;
         this.mY = (Math.random() - 0.5) * 0.06;
         this.mZ = (Math.random() - 0.5) * 0.06;
         this.animation.setDirection(Direction.FORWARDS);
      }

      public void update(long now) {
         if (mc.world != null) {
            double velMagSq = this.mX * this.mX + this.mY * this.mY + this.mZ * this.mZ;
            if (velMagSq > 1.0E-4) {
               if (this.isHit(this.x + this.mX, this.y, this.z)) {
                  this.mX *= -0.8;
               } else {
                  this.x = this.x + this.mX;
               }

               if (this.isHit(this.x, this.y + this.mY, this.z)) {
                  this.mY *= -0.8;
               } else {
                  this.y = this.y + this.mY;
               }

               if (this.isHit(this.x, this.y, this.z + this.mZ)) {
                  this.mZ *= -0.8;
               } else {
                  this.z = this.z + this.mZ;
               }
            } else {
               this.x = this.x + this.mX;
               this.y = this.y + this.mY;
               this.z = this.z + this.mZ;
            }

            this.mX *= 0.99;
            this.mY *= 0.99;
            this.mZ *= 0.99;
            if (this.animation.getDirection() != Direction.BACKWARDS && now - this.start > 7000L) {
               this.animation.setDirection(Direction.BACKWARDS);
            }

            if (now - this.lastAlphaUpdate > 16L) {
               this.cachedAlpha = this.animation.getOutput();
               this.lastAlphaUpdate = now;
            }
         }
      }

      public float getAlpha() {
         return this.cachedAlpha;
      }

      private boolean isHit(double px, double py, double pz) {
         BlockPos pos = BlockPos.ofFloored(px, py, pz);
         return mc.world.getBlockState(pos).isFullCube(mc.world, pos);
      }

      public boolean shouldRemove() {
         return this.animation.getDirection() == Direction.BACKWARDS && this.cachedAlpha <= 0.0F;
      }

      public void render(
            MatrixStack matrices, VertexConsumerProvider immediate, Vec3d cameraPos, long time, int baseColor,
            float rotation, float cameraYaw, float cameraPitch) {
         float alpha = this.getAlpha();
         if (!(alpha <= 0.0F)) {
            float alpha02 = alpha * 0.2F;
            float alpha04 = alpha * 0.4F;
            int glowCol = ColorUtil.multAlpha(baseColor, alpha);
            float cubeSize = 0.26F;
            float cubeSize6 = cubeSize * 6.0F;
            float cubeSize2 = cubeSize * 2.0F;
            float relX = (float) (this.x - cameraPos.x);
            float relY = (float) (this.y - cameraPos.y);
            float relZ = (float) (this.z - cameraPos.z);
            float rotY = rotation + this.phase;
            float rotX = rotation * 0.5F;
            matrices.push();
            matrices.translate(relX, relY, relZ);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
            Matrix4f mat = matrices.peek().getPositionMatrix();
            Svetych.drawCube(immediate.getBuffer(Svetych.COLOR_QUADS_LAYER), mat,
                  ColorUtil.multAlpha(baseColor, alpha02), cubeSize);
            Svetych.drawLines(immediate.getBuffer(Svetych.COLOR_LINES_LAYER), mat,
                  ColorUtil.multAlpha(baseColor, alpha04), cubeSize);
            matrices.pop();
            matrices.push();
            matrices.translate(relX, relY, relZ);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
            Matrix4f gMat = matrices.peek().getPositionMatrix();
            WorldRenderUtil.drawGlow(immediate.getBuffer(Svetych.GLOW_LAYER), gMat, glowCol, (int) (80.0F * alpha),
                  cubeSize6);
            WorldRenderUtil.drawGlow(immediate.getBuffer(Svetych.GLOW_LAYER_G), gMat, glowCol, (int) (140.0F * alpha),
                  cubeSize2);
            matrices.pop();
         }
      }
   }
}
