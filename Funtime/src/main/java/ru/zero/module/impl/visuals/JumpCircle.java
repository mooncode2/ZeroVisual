package ru.zero.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.zero.event.EventInit;
import ru.zero.event.player.EventJump;
import ru.zero.event.render.WorldRenderEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.animation.anim.Animation;
import ru.zero.util.render.math.animation.anim.util.Easings;

@IModule(name = "Jump Circle", description = " ", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class JumpCircle extends Module {
   private final List<JumpCircle.Circle> circles = new ArrayList<>();
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
   private static final Identifier JUMP_TEXTURE = Identifier.of("zero", "textures/world/jump.png");
   private static final RenderLayer JUMP_LAYER = RenderLayer.of(
         "zero/jump_circle",
         RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
               .expectedBufferSize(QUAD_BUFFER_SIZE_BYTES)
               .translucent()
               .texture("Sampler0", JUMP_TEXTURE)
               .build());

   @EventInit
   public void onJump(EventJump e) {
      this.circles.add(new JumpCircle.Circle(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).add(0.0, 0.05, 0.0)));
   }

   @EventInit
   public void onRender(WorldRenderEvent e) {
      if (!this.circles.isEmpty()) {
         this.circles.removeIf(cx -> System.currentTimeMillis() - cx.time > 2500L);
         if (!this.circles.isEmpty()) {
            Immediate immediate = e.worldRenderer().bufferSource();
            MatrixStack pose = e.matrixStack();

            int color = Renderer2D.ColorUtil.getMainColor(1, 1);
            int alpha = 255;

            for (JumpCircle.Circle c : this.circles) {
               if (System.currentTimeMillis() - c.time > 1000L && !c.isBack) {
                  c.animation.animate(0.0, 1.0, Easings.BACK_IN);
                  c.isBack = true;
               }

               c.animation.update();
               float rad = (float) c.animation.getValue();
               double posX = c.vector3d.x - mc.gameRenderer.getCamera().getCameraPos().x;
               double posY = c.vector3d.y - mc.gameRenderer.getCamera().getCameraPos().y;
               double posZ = c.vector3d.z - mc.gameRenderer.getCamera().getCameraPos().z;
               float size = 0.8F * rad;
               pose.push();
               pose.translate(posX, posY, posZ);
               pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
               Entry entry = pose.peek();
               Matrix4f matrix4f = entry.getPositionMatrix();
               Matrix3f normalMatrix = entry.getNormalMatrix();
               VertexConsumer buffer = immediate.getBuffer(JUMP_LAYER);
               this.drawTexturedQuad(buffer, matrix4f, normalMatrix, -size / 2.0F, -size / 2.0F, size, size, color,
                     alpha);
               pose.pop();
            }
         }
      }
   }

   private void drawTexturedQuad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix, float x, float y,
         float width, float height, int color, int alpha) {
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F);
      normalMatrix.transform(normal);
      normal.normalize();
      float x2 = x + width;
      float y2 = y + height;
      buffer.vertex(matrix, x, y, 0.0F)
            .color(r, g, b, alpha)
            .texture(0.0F, 1.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(normal.x, normal.y, normal.z);
      buffer.vertex(matrix, x2, y, 0.0F)
            .color(r, g, b, alpha)
            .texture(1.0F, 1.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(normal.x, normal.y, normal.z);
      buffer.vertex(matrix, x2, y2, 0.0F)
            .color(r, g, b, alpha)
            .texture(1.0F, 0.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(normal.x, normal.y, normal.z);
      buffer.vertex(matrix, x, y2, 0.0F)
            .color(r, g, b, alpha)
            .texture(0.0F, 0.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal(normal.x, normal.y, normal.z);
   }

   @Environment(EnvType.CLIENT)
   private class Circle {
      private final Vec3d vector3d;
      private final long time;
      private final Animation animation = new Animation();
      private boolean isBack;

      public Circle(Vec3d vector3d) {
         this.vector3d = vector3d;
         this.time = System.currentTimeMillis();
         this.animation.animate(2.0, 1.0, Easings.BACK_OUT);
      }
   }
}
