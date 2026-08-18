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
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import org.joml.Matrix4f;
import ru.zero.event.EventInit;
import ru.zero.event.player.EventMotion;
import ru.zero.event.render.WorldRenderEvent;
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
   private final Matrix4f baseMat = new Matrix4f();
   private final Matrix4f cubeMat = new Matrix4f();
   private final Matrix4f glowMat = new Matrix4f();
   private static final float DEG_TO_RAD = (float) Math.PI / 180.0F;
   private static final Identifier GLOW_TEXTURE_C = Identifier.of("zero", "textures/world/dashbloom.png");
   private static final Identifier GLOW_TEXTURE_G = Identifier.of("zero", "textures/world/dashbloomsample.png");
   public static SliderSetting cube = new SliderSetting("Кол кубиков", 100.0F, 50.0F, 300.0F, 1.0F, false);
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
   public void onRender3D(WorldRenderEvent e) {
      if (this.particles.isEmpty()) {
         return;
      }
      Immediate immediate = e.worldRenderer().bufferSource();
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      long now = System.currentTimeMillis();
      float cameraYaw = mc.gameRenderer.getCamera().getYaw();
      float cameraPitch = mc.gameRenderer.getCamera().getPitch();
      float rotation = (float) (now % 9000L) / 9000.0F * 360.0F;
      int baseColor = ColorUtil.fade();
      this.baseMat.set(e.matrixStack().peek().getPositionMatrix());

      Iterator<Svetych.Particle> iterator = this.particles.iterator();
      while (iterator.hasNext()) {
         Svetych.Particle p = iterator.next();
         p.update(now);
         if (p.shouldRemove()) {
            iterator.remove();
         } else {
            p.prepareRender(baseColor, cameraPos, rotation);
         }
      }

      if (this.particles.isEmpty()) {
         return;
      }

      VertexConsumer cubeBuffer = immediate.getBuffer(Svetych.COLOR_QUADS_LAYER);
      for (Svetych.Particle p : this.particles) {
         p.renderCubePass(cubeBuffer, this.baseMat, this.cubeMat);
      }

      VertexConsumer lineBuffer = immediate.getBuffer(Svetych.COLOR_LINES_LAYER);
      for (Svetych.Particle p : this.particles) {
         p.renderLinesPass(lineBuffer, this.baseMat, this.cubeMat);
      }

      VertexConsumer glowCBuffer = immediate.getBuffer(Svetych.GLOW_LAYER);
      for (Svetych.Particle p : this.particles) {
         p.renderGlowPass(glowCBuffer, this.baseMat, this.glowMat, cameraYaw, cameraPitch, false);
      }

      VertexConsumer glowGBuffer = immediate.getBuffer(Svetych.GLOW_LAYER_G);
      for (Svetych.Particle p : this.particles) {
         p.renderGlowPass(glowGBuffer, this.baseMat, this.glowMat, cameraYaw, cameraPitch, true);
      }
   }

   private static void drawCube(VertexConsumer b, Matrix4f m, int color, float s) {
      float h = s / 2.0F;
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
      float h = s / 2.0F;
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
      private double prevX;
      private double prevY;
      private double prevZ;
      double mX;
      double mY;
      double mZ;
      long start;
      float phase;
      Animation animation = new EaseInOutQuad(1200, 1.0);
      float cachedAlpha = 1.0F;
      private static final MinecraftClient mc = MinecraftClient.getInstance();
      private static final BlockPos.Mutable MUTABLE_POS = new BlockPos.Mutable();

      float frameAlpha;
      private int frameCubeCol;
      private int frameLineCol;
      private int frameGlowColor;
      private int frameGlowAlpha80;
      private int frameGlowAlpha140;
      private float frameRelX;
      private float frameRelY;
      private float frameRelZ;
      private float frameRotY;
      private float frameRotX;
      private static final float CUBE_SIZE = 0.26F;
      private static final float CUBE_SIZE6 = CUBE_SIZE * 6.0F;
      private static final float CUBE_SIZE2 = CUBE_SIZE * 2.0F;

      public Particle(Vec3d pos, float h) {
         this.start = System.currentTimeMillis();
         this.phase = (float) (Math.random() * 100.0);
         double radius = 2.0 + Math.random() * 3.0;
         double angle = Math.random() * Math.PI * 2.0;
         this.x = pos.x + Math.cos(angle) * radius;
         this.z = pos.z + Math.sin(angle) * radius;
         this.y = pos.y + 2.0 + Math.random() * (h + 2.0);
         this.prevX = this.x;
         this.prevY = this.y;
         this.prevZ = this.z;
         this.mX = (Math.random() - 0.5) * 0.06;
         this.mY = (Math.random() - 0.5) * 0.06;
         this.mZ = (Math.random() - 0.5) * 0.06;
         this.animation.setDirection(Direction.FORWARDS);
      }

      public void update(long now) {
         if (mc.world == null) {
            return;
         }
         this.prevX = this.x;
         this.prevY = this.y;
         this.prevZ = this.z;

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

         this.cachedAlpha = this.animation.getOutput();
      }

      public float getAlpha() {
         return this.cachedAlpha;
      }

      private boolean isHit(double px, double py, double pz) {
         MUTABLE_POS.set((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz));
         return mc.world.getBlockState(MUTABLE_POS).isFullCube(mc.world, MUTABLE_POS);
      }

      public boolean shouldRemove() {
         return this.animation.getDirection() == Direction.BACKWARDS && this.cachedAlpha <= 0.0F;
      }

      public void prepareRender(int baseColor, Vec3d cameraPos, float rotation) {
         float alpha = this.cachedAlpha;
         if (alpha <= 0.0F) {
            this.frameAlpha = 0.0F;
            return;
         }
         this.frameAlpha = alpha;
         float alpha02 = alpha * 0.2F;
         float alpha04 = alpha * 0.4F;
         int baseRGB = baseColor & 0x00FFFFFF;
         this.frameCubeCol = (Math.round(255.0F * alpha02) << 24) | baseRGB;
         this.frameLineCol = (Math.round(255.0F * alpha04) << 24) | baseRGB;
         this.frameGlowColor = baseColor;
         this.frameGlowAlpha80 = (int) (80.0F * alpha);
         this.frameGlowAlpha140 = (int) (140.0F * alpha);
         this.frameRelX = (float) (this.x - cameraPos.x);
         this.frameRelY = (float) (this.y - cameraPos.y);
         this.frameRelZ = (float) (this.z - cameraPos.z);
         this.frameRotY = rotation + this.phase;
         this.frameRotX = rotation * 0.5F;
      }

      public void renderCubePass(VertexConsumer buffer, Matrix4f baseMat, Matrix4f cubeMat) {
         if (this.frameAlpha <= 0.0F) {
            return;
         }
         cubeMat.set(baseMat)
               .translate(this.frameRelX, this.frameRelY, this.frameRelZ)
               .rotateY(this.frameRotY * Svetych.DEG_TO_RAD)
               .rotateX(this.frameRotX * Svetych.DEG_TO_RAD);
         Svetych.drawCube(buffer, cubeMat, this.frameCubeCol, CUBE_SIZE);
      }

      public void renderLinesPass(VertexConsumer buffer, Matrix4f baseMat, Matrix4f cubeMat) {
         if (this.frameAlpha <= 0.0F) {
            return;
         }
         cubeMat.set(baseMat)
               .translate(this.frameRelX, this.frameRelY, this.frameRelZ)
               .rotateY(this.frameRotY * Svetych.DEG_TO_RAD)
               .rotateX(this.frameRotX * Svetych.DEG_TO_RAD);
         Svetych.drawLines(buffer, cubeMat, this.frameLineCol, CUBE_SIZE);
      }

      public void renderGlowPass(VertexConsumer buffer, Matrix4f baseMat, Matrix4f glowMat, float cameraYaw,
            float cameraPitch, boolean glowG) {
         if (this.frameAlpha <= 0.0F) {
            return;
         }
         glowMat.set(baseMat)
               .translate(this.frameRelX, this.frameRelY, this.frameRelZ)
               .rotateY(-cameraYaw * Svetych.DEG_TO_RAD)
               .rotateX(cameraPitch * Svetych.DEG_TO_RAD);
         if (glowG) {
            WorldRenderUtil.drawGlow(buffer, glowMat, this.frameGlowColor, this.frameGlowAlpha140, CUBE_SIZE2);
         } else {
            WorldRenderUtil.drawGlow(buffer, glowMat, this.frameGlowColor, this.frameGlowAlpha80, CUBE_SIZE6);
         }
      }
   }
}
