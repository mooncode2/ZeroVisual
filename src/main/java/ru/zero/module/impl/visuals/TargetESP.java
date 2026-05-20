package ru.zero.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.Iterator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import org.joml.Matrix4f;
import ru.zero.event.EventInit;
import ru.zero.event.render.EventRender3D;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.player.CrosshairTargetUtil;
import ru.zero.util.other.Mathf;
import ru.zero.util.render.math.MathHelper;
import ru.zero.util.render.math.animation.Animation;
import ru.zero.util.render.math.animation.Direction;
import ru.zero.util.render.math.animation.anim.util.Animation2;
import ru.zero.util.render.math.animation.anim.util.Easings;
import ru.zero.util.render.math.animation.impl.EaseInOutQuad;

/**
 * Подсветка цели только по {@link net.minecraft.client.MinecraftClient#targetedEntity} (Vanilla), без боевых модулей.
 */
@IModule(name = "TargetESP", description = "Жозки таргет есп", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class TargetESP extends Module {
   public static ModeSetting typeTargetEsp = new ModeSetting("Режим", "Картинка", "Картинка", "Призраки", "Кольцо",
         "Кубики");
   public static ModeSetting typeGhost = new ModeSetting("Режим призраков", "Обычный", "Обычный", "Новый", "Старый")
         .hidden(() -> !typeTargetEsp.is("Призраки"));
   public static ModeSetting typeImage = new ModeSetting("Режим картинки", "Клиент", "Клиент", "Ромб", "Ромб 2")
         .hidden(() -> !typeTargetEsp.is("Картинка"));
   public static ModeSetting typeCube = new ModeSetting("Режим кубиков", "Новый", "Новый", "Старый")
         .hidden(() -> !typeTargetEsp.is("Кубики"));
   private static final Identifier TARGET_TEXTURE = Identifier.of("zero", "textures/world/target.png");
   private static final Identifier TARGET_TEXTURE_C = Identifier.of("zero", "textures/world/targetn2.png");
   private static final Identifier TARGET_TEXTURE_N = Identifier.of("zero", "textures/world/targetn.png");
   private static final Identifier GLOW_TEXTURE = Identifier.of("zero", "textures/world/glow.png");
   private static final Identifier GLOW_TEXTURE_C = Identifier.of("zero", "textures/world/dashbloom.png");
   public static Animation2 alpha = new Animation2();
   public static Animation2 size = new Animation2();
   private LivingEntity lastTarget = null;
   private static long lastTime = 0L;
   private float animationNurik = 0.0F;
   private long currentTimeSpirits = 0L;
   private final ArrayList<TargetESP.OldCubeParticle> oldCubeParticles = new ArrayList<>();
   private static long oldCubeLastTime = System.currentTimeMillis();
   private static float oldCubeDeltaTime = 0.0F;
   private static final long OLD_CUBE_LIFE_TIME = 1000L;
   private static final int OLD_CUBE_PARTICLES_PER_SPAWN = 1;
   private static final float OLD_CUBE_SPAWN_INTERVAL = 0.02F;
   private static final int MAX_PARTICLES = 50;
   private float oldCubeSpawnAccumulator = 0.0F;
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
   private static final RenderPipeline TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_TEX_COLOR_SNIPPET })
               .withLocation(Identifier.of("zero", "pipeline/world/textured_quads"))
               .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final RenderPipeline RING_STRIP_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
               .withLocation(Identifier.of("minecraft", "rendertype_lequal_depth_test"))
               .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLE_STRIP)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final RenderPipeline RING_LINE_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
               .withLocation(Identifier.of("minecraft", "rendertype_lines"))
               .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINE_STRIP)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final RenderLayer RING_STRIP_LAYER = RenderLayer.of("zero_ring_strip", RenderSetup.builder(RING_STRIP_PIPELINE).expectedBufferSize(1024).translucent().build());
   private static final RenderLayer RING_LINE_LAYER = RenderLayer.of("zero_ring_line", RenderSetup.builder(RING_LINE_PIPELINE).expectedBufferSize(1024).translucent().build());
   private static final RenderPipeline COLOR_QUADS_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
               .withLocation(Identifier.of("zero", "pipeline/world/color_quads"))
               .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final RenderLayer COLOR_QUADS_LAYER = RenderLayer.of("zero_color_quads", RenderSetup.builder(COLOR_QUADS_PIPELINE).expectedBufferSize(1024).translucent().build());
   private static final RenderPipeline COLOR_LINES_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
               .withLocation(Identifier.of("minecraft", "rendertype_lines"))
               .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.LINES)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final RenderPipeline CUBE_LINES_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
               .withLocation(Identifier.of("zero", "targetesp_cube_lines"))
               .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final RenderLayer CUBE_LINES_LAYER = RenderLayer.of("targetesp_cube_lines", RenderSetup.builder(CUBE_LINES_PIPELINE).expectedBufferSize(1024).translucent().build());
   private static final RenderLayer TARGET_DIAMOND_LAYER = RenderLayer.of(
         "zero/target_esp/diamond",
         RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
               .expectedBufferSize(QUAD_BUFFER_SIZE_BYTES)
               .translucent()
               .texture("Sampler0", TARGET_TEXTURE)
               .build());
   private static final RenderLayer TARGET_CLIENT_LAYER = RenderLayer.of(
         "zero/target_esp/client",
         RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
               .expectedBufferSize(QUAD_BUFFER_SIZE_BYTES)
               .translucent()
               .texture("Sampler0", TARGET_TEXTURE_N)
               .build());
   private static final RenderLayer TARGET_DIAMOND2_LAYER = RenderLayer.of(
         "zero/target_esp/diamond2",
         RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
               .expectedBufferSize(QUAD_BUFFER_SIZE_BYTES)
               .translucent()
               .texture("Sampler0", TARGET_TEXTURE_C)
               .build());
   private static final RenderLayer GHOST_GLOW_LAYER = RenderLayer.of(
         "zero/target_esp/ghost_glow",
         RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
               .expectedBufferSize(QUAD_BUFFER_SIZE_BYTES)
               .translucent()
               .texture("Sampler0", GLOW_TEXTURE)
               .build());
   private static final RenderLayer CUBE_GLOW_LAYER = RenderLayer.of(
         "zero/target_esp/cube_glow",
         RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
               .expectedBufferSize(QUAD_BUFFER_SIZE_BYTES)
               .translucent()
               .texture("Sampler0", GLOW_TEXTURE_C)
               .build());

   public TargetESP() {
      this.addSettings(new Setting[] { typeTargetEsp, typeGhost, typeImage, typeCube });
   }

   @EventInit
   public void onRender(EventRender3D e) {
      alpha.update();
      LivingEntity target = this.getTarget();
      if (mc.world != null && mc.player != null) {
         alpha.run(target == null ? 0.0 : 1.0, 0.35F, Easings.QUART_OUT);
         if (alpha.getValue() > 0.0) {
            if (target != null) {
               if (this.lastTarget != target) {
                  lastTime = 0L;
                  this.currentTimeSpirits = 0L;
                  this.animationNurik = 0.0F;
               }

               this.lastTarget = target;
            }

            if (this.lastTarget != null && !typeTargetEsp.is("Не отображать")) {
               BufferAllocator allocator = new BufferAllocator(262144);
               Immediate immediate = VertexConsumerProvider.immediate(allocator);

               try {
                  if (typeTargetEsp.is("Картинка") && typeImage.is("Ромб")) {
                     this.renderDiamond(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                  }

                  if (typeTargetEsp.is("Картинка") && typeImage.is("Клиент")) {
                     this.renderDiamondNewStyle(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                  }

                  if (typeTargetEsp.is("Картинка") && typeImage.is("Ромб 2")) {
                     this.renderDiamondNewStyle2(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                  }

                  if (typeTargetEsp.is("Призраки") && typeGhost.is("Обычный")) {
                     this.renderGhosts(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                  }

                  if (typeTargetEsp.is("Призраки") && typeGhost.is("Новый")) {
                     this.renderSpirits(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                  }

                  if (typeTargetEsp.is("Призраки") && typeGhost.is("Старый")) {
                     this.renderSpiritsOld(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                  }

                  if (typeTargetEsp.is("Кольцо")) {
                     this.renderRing(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                  }

                  if (typeTargetEsp.is("Кубики") && typeCube.is("Новый")) {
                     this.renderCubes(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                  }

                  if (typeTargetEsp.is("Кубики") && typeCube.is("Старый")) {
                     this.renderCubesOld(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                  }

                  immediate.draw();
               } finally {
                  allocator.close();
               }
            }
         } else {
            this.lastTarget = null;
            lastTime = 0L;
            this.currentTimeSpirits = 0L;
            this.animationNurik = 0.0F;
         }
      }
   }

   private LivingEntity getTarget() {
      return CrosshairTargetUtil.getLivingCrosshairTarget();
   }

   private void renderDiamond(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      Vec3d lerpedPos = target.getLerpedPos(partialTicks);
      double x = lerpedPos.x;
      double y = lerpedPos.y;
      double z = lerpedPos.z;
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      matrices.push();
      matrices.translate(x - cameraPos.x, y - cameraPos.y + target.getHeight() / 1.75F, z - cameraPos.z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
      long currentTimeMillis = System.currentTimeMillis();
      float rotate = (float) Mathf.clamp(0.0, 720.0, (Math.sin(currentTimeMillis / 900.0) + 1.0) / 2.0 * 360.0 * 2.0);
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate));
      TargetESP.size.update();
      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      TargetESP.size.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float rzs = TargetESP.size.get();
      float sizePC = (float) alpha.getValue();
      int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * sizePC));
      int colorS = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(), sizePC), redColor, TargetESP.size.get());
      float size = 1.7F - 0.9F * sizePC + (0.35F - 0.35F * rzs);
      matrices.scale(size, size, 1.0F);
      Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();
      VertexConsumer bloomBuffer = immediate.getBuffer(TARGET_DIAMOND_LAYER);
      drawGradientQuad(bloomBuffer, bloomMatrix, colorS, (int) (255.0F * sizePC));
      matrices.pop();
   }

   private void renderDiamondNewStyle(MatrixStack matrices, Immediate immediate, LivingEntity target,
         float partialTicks) {
      Vec3d lerpedPos = target.getLerpedPos(partialTicks);
      double x = lerpedPos.x;
      double y = lerpedPos.y;
      double z = lerpedPos.z;
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      matrices.push();
      matrices.translate(x - cameraPos.x, y - cameraPos.y + target.getHeight() / 1.75F, z - cameraPos.z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
      long currentTimeMillis = System.currentTimeMillis();
      float rotate = (float) Mathf.clamp(0.0, 720.0, (Math.sin(currentTimeMillis / 1600.0) + 1.0) / 2.0 * 360.0 * 2.0);
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate));
      TargetESP.size.update();
      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      TargetESP.size.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float rzs = TargetESP.size.get();
      float sizePC = (float) alpha.getValue();
      int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * sizePC));
      int colorS = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(), sizePC), redColor, TargetESP.size.get());
      float size = 1.5F - 0.9F * sizePC + (0.35F - 0.35F * rzs);
      matrices.scale(size, size, 1.0F);
      Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();
      VertexConsumer bloomBuffer = immediate.getBuffer(TARGET_CLIENT_LAYER);
      drawGradientQuad(bloomBuffer, bloomMatrix, colorS, (int) (255.0F * sizePC));
      matrices.pop();
   }

   private void renderDiamondNewStyle2(MatrixStack matrices, Immediate immediate, LivingEntity target,
         float partialTicks) {
      Vec3d lerpedPos = target.getLerpedPos(partialTicks);
      double x = lerpedPos.x;
      double y = lerpedPos.y;
      double z = lerpedPos.z;
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      matrices.push();
      matrices.translate(x - cameraPos.x, y - cameraPos.y + target.getHeight() / 1.75F, z - cameraPos.z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
      long currentTimeMillis = System.currentTimeMillis();
      float rotate = (float) Mathf.clamp(0.0, 720.0, (Math.sin(currentTimeMillis / 1000.0) + 1.0) / 2.0 * 360.0 * 2.0);
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate));
      TargetESP.size.update();
      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      TargetESP.size.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float rzs = TargetESP.size.get();
      float sizePC = (float) alpha.getValue();
      int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * sizePC));
      int colorS = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(), sizePC), redColor, TargetESP.size.get());
      float size = 1.25F - 0.6F * sizePC + (0.35F - 0.35F * rzs);
      matrices.scale(size, size, 1.0F);
      Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();
      VertexConsumer bloomBuffer = immediate.getBuffer(TARGET_DIAMOND2_LAYER);
      drawGradientQuad(bloomBuffer, bloomMatrix, colorS, (int) (255.0F * sizePC));
      matrices.pop();
   }

   private void renderRing(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target != null) {
         Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
         double x = target.lastRenderX + (target.getX() - target.lastRenderX) * partialTicks;
         double y = target.lastRenderY + (target.getY() - target.lastRenderY) * partialTicks;
         double z = target.lastRenderZ + (target.getZ() - target.lastRenderZ) * partialTicks;
         matrices.push();
         matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
         float alphaPC = (float) alpha.getValue();
         float height = target.getHeight();
         double width = target.getWidth() * 1.0F - 0.2F * size.get();
         int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
         size.update();
         int hurtTicks = target.hurtTime;
         float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
         size.run(hurtPC, 0.4F, Easings.QUART_OUT);
         Matrix4f matrix = matrices.peek().getPositionMatrix();
         double duration = 1800.0;
         double elapsed = System.currentTimeMillis() % duration;
         boolean side = elapsed > duration / 2.0;
         double progress = elapsed / (duration / 2.0);
         progress = side ? progress - 1.0 : 1.0 - progress;
         progress = progress < 0.5 ? 2.0 * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0;
         double eased = height / 1.25F * (progress > 0.5 ? 1.0 - progress : progress) * (side ? -1 : 1);
         VertexConsumer strip = immediate.getBuffer(RING_STRIP_LAYER);

         for (int i = 0; i <= 360; i += 5) {
            double rad = Math.toRadians(i);
            float xPos = (float) (Math.cos(rad) * width);
            float zPos = (float) (Math.sin(rad) * width);
            int c = ColorUtil.overCol(
                  ColorUtil.multAlpha(
                        ColorUtil.gradient(ColorUtil.multDark(ColorUtil.fade(), 0.5F),
                              ColorUtil.multDark(ColorUtil.fade(), 1.0F), i * 4, 1),
                        alphaPC),
                  redColor,
                  size.get());
            int r = c >> 16 & 0xFF;
            int g = c >> 8 & 0xFF;
            int b = c & 0xFF;
            strip.vertex(matrix, xPos, (float) (height * progress), zPos).color(r, g, b, (int) (180.0F * alphaPC));
            strip.vertex(matrix, xPos, (float) (height * progress + eased), zPos).color(r, g, b, 0);
         }

         VertexConsumer line = immediate.getBuffer(RING_LINE_LAYER);

         for (int i = 0; i <= 360; i += 5) {
            double rad = Math.toRadians(i);
            float xPos = (float) (Math.cos(rad) * width);
            float zPos = (float) (Math.sin(rad) * width);
            int c = ColorUtil.overCol(
                  ColorUtil.multAlpha(
                        ColorUtil.gradient(ColorUtil.multDark(ColorUtil.fade(), 0.5F),
                              ColorUtil.multDark(ColorUtil.fade(), 1.0F), i * 4, 1),
                        alphaPC),
                  redColor,
                  size.get());
            line.vertex(matrix, xPos, (float) (height * progress), zPos)
                  .color(ColorUtil.replAlpha(c, (int) (255.0F * alphaPC)));
         }

         matrices.pop();
      }
   }

   private void renderSpirits(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target != null) {
         long currentTime = System.currentTimeMillis();
         if (this.currentTimeSpirits == 0L) {
            this.currentTimeSpirits = currentTime;
         }

         long timeDiff = currentTime - this.currentTimeSpirits;
         if (timeDiff > 0L) {
            this.animationNurik += (float) (5L * timeDiff) / 900.0F;
         }

         this.currentTimeSpirits = currentTime;
         Vec3d lerpedPos = target.getLerpedPos(partialTicks);
         Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
         double x = lerpedPos.x - cameraPos.x;
         double y = lerpedPos.y - cameraPos.y;
         double z = lerpedPos.z - cameraPos.z;
         float alphaPC = (float) alpha.getValue();
         size.update();
         int hurtTicks = target.hurtTime;
         float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
         size.run(hurtPC, 0.4F, Easings.QUART_OUT);
         float atts = size.get();
         int fadeColor = ColorUtil.fade();
         int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
         int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);
         int n2 = 3;
         int n3 = 12;
         int n4 = 3 * n2;
         matrices.push();
         Camera camera = mc.gameRenderer.getCamera();

         for (int i = 0; i < n4; i += n2) {
            for (int j = 0; j < n3; j++) {
               float f2 = this.animationNurik + j * 0.1F;
               float f3 = 0.75F;
               float f4 = 0.5F;
               int n5 = (int) Math.pow(i, 2.0);
               matrices.push();
               double particleX = x + f3 * Math.sin(f2 + n5);
               double particleY = y + f4 + 0.3F * Math.sin(this.animationNurik + j * 0.2F) + 0.2F * i;
               double particleZ = z + f3 * Math.cos(f2 - n5);
               matrices.translate(particleX, particleY, particleZ);
               float scale = 0.005F + j / 2000.0F;
               matrices.scale(scale, scale, scale);
               matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
               matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
               Matrix4f matrix = matrices.peek().getPositionMatrix();
               VertexConsumer buffer = immediate.getBuffer(GHOST_GLOW_LAYER);
               int r = baseColor >> 16 & 0xFF;
               int g = baseColor >> 8 & 0xFF;
               int b = baseColor & 0xFF;
               int a = (int) (alphaPC * 255.0F);
               int n7 = -25;
               int n8 = 50;
               buffer.vertex(matrix, n7, n7 + n8, 0.0F)
                     .color(r, g, b, a)
                     .texture(0.0F, 1.0F)
                     .overlay(OverlayTexture.DEFAULT_UV)
                     .light(15728880)
                     .normal(0.0F, 0.0F, 1.0F);
               buffer.vertex(matrix, n7 + n8, n7 + n8, 0.0F)
                     .color(r, g, b, a)
                     .texture(1.0F, 1.0F)
                     .overlay(OverlayTexture.DEFAULT_UV)
                     .light(15728880)
                     .normal(0.0F, 0.0F, 1.0F);
               buffer.vertex(matrix, n7 + n8, n7, 0.0F)
                     .color(r, g, b, a)
                     .texture(1.0F, 0.0F)
                     .overlay(OverlayTexture.DEFAULT_UV)
                     .light(15728880)
                     .normal(0.0F, 0.0F, 1.0F);
               buffer.vertex(matrix, n7, n7, 0.0F)
                     .color(r, g, b, a)
                     .texture(0.0F, 0.0F)
                     .overlay(OverlayTexture.DEFAULT_UV)
                     .light(15728880)
                     .normal(0.0F, 0.0F, 1.0F);
               matrices.pop();
            }
         }

         matrices.pop();
      }
   }

   private void renderSpiritsOld(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target != null) {
         long currentTime = System.currentTimeMillis();
         if (this.currentTimeSpirits == 0L) {
            this.currentTimeSpirits = currentTime;
         }

         long timeDiff = currentTime - this.currentTimeSpirits;
         if (timeDiff > 0L) {
            this.animationNurik += (float) (5L * timeDiff) / 200.0F;
         }

         this.currentTimeSpirits = currentTime;
         Vec3d lerpedPos = target.getLerpedPos(partialTicks);
         Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
         double x = lerpedPos.x - cameraPos.x;
         double y = lerpedPos.y + 1.1F - cameraPos.y;
         double z = lerpedPos.z - cameraPos.z;
         float alphaPC = (float) alpha.getValue();
         int espLength = 17;
         int factor = 6;
         float shaking = 1.25F;
         float amplitude = 1.1F;
         float iAge = this.animationNurik;
         Camera camera = mc.gameRenderer.getCamera();
         double targetWidth = target.getWidth() + 0.12F;
         boolean canSee = mc.player.canSee(target);
         VertexConsumer buffer = immediate.getBuffer(GHOST_GLOW_LAYER);
         size.update();
         int hurtTicks = target.hurtTime;
         float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
         size.run(hurtPC, 0.4F, Easings.QUART_OUT);
         float atts = size.get();
         int fadeColor = ColorUtil.fade();
         int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
         int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);

         for (int j = 0; j < 3; j++) {
            for (int i = 0; i <= espLength; i++) {
               double radians = Math.toRadians(((i / 1.5F + iAge) * factor + j * 120) % (factor * 360));
               double sinQuad = Math.sin(Math.toRadians(iAge * 2.0F + i * (j + 1)) * amplitude) / shaking;
               float offset = (float) i / espLength;
               matrices.push();
               matrices.translate(x + Math.cos(radians) * targetWidth, y + sinQuad,
                     z + Math.sin(radians) * targetWidth);
               matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
               matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
               Matrix4f matrix = matrices.peek().getPositionMatrix();
               int color = ColorUtil.multAlpha(baseColor, offset * alphaPC);
               int r = color >> 16 & 0xFF;
               int g = color >> 8 & 0xFF;
               int b = color & 0xFF;
               int a = color >> 24 & 0xFF;
               float scale = Math.max(0.25F * offset, 0.22F);
               buffer.vertex(matrix, -scale, scale, 0.0F)
                     .color(r, g, b, a)
                     .texture(0.0F, 1.0F)
                     .overlay(OverlayTexture.DEFAULT_UV)
                     .light(15728880)
                     .normal(0.0F, 0.0F, 1.0F);
               buffer.vertex(matrix, scale, scale, 0.0F)
                     .color(r, g, b, a)
                     .texture(1.0F, 1.0F)
                     .overlay(OverlayTexture.DEFAULT_UV)
                     .light(15728880)
                     .normal(0.0F, 0.0F, 1.0F);
               buffer.vertex(matrix, scale, -scale, 0.0F)
                     .color(r, g, b, a)
                     .texture(1.0F, 0.0F)
                     .overlay(OverlayTexture.DEFAULT_UV)
                     .light(15728880)
                     .normal(0.0F, 0.0F, 1.0F);
               buffer.vertex(matrix, -scale, -scale, 0.0F)
                     .color(r, g, b, a)
                     .texture(0.0F, 0.0F)
                     .overlay(OverlayTexture.DEFAULT_UV)
                     .light(15728880)
                     .normal(0.0F, 0.0F, 1.0F);
               matrices.pop();
            }
         }
      }
   }

   private static void drawGradientQuad(VertexConsumer buffer, Matrix4f matrix, int color, int alpha) {
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      buffer.vertex(matrix, -0.5F, -0.5F, 0.0F)
            .color(r, g, b, alpha)
            .texture(0.0F, 1.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(0.0F, 0.0F, 1.0F);
      buffer.vertex(matrix, 0.5F, -0.5F, 0.0F)
            .color(r, g, b, alpha)
            .texture(1.0F, 1.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(0.0F, 0.0F, 1.0F);
      buffer.vertex(matrix, 0.5F, 0.5F, 0.0F)
            .color(r, g, b, alpha)
            .texture(1.0F, 0.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(0.0F, 0.0F, 1.0F);
      buffer.vertex(matrix, -0.5F, 0.5F, 0.0F)
            .color(r, g, b, alpha)
            .texture(0.0F, 0.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(0.0F, 0.0F, 1.0F);
   }

   private void renderGhosts(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (target != null) {
         double radius = 0.3 + target.getWidth() / 2.0F;
         TargetESP.size.update();
         int hurtTicks = target.hurtTime;
         float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
         TargetESP.size.run(hurtPC, 0.4F, Easings.QUART_OUT);
         float atts = TargetESP.size.get();
         float speed = 30.0F;
         float size = 0.4F - 0.1F * atts;
         double distance = 6 - (int) (1.0F * atts);
         int length = 40 - (int) (12.0F * atts);
         Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
         Camera camera = mc.gameRenderer.getCamera();
         if (lastTime == 0L) {
            lastTime = System.currentTimeMillis();
         }

         long currentTime = System.currentTimeMillis();
         Vec3d interpolated = target.getLerpedPos(partialTicks);
         interpolated = new Vec3d(interpolated.x, interpolated.y + 0.32 + target.getHeight() / 2.0F, interpolated.z);
         double posX = interpolated.x + 0.2;
         double posY = interpolated.y;
         double posZ = interpolated.z;
         VertexConsumer buffer = immediate.getBuffer(GHOST_GLOW_LAYER);
         float sizePC = (float) alpha.getValue();
         int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * sizePC));
         int fadeColor = ColorUtil.fade();
         int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, sizePC), redColor, atts);
         int color1 = baseColor;
         int color2 = ColorUtil.multDark(baseColor, 0.8F);
         int color3 = ColorUtil.multDark(baseColor, 0.6F);
         int color4 = ColorUtil.multDark(baseColor, 0.4F);
         matrices.push();
         matrices.translate(posX - cameraPos.x, posY - cameraPos.y, posZ - cameraPos.z);
         float sfz = 0.3F;

         for (int i = 0; i < length; i++) {
            double angle = 0.05F * (currentTime - lastTime - i * distance) / speed;
            double s = Math.sin(angle * Math.PI) * radius;
            double c = Math.cos(angle * Math.PI) * radius;
            double o = Math.cos(angle * Math.PI) * radius;
            float t = (float) i / (length - 1);
            float scale = 1.0F - t * sfz;
            float curSize = size * scale;
            matrices.push();
            matrices.translate(s, o, -c);
            matrices.translate(-curSize / 2.0F, -curSize / 2.0F, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.translate(curSize / 2.0F, curSize / 2.0F, 0.0F);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            this.drawTexturedQuad(buffer, matrix, color1, color2, color3, color4, curSize);
            matrices.pop();
         }

         for (int i = 0; i < length; i++) {
            double angle = 0.05F * (currentTime - lastTime - i * distance) / speed;
            double s = Math.sin(angle * Math.PI) * radius;
            double c = Math.cos(angle * Math.PI) * radius;
            double o = Math.sin(angle * Math.PI) * radius;
            float t = (float) i / (length - 1);
            float scale = 1.0F - t * sfz;
            float curSize = size * scale;
            matrices.push();
            matrices.translate(-s, o, -c);
            matrices.translate(-curSize / 2.0F, -curSize / 2.0F, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.translate(curSize / 2.0F, curSize / 2.0F, 0.0F);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            this.drawTexturedQuad(buffer, matrix, color1, color2, color3, color4, curSize);
            matrices.pop();
         }

         for (int i = 0; i < length; i++) {
            double angle = 0.05F * (currentTime - lastTime - i * distance) / speed;
            double s = Math.sin(angle * Math.PI) * radius;
            double c = Math.cos(angle * Math.PI) * radius;
            double o = Math.sin(angle * Math.PI) * radius;
            float t = (float) i / (length - 1);
            float scale = 1.0F - t * sfz;
            float curSize = size * scale;
            matrices.push();
            matrices.translate(s, o, c);
            matrices.translate(-curSize / 2.0F, -curSize / 2.0F, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.translate(curSize / 2.0F, curSize / 2.0F, 0.0F);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            this.drawTexturedQuad(buffer, matrix, color1, color2, color3, color4, curSize);
            matrices.pop();
         }

         matrices.pop();
      }
   }

   private void drawTexturedQuad(VertexConsumer buffer, Matrix4f matrix, int color1, int color2, int color3, int color4,
         float size) {
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
      buffer.vertex(matrix, 0.0F, -size, 0.0F).texture(0.0F, 0.0F).color(r1, g1, b1, a1);
      buffer.vertex(matrix, -size, -size, 0.0F).texture(0.0F, 1.0F).color(r2, g2, b2, a2);
      buffer.vertex(matrix, -size, 0.0F, 0.0F).texture(1.0F, 1.0F).color(r3, g3, b3, a3);
      buffer.vertex(matrix, 0.0F, 0.0F, 0.0F).texture(1.0F, 0.0F).color(r4, g4, b4, a4);
   }

   private void renderCubes(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target != null) {
         Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
         long time = System.currentTimeMillis();
         int count = 24;
         double radius = 0.4 + target.getWidth() / 2.0F + 0.35F - 0.35F * alpha.get();
         double heightRange = target.getHeight();
         Vec3d lerpedPos = target.getLerpedPos(partialTicks);
         float alphaPC = (float) alpha.getValue();
         size.update();
         int hurtTicks = target.hurtTime;
         float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
         size.run(hurtPC, 0.4F, Easings.QUART_OUT);
         float atts = size.get();
         int redColor = ColorUtil.getColor(200, 70, 70, (int) (60.0F * alphaPC));
         int fadeColor = ColorUtil.fade();
         int baseColor = ColorUtil.multAlpha(fadeColor, alphaPC * 0.35F);
         int color = ColorUtil.overCol(baseColor, redColor, atts);
         int glowCol = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC),
               ColorUtil.getColor(200, 100, 100, (int) (255.0F * alphaPC)), atts);

         for (int i = 0; i < count; i++) {
            double r1 = Math.sin(i * 132.12 + 4.12);
            double r2 = Math.cos(i * 453.21 + 1.23);
            double r3 = Math.sin(i * 789.34 + 9.87);
            double speedFactor = 1.0;
            double angleOffset = (Math.PI * 2) / count * i;
            double timeFactor = time / 6000.0 * (Math.PI * 2) * speedFactor;
            double angle = timeFactor + angleOffset;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double ySpeed = 1.0 + r1 * 0.2;
            double yPhase = angleOffset + r3 * 2.0;
            double yOffset = Math.sin(time / 9000.0 * (Math.PI * 2) * ySpeed + yPhase) * 0.45 + 0.55;
            double y = yOffset * heightRange;
            double cX = lerpedPos.x + x - cameraPos.x;
            double cY = lerpedPos.y + y - cameraPos.y;
            double cZ = lerpedPos.z + z - cameraPos.z;
            matrices.push();
            matrices.translate(cX, cY, cZ);
            float pulse = 1.0F + 0.15F * (float) Math.sin(time / 400.0 + i * 1.5);
            float cubeSize = 0.19F * pulse;
            double hurtFactor = atts * (0.5 + 0.5 * Math.sin(i * 123.45));
            if (hurtFactor > 0.05) {
               cubeSize = (float) (cubeSize * (1.0 - hurtFactor * 0.2));
               double pushOut = hurtFactor * 0.4;
               matrices.translate(Math.cos(angle) * pushOut, 0.0, Math.sin(angle) * pushOut);
            }

            matrices.push();
            float selfRotSpeed = 12000.0F + (float) r3 * 2000.0F;
            float selfRot = (float) (time % (long) Math.abs(selfRotSpeed)) / Math.abs(selfRotSpeed) * 360.0F;
            if (i % 3 == 0) {
               matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(selfRot));
               matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(selfRot));
            } else if (i % 3 == 1) {
               matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(selfRot));
               matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(selfRot));
            } else {
               matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(selfRot));
               matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(selfRot));
            }

            VertexConsumer buffer = immediate.getBuffer(COLOR_QUADS_LAYER);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            drawColorCube(buffer, matrix, ColorUtil.multAlpha(color, 0.5F), cubeSize);
            int lineColor = ColorUtil.multAlpha(color, alphaPC * 1.0F);
            VertexConsumer lineBuffer = immediate.getBuffer(CUBE_LINES_LAYER);
            drawCubeLines(lineBuffer, matrix, lineColor, cubeSize);
            matrices.pop();
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
            VertexConsumer glowBuffer = immediate.getBuffer(CUBE_GLOW_LAYER);
            Matrix4f glowMatrix = matrices.peek().getPositionMatrix();
            float glowSize = cubeSize * 3.0F;
            matrices.scale(glowSize, glowSize, glowSize);
            drawGradientQuad(glowBuffer, glowMatrix, glowCol, (int) (125.0F * alphaPC));
            matrices.pop();
            matrices.pop();
         }
      }
   }

   private static void drawColorCube(VertexConsumer buffer, Matrix4f matrix, int color, float size) {
      float s = size / 2.0F;
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      int a = color >> 24 & 0xFF;
      buffer.vertex(matrix, -s, s, -s).color(r, g, b, a);
      buffer.vertex(matrix, -s, s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, s, -s).color(r, g, b, a);
      buffer.vertex(matrix, -s, -s, -s).color(r, g, b, a);
      buffer.vertex(matrix, s, -s, -s).color(r, g, b, a);
      buffer.vertex(matrix, s, -s, s).color(r, g, b, a);
      buffer.vertex(matrix, -s, -s, s).color(r, g, b, a);
      buffer.vertex(matrix, -s, s, s).color(r, g, b, a);
      buffer.vertex(matrix, -s, -s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, -s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, s, s).color(r, g, b, a);
      buffer.vertex(matrix, -s, s, -s).color(r, g, b, a);
      buffer.vertex(matrix, s, s, -s).color(r, g, b, a);
      buffer.vertex(matrix, s, -s, -s).color(r, g, b, a);
      buffer.vertex(matrix, -s, -s, -s).color(r, g, b, a);
      buffer.vertex(matrix, -s, s, -s).color(r, g, b, a);
      buffer.vertex(matrix, -s, -s, -s).color(r, g, b, a);
      buffer.vertex(matrix, -s, -s, s).color(r, g, b, a);
      buffer.vertex(matrix, -s, s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, s, -s).color(r, g, b, a);
      buffer.vertex(matrix, s, s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, -s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, -s, -s).color(r, g, b, a);
   }

   private static void drawCubeLines(VertexConsumer buffer, Matrix4f matrix, int color, float size) {
      float s = size / 2.0F;
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      int a = color >> 24 & 0xFF;
      drawLine(buffer, matrix, -s, -s, -s, s, -s, -s, r, g, b, a);
      drawLine(buffer, matrix, s, -s, -s, s, -s, s, r, g, b, a);
      drawLine(buffer, matrix, s, -s, s, -s, -s, s, r, g, b, a);
      drawLine(buffer, matrix, -s, -s, s, -s, -s, -s, r, g, b, a);
      drawLine(buffer, matrix, -s, s, -s, s, s, -s, r, g, b, a);
      drawLine(buffer, matrix, s, s, -s, s, s, s, r, g, b, a);
      drawLine(buffer, matrix, s, s, s, -s, s, s, r, g, b, a);
      drawLine(buffer, matrix, -s, s, s, -s, s, -s, r, g, b, a);
      drawLine(buffer, matrix, -s, -s, -s, -s, s, -s, r, g, b, a);
      drawLine(buffer, matrix, s, -s, -s, s, s, -s, r, g, b, a);
      drawLine(buffer, matrix, s, -s, s, s, s, s, r, g, b, a);
      drawLine(buffer, matrix, -s, -s, s, -s, s, s, r, g, b, a);
   }

   private static void drawLine(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float z1, float x2,
         float y2, float z2, int r, int g, int b, int a) {
      buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
      buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
   }

   private void renderCubesOld(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target == null) {
         this.oldCubeParticles.clear();
      } else {
         Iterator<TargetESP.OldCubeParticle> iterator = this.oldCubeParticles.iterator();

         while (iterator.hasNext()) {
            TargetESP.OldCubeParticle particle = iterator.next();
            if (particle.animation.getDirection() != Direction.FORWARDS && particle.animation.getOutput() <= 0.0F) {
               iterator.remove();
            }
         }

         long currentTime = System.currentTimeMillis();
         oldCubeDeltaTime = Math.max(0.001F, Math.min(0.1F, (float) (currentTime - oldCubeLastTime) / 1000.0F));
         oldCubeLastTime = currentTime;
         if (this.oldCubeParticles.size() < 50) {
            this.oldCubeSpawnAccumulator = this.oldCubeSpawnAccumulator + oldCubeDeltaTime;

            while (this.oldCubeSpawnAccumulator >= 0.02F && this.oldCubeParticles.size() < 50) {
               this.oldCubeSpawnAccumulator -= 0.02F;

               for (int i = 0; i < 1 && this.oldCubeParticles.size() < 50; i++) {
                  double rand = MathHelper.random(0.0F, 360.0F);
                  double x = Math.cos(rand * Math.PI / 180.0) * 0.7F;
                  double y = MathHelper.getRandomNumberBetween(0.04F, 0.2F);
                  double z = Math.sin(rand * Math.PI / 180.0) * 0.7F;
                  this.oldCubeParticles.add(new TargetESP.OldCubeParticle(target, x, y, z));
               }
            }
         }

         if (!this.oldCubeParticles.isEmpty()) {
            float alphaPC = (float) alpha.getValue();
            size.update();
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
            size.run(hurtPC, 0.4F, Easings.QUART_OUT);
            float atts = size.get();
            int redColor = ColorUtil.getColor(200, 70, 70, (int) (60.0F * alphaPC));
            int fadeColor = ColorUtil.fade();
            int baseColor = ColorUtil.multAlpha(fadeColor, alphaPC * 0.35F);
            int color = ColorUtil.overCol(baseColor, redColor, atts);
            int glowCol = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC),
                  ColorUtil.getColor(200, 100, 100, (int) (255.0F * alphaPC)), atts);
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
            float pitch = mc.gameRenderer.getCamera().getPitch();
            float yaw = mc.gameRenderer.getCamera().getYaw();
            for (TargetESP.OldCubeParticle particle : this.oldCubeParticles) {
               particle.update(partialTicks);
               particle.render(matrices, immediate, color, glowCol, alphaPC, atts, partialTicks, cameraPos, pitch, yaw);
            }
         }
      }
   }

   private static void drawAxisBox(
         MatrixStack matrixStack,
         Immediate immediate,
         Matrix4f matrix,
         float minX,
         float minY,
         float minZ,
         float maxX,
         float maxY,
         float maxZ,
         int colorOut,
         int colorFill) {
      VertexConsumer buffer = immediate.getBuffer(COLOR_QUADS_LAYER);
      int rOut = colorOut >> 16 & 0xFF;
      int gOut = colorOut >> 8 & 0xFF;
      int bOut = colorOut & 0xFF;
      int aOut = colorOut >> 24 & 0xFF;
      int rFill = colorFill >> 16 & 0xFF;
      int gFill = colorFill >> 8 & 0xFF;
      int bFill = colorFill & 0xFF;
      int aFill = colorFill >> 24 & 0xFF;
      buffer.vertex(matrix, minX, maxY, minZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, minX, maxY, maxZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, maxX, maxY, maxZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, maxX, maxY, minZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, minX, minY, minZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, minX, minY, maxZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, maxX, minY, maxZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, maxX, minY, minZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, minX, minY, maxZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, minX, maxY, maxZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, maxX, maxY, maxZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, maxX, minY, maxZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, minX, minY, minZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, maxX, minY, minZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, maxX, maxY, minZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, minX, maxY, minZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, minX, minY, minZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, minX, maxY, minZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, minX, maxY, maxZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, minX, minY, maxZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, maxX, minY, minZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, maxX, minY, maxZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, maxX, maxY, maxZ).color(rOut, gOut, bOut, aOut);
      buffer.vertex(matrix, maxX, maxY, minZ).color(rOut, gOut, bOut, aOut);
      VertexConsumer fillBuffer = immediate.getBuffer(COLOR_QUADS_LAYER);
      fillBuffer.vertex(matrix, minX, maxY, minZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, minX, maxY, maxZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, maxX, maxY, maxZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, maxX, maxY, minZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, minX, minY, minZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, maxX, minY, minZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, maxX, minY, maxZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, minX, minY, maxZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, minX, minY, maxZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, minX, maxY, maxZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, maxX, maxY, maxZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, maxX, minY, maxZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, minX, minY, minZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, minX, maxY, minZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, maxX, maxY, minZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, maxX, minY, minZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, minX, minY, minZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, minX, minY, maxZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, minX, maxY, maxZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, minX, maxY, minZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, maxX, minY, minZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, maxX, maxY, minZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, maxX, maxY, maxZ).color(rFill, gFill, bFill, aFill);
      fillBuffer.vertex(matrix, maxX, minY, maxZ).color(rFill, gFill, bFill, aFill);
   }

   @Environment(EnvType.CLIENT)
   private static class OldCubeParticle {
      double x;
      double y;
      double z;
      double posX;
      double posY;
      double posZ;
      double motionX;
      double motionY;
      double motionZ;
      long time;
      LivingEntity entity;
      Animation animation = new EaseInOutQuad(500, 1.0);
      private double velocityY;

      public OldCubeParticle(LivingEntity entity, double x, double y, double z) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.entity = entity;
         this.time = System.currentTimeMillis();
         this.velocityY = MathHelper.getRandomNumberBetween(0.01F, 0.04F);
      }

      public long getTime() {
         return this.time;
      }

      public void update(float partialTicks) {
         long currentTime = System.currentTimeMillis();
         long elapsed = currentTime - this.getTime();
         this.animation.setDirection(elapsed <= 800L ? Direction.FORWARDS : Direction.BACKWARDS);
         this.y = this.y + this.velocityY * (TargetESP.oldCubeDeltaTime * 60.0F);
         if (this.entity != null) {
            Vec3d lerpedPos = this.entity.getLerpedPos(partialTicks);
            this.motionX = this.x + lerpedPos.x;
            this.motionY = this.y + lerpedPos.y;
            this.motionZ = this.z + lerpedPos.z;
         }
      }

      public void render(
            MatrixStack matrixStack,
            Immediate immediate,
            int color,
            int glowCol,
            float alphaPC,
            float atts,
            float partialTicks,
            Vec3d cameraPos,
            float pitch,
            float yaw) {
         long currentTime = System.currentTimeMillis();
         double rotation = (currentTime - this.getTime()) / 10.0;
         this.posX = MathHelper.interpolate(this.posX, this.motionX - cameraPos.x, 0.2F);
         this.posY = MathHelper.interpolate(this.posY, this.motionY - cameraPos.y, 0.2F);
         this.posZ = MathHelper.interpolate(this.posZ, this.motionZ - cameraPos.z, 0.2F);
         float animOutput = this.animation.getOutput();
         if (!(animOutput <= 0.0F)) {
            float pulse = 1.0F + 0.15F * (float) Math.sin((currentTime - this.getTime()) / 400.0);
            float cubeSize = 0.12F + 0.04F * animOutput;
            matrixStack.push();
            matrixStack.translate(this.posX, this.posY, this.posZ);
            matrixStack.push();
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotation));
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotation));
            matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotation));
            Matrix4f matrix = matrixStack.peek().getPositionMatrix();
            int cubeColor = ColorUtil.multAlpha(color, 0.5F * animOutput);
            VertexConsumer buffer = immediate.getBuffer(TargetESP.COLOR_QUADS_LAYER);
            TargetESP.drawColorCube(buffer, matrix, cubeColor, cubeSize);
            int lineColor = ColorUtil.multAlpha(color, alphaPC * animOutput);
            VertexConsumer lineBuffer = immediate.getBuffer(TargetESP.CUBE_LINES_LAYER);
            TargetESP.drawCubeLines(lineBuffer, matrix, lineColor, cubeSize);
            matrixStack.pop();
            matrixStack.push();
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            VertexConsumer glowBuffer = immediate.getBuffer(TargetESP.CUBE_GLOW_LAYER);
            Matrix4f glowMatrix = matrixStack.peek().getPositionMatrix();
            float glowSize = cubeSize * 3.0F;
            matrixStack.scale(glowSize, glowSize, glowSize);
            int glowColorWithAlpha = ColorUtil.reAlphaInt(glowCol, (int) (125.0F * alphaPC * animOutput));
            TargetESP.drawGradientQuad(glowBuffer, glowMatrix, glowColorWithAlpha,
                  ColorUtil.getAlpha(glowColorWithAlpha));
            matrixStack.pop();
            matrixStack.pop();
         }
      }
   }
}
