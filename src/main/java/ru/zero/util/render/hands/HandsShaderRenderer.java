package ru.zero.util.render.hands;

import com.mojang.blaze3d.textures.GpuTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import ru.zero.util.render.backends.gl.GlState;
import ru.zero.util.render.backends.gl.ShaderProgram;
import ru.zero.util.render.core.RenderFrameMetrics;

@Environment(EnvType.CLIENT)
public final class HandsShaderRenderer {
   private static final float[] QUAD = new float[]{
      -1.0F, -1.0F, 0.0F, 0.0F,
      1.0F, -1.0F, 1.0F, 0.0F,
      1.0F, 1.0F, 1.0F, 1.0F,
      -1.0F, -1.0F, 0.0F, 0.0F,
      1.0F, 1.0F, 1.0F, 1.0F,
      -1.0F, 1.0F, 0.0F, 1.0F
   };
   private ShaderProgram program;
   private int uTintColor = -1;
   private int uTime = -1;
   private int uDepthNow = -1;
   private int uDepthBefore = -1;
   private int vao;
   private int vbo;
   private int tempFbo;
   private SimpleFramebuffer depthSnapshot;
   private int snapshotWidth = -1;
   private int snapshotHeight = -1;
   private boolean failed;

   public void snapshotDepth() {
      if (this.failed) {
         return;
      }

      try {
         MinecraftClient mc = MinecraftClient.getInstance();
         Framebuffer main = mc.getFramebuffer();
         if (main == null || mc.getWindow() == null) {
            return;
         }

         int width = mc.getWindow().getFramebufferWidth();
         int height = mc.getWindow().getFramebufferHeight();
         if (width <= 0 || height <= 0) {
            return;
         }

         if (this.depthSnapshot == null) {
            this.depthSnapshot = new SimpleFramebuffer("zero_hands_depth", width, height, true);
            this.snapshotWidth = width;
            this.snapshotHeight = height;
         } else if (this.snapshotWidth != width || this.snapshotHeight != height) {
            this.depthSnapshot.resize(width, height);
            this.snapshotWidth = width;
            this.snapshotHeight = height;
         }

         GlState.Snapshot snapshot = GlState.push();

         try {
            this.depthSnapshot.copyDepthFrom(main);
         } finally {
            GlState.pop(snapshot);
         }
      } catch (Throwable var8) {
         this.failed = true;
         System.err.println("[Zero/HandsShader] Depth snapshot failed: " + var8);
      }
   }

   public void draw(float r, float g, float b, float a) {
      if (this.failed || this.depthSnapshot == null) {
         return;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      Framebuffer main = mc.getFramebuffer();
      if (main == null) {
         return;
      }

      int mainColor = glId(main.getColorAttachment());
      int mainDepth = glId(main.getDepthAttachment());
      int snapshotDepth = glId(this.depthSnapshot.getDepthAttachment());
      if (mainColor <= 0 || mainDepth <= 0 || snapshotDepth <= 0) {
         return;
      }

      GlState.Snapshot snapshot = GlState.push();

      try {
         this.ensureInitialized();
         if (this.tempFbo == 0) {
            this.tempFbo = GL30.glGenFramebuffers();
         }

         if (this.tempFbo == 0) {
            return;
         }

         GL30.glBindFramebuffer(36160, this.tempFbo);
         GL30.glFramebufferTexture2D(36160, 36064, 3553, mainColor, 0);
         boolean complete = GL30.glCheckFramebufferStatus(36160) == 36053;
         GL11.glDrawBuffer(36064);

         if (complete) {
            GL11.glColorMask(true, true, true, true);
            GL11.glDisable(3089);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            GL11.glDisable(2884);
            GL11.glEnable(3042);
            GL14.glBlendFuncSeparate(770, 771, 770, 771);
            this.program.use();
            if (this.uTintColor >= 0) {
               GL20.glUniform4f(this.uTintColor, r, g, b, a);
            }

            if (this.uTime >= 0) {
               GL20.glUniform1f(this.uTime, (float) (System.currentTimeMillis() % 100000L) / 1000.0F);
            }

            if (this.uDepthNow >= 0) {
               GL20.glUniform1i(this.uDepthNow, 0);
            }

            if (this.uDepthBefore >= 0) {
               GL20.glUniform1i(this.uDepthBefore, 1);
            }

            GL13.glActiveTexture(33984);
            int previousUnit0 = GL11.glGetInteger(32873);
            GL11.glBindTexture(3553, mainDepth);
            GL13.glActiveTexture(33985);
            int previousUnit1 = GL11.glGetInteger(32873);
            GL11.glBindTexture(3553, snapshotDepth);

            try {
               GL30.glBindVertexArray(this.vao);
               RenderFrameMetrics.getInstance().recordDrawCall(2);
               GL11.glDrawArrays(4, 0, 6);
               GL30.glBindVertexArray(0);
            } finally {
               GL11.glBindTexture(3553, previousUnit1);
               GL13.glActiveTexture(33984);
               GL11.glBindTexture(3553, previousUnit0);
            }
         }

         GL30.glFramebufferTexture2D(36160, 36064, 3553, 0, 0);
      } catch (Throwable var16) {
         this.failed = true;
         System.err.println("[Zero/HandsShader] Draw failed: " + var16);
      } finally {
         GlState.pop(snapshot);
      }
   }

   public void close() {
      try {
         if (this.program != null) {
            this.program.delete();
            this.program = null;
         }

         if (this.vbo != 0) {
            GL15.glDeleteBuffers(this.vbo);
            this.vbo = 0;
         }

         if (this.vao != 0) {
            GL30.glDeleteVertexArrays(this.vao);
            this.vao = 0;
         }

         if (this.tempFbo != 0) {
            GL30.glDeleteFramebuffers(this.tempFbo);
            this.tempFbo = 0;
         }

         if (this.depthSnapshot != null) {
            this.depthSnapshot.delete();
            this.depthSnapshot = null;
         }

         this.snapshotWidth = -1;
         this.snapshotHeight = -1;
         this.failed = false;
      } catch (Throwable var2) {
         System.err.println("[Zero/HandsShader] Close failed: " + var2);
      }
   }

   private void ensureInitialized() {
      if (this.program != null) {
         return;
      }

      this.program = ShaderProgram.fromResources(
         "assets/zero/shaders/hands/hands.vert",
         "assets/zero/shaders/hands/hands.frag"
      );
      this.uTintColor = this.program.getUniformLocation("uTintColor");
      this.uTime = this.program.getUniformLocation("uTime");
      this.uDepthNow = this.program.getUniformLocation("uDepthNow");
      this.uDepthBefore = this.program.getUniformLocation("uDepthBefore");
      this.vao = GL30.glGenVertexArrays();
      this.vbo = GL15.glGenBuffers();
      GL30.glBindVertexArray(this.vao);
      GL15.glBindBuffer(34962, this.vbo);
      GL15.glBufferData(34962, QUAD, 35044);
      GL20.glEnableVertexAttribArray(0);
      GL20.glVertexAttribPointer(0, 2, 5126, false, 16, 0L);
      GL20.glEnableVertexAttribArray(1);
      GL20.glVertexAttribPointer(1, 2, 5126, false, 16, 8L);
      GL15.glBindBuffer(34962, 0);
      GL30.glBindVertexArray(0);
   }

   private static int glId(GpuTexture texture) {
      return texture instanceof GlTexture glTexture && !glTexture.isClosed() ? glTexture.getGlId() : 0;
   }
}
