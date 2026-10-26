package ru.zero.util.render.sky;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.CompiledShaderPipeline;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL32;
import ru.zero.mixin.GameRendererAccessor;
import ru.zero.mixin.GlGpuBufferAccessor;
import ru.zero.module.impl.visuals.SkyBox;
import ru.zero.util.render.backends.gl.ResourceUtils;

@Environment(EnvType.CLIENT)
public final class EndSkyboxRenderer {
   private static final Logger LOGGER = LogManager.getLogger("Zero SkyBox");
   private static final EndSkyboxRenderer INSTANCE = new EndSkyboxRenderer();
   private static final String NOISE_TEXTURE_PATH = "assets/zero/shaders/core/sky_noise.png";
   private static final int UNIFORM_FLOATS = 40;
   private static final int VERTEX_FLOATS = 5;
   private static final float SUN_PATH_ROTATION = -70.0F;
   private static final Vector3f SUN_DIRECTION = new Vector3f(
         1.0F,
         MathHelper.cos((float) Math.toRadians(SUN_PATH_ROTATION)) * 2000.0F,
         -MathHelper.sin((float) Math.toRadians(SUN_PATH_ROTATION)) * 2000.0F
   ).normalize();

   private final Matrix4f viewToWorld = new Matrix4f();
   private final Quaternionf cameraConjugate = new Quaternionf();
   private final Vector3f flashDirectionView = new Vector3f(0.0F, 0.0F, -1.0F);
   private final Vector3f flashDirectionWorld = new Vector3f(0.0F, 0.0F, -1.0F);
   private final Vector3f cameraPosition = new Vector3f();

   private float tanHalfFovX = 1.0F;
   private float tanHalfFovY = 1.0F;
   private float timeCounter;
    private float flashIntensity;
    private volatile boolean captured;
    private boolean initialized;
    private boolean broken;
    private boolean debugLogged;
    private boolean gpuDebugLogged;

   private RenderPipeline pipeline;
   private GpuBuffer vertexBuffer;
   private GpuBuffer uniformBuffer;
   private GpuTexture noiseTexture;
   private GpuTextureView noiseView;
   private GpuSampler noiseSampler;

   private EndSkyboxRenderer() {
   }

   public static void captureState(ClientWorld world, float tickProgress, Camera camera, SkyRenderState skyState) {
      INSTANCE.capture(world, tickProgress, camera, skyState);
   }

   public static boolean shouldReplaceSky() {
      return INSTANCE.captured && !INSTANCE.broken;
   }

   public static void drawSkyPass() {
      INSTANCE.draw();
   }

   public static void destroy() {
      INSTANCE.destroyResources();
      INSTANCE.captured = false;
      INSTANCE.broken = false;
      INSTANCE.initialized = false;
   }

   private synchronized void destroyResources() {
      this.initialized = false;

      if (this.pipeline != null) {
         this.pipeline = null;
      }

      if (this.vertexBuffer != null) {
         this.vertexBuffer.close();
         this.vertexBuffer = null;
      }

      if (this.uniformBuffer != null) {
         this.uniformBuffer.close();
         this.uniformBuffer = null;
      }

      if (this.noiseView != null) {
         this.noiseView.close();
         this.noiseView = null;
      }

      if (this.noiseSampler != null) {
         this.noiseSampler = null;
      }

      if (this.noiseTexture != null) {
         this.noiseTexture.close();
         this.noiseTexture = null;
      }
   }

   private void capture(ClientWorld world, float tickProgress, Camera camera, SkyRenderState skyState) {
      this.captured = false;

      if (this.broken || world == null || camera == null) {
         return;
      }

      if (!SkyBox.isActive()) {
         return;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client.gameRenderer == null) {
         return;
      }

      this.viewToWorld.rotation(camera.getRotation().conjugate(this.cameraConjugate));

      float fov = ((GameRendererAccessor) client.gameRenderer).invokeGetFov(camera, tickProgress, true);
      Matrix4f projection = client.gameRenderer.getBasicProjectionMatrix(fov);
      if (projection.m00() == 0.0F || projection.m11() == 0.0F) {
         return;
      }

      this.tanHalfFovX = 1.0F / projection.m00();
      this.tanHalfFovY = 1.0F / projection.m11();

      if (!Float.isFinite(this.tanHalfFovX) || !Float.isFinite(this.tanHalfFovY)
            || this.tanHalfFovX <= 0.0F || this.tanHalfFovY <= 0.0F) {
         float fallbackFov = ((GameRendererAccessor) client.gameRenderer).invokeGetFov(camera, tickProgress, true);
         if (fallbackFov <= 0.0F) {
            fallbackFov = (float) client.options.getFov().getValue();
         }

         float tanHalfFov = (float) Math.tan(Math.toRadians(fallbackFov) * 0.5);
         float aspect = (float) client.getWindow().getFramebufferWidth()
               / Math.max(1.0F, (float) client.getWindow().getFramebufferHeight());
         this.tanHalfFovY = tanHalfFov;
         this.tanHalfFovX = tanHalfFov * aspect;
      }
      this.timeCounter = (world.getTime() % 24000000L) * 0.05F + tickProgress * 0.05F;
      this.cameraPosition.set(
         (float) camera.getCameraPos().x,
         (float) camera.getCameraPos().y,
         (float) camera.getCameraPos().z
      );

      this.flashIntensity = 0.0F;
      this.flashDirectionView.set(0.0F, 0.0F, -1.0F);
      if (skyState != null) {
         this.flashIntensity = Math.max(skyState.endFlashIntensity, 0.0F);

         float yaw = (float) Math.toRadians(skyState.endFlashYaw);
         float pitch = (float) Math.toRadians(skyState.endFlashPitch);
         float cosPitch = MathHelper.cos(pitch);
         this.flashDirectionView.set(
            -MathHelper.sin(yaw) * cosPitch,
            -MathHelper.sin(pitch),
            MathHelper.cos(yaw) * cosPitch
         );
      }

      this.viewToWorld.transformDirection(this.flashDirectionWorld.set(this.flashDirectionView));
      this.captured = true;
   }

   private void draw() {
      MinecraftClient client = MinecraftClient.getInstance();
      Framebuffer framebuffer = client.getFramebuffer();
      if (!this.captured || this.broken || framebuffer == null) {
         return;
      }

      try {
         this.ensureResources();
         if (this.broken) {
            return;
         }

         GpuDevice device = RenderSystem.getDevice();
         CommandEncoder encoder = device.createCommandEncoder();

         ByteBuffer uniformData = BufferUtils.createByteBuffer(UNIFORM_FLOATS * 4);
         FloatBuffer uniformFloats = uniformData.asFloatBuffer();
         this.viewToWorld.get(uniformFloats);
         uniformFloats.put(this.tanHalfFovX).put(this.tanHalfFovY).put(this.timeCounter).put(SUN_PATH_ROTATION);
         uniformFloats.put(this.flashDirectionWorld.x).put(this.flashDirectionWorld.y).put(this.flashDirectionWorld.z).put(this.flashIntensity);
         uniformFloats.put(this.cameraPosition.x).put(this.cameraPosition.y).put(this.cameraPosition.z).put(SkyBox.disk.get() ? 1.0F : 0.0F);
         uniformFloats.put(SUN_DIRECTION.x).put(SUN_DIRECTION.y).put(SUN_DIRECTION.z).put(0.0F);
         uniformFloats.put(SkyBox.nebula.get() ? 1.0F : 0.0F)
               .put(SkyBox.stars.get() ? 1.0F : 0.0F)
               .put(SkyBox.blackHole.get() ? 1.0F : 0.0F)
               .put(SkyBox.flashes.get() ? 1.0F : 0.0F);
          uniformFloats.put(SkyBox.starBrightness.get())
                .put(SkyBox.nebulaBrightness.get())
                .put(SkyBox.blackHoleSize.get())
                .put(SkyBox.flashBrightness.get());

          if (!this.debugLogged) {
             this.debugLogged = true;
             LOGGER.info(
                   "SkyBox debug: cam={} rot={} ray=({}, {}) toggles=({}, {}, {}, {}) strengths=({}, {}, {}, {}) disk={} uniformBytes={}",
                   this.cameraPosition,
                   this.viewToWorld,
                   this.tanHalfFovX,
                   this.tanHalfFovY,
                   SkyBox.nebula.get(),
                   SkyBox.stars.get(),
                   SkyBox.blackHole.get(),
                   SkyBox.flashes.get(),
                   SkyBox.starBrightness.get(),
                   SkyBox.nebulaBrightness.get(),
                   SkyBox.blackHoleSize.get(),
                   SkyBox.flashBrightness.get(),
                   SkyBox.disk.get(),
                   uniformData.remaining());
          }

          encoder.writeToBuffer(this.uniformBuffer.slice(), uniformData);
          this.debugGpuBuffer();

         RenderPass renderPass = encoder.createRenderPass(
               () -> "Zero SkyBox",
               framebuffer.getColorAttachmentView(),
               OptionalInt.empty(),
               framebuffer.getDepthAttachmentView(),
               OptionalDouble.empty()
         );

         try {
            renderPass.setPipeline(this.pipeline);
            renderPass.bindTexture("NoiseTex", this.noiseView, this.noiseSampler);
            renderPass.setUniform("SkyConfig", this.uniformBuffer);
            renderPass.setVertexBuffer(0, this.vertexBuffer);
            renderPass.draw(0, 3);
         } finally {
            renderPass.close();
         }
      } catch (Exception | LinkageError error) {
         this.broken = true;
         LOGGER.error("SkyBox render failed, falling back to vanilla sky", error);
      }
   }

   private void debugGpuBuffer() {
      if (this.gpuDebugLogged || this.uniformBuffer == null) {
         return;
      }

      this.gpuDebugLogged = true;
      int id = ((GlGpuBufferAccessor) (Object) this.uniformBuffer).zero$getId();
      FloatBuffer data = BufferUtils.createFloatBuffer(UNIFORM_FLOATS);
      GL15.glBindBuffer(GL32.GL_UNIFORM_BUFFER, id);
      int bindError = GL11.glGetError();
      GL15.glGetBufferSubData(GL32.GL_UNIFORM_BUFFER, 0L, data);
      int readError = GL11.glGetError();
      GL15.glBindBuffer(GL32.GL_UNIFORM_BUFFER, 0);
      LOGGER.info(
            "SkyConfig direct GPU readback: id={} bindError={} readError={} ray=({}, {}) disk={} toggles=({}, {}, {}, {}) strengths=({}, {}, {}, {})",
            id,
            bindError,
            readError,
            data.get(16),
            data.get(17),
            data.get(27),
            data.get(32),
            data.get(33),
            data.get(34),
            data.get(35),
            data.get(36),
            data.get(37),
            data.get(38),
            data.get(39));
   }

   private synchronized void ensureResources() {
      if (this.initialized || this.broken) {
         return;
      }

      GpuDevice device = RenderSystem.getDevice();

      this.uniformBuffer = device.createBuffer(
            () -> "Zero SkyBox uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_FLOATS * 4);

      ByteBuffer vertices = BufferUtils.createByteBuffer(VERTEX_FLOATS * 4 * 3);
      vertices.putFloat(-1.0F).putFloat(-1.0F).putFloat(0.0F).putFloat(0.0F).putFloat(0.0F);
      vertices.putFloat(3.0F).putFloat(-1.0F).putFloat(0.0F).putFloat(2.0F).putFloat(0.0F);
      vertices.putFloat(-1.0F).putFloat(3.0F).putFloat(0.0F).putFloat(0.0F).putFloat(2.0F);
      vertices.flip();
      this.vertexBuffer = device.createBuffer(
            () -> "Zero SkyBox mesh", GpuBuffer.USAGE_VERTEX, vertices);

      ByteBuffer noisePng = ResourceUtils.readBinary(NOISE_TEXTURE_PATH);
      NativeImage image;
      try {
         image = NativeImage.read(noisePng);
      } catch (java.io.IOException ioError) {
         throw new IllegalStateException("Failed to decode SkyBox noise texture", ioError);
      }

      this.noiseTexture = device.createTexture(
            () -> "Zero SkyBox noise",
            GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
            TextureFormat.RGBA8,
            image.getWidth(),
            image.getHeight(),
            1,
            1
      );
      device.createCommandEncoder().writeToTexture(this.noiseTexture, image);
      image.close();
      this.noiseView = device.createTextureView(this.noiseTexture);
      this.noiseSampler = device.createSampler(
            AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());

      this.pipeline = RenderPipeline.builder()
            .withLocation(Identifier.of("zero", "end_sky"))
            .withVertexShader(Identifier.of("zero", "core/end_sky"))
            .withFragmentShader(Identifier.of("zero", "core/end_sky"))
            .withSampler("NoiseTex")
            .withUniform("SkyConfig", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withoutBlend()
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.TRIANGLES)
            .build();

      CompiledRenderPipeline compiled = device.precompilePipeline(this.pipeline);
      if (compiled != null && !compiled.isValid()) {
         throw new IllegalStateException("SkyBox pipeline compilation failed (check shaders core/end_sky)");
      }

      if (compiled instanceof CompiledShaderPipeline shaderPipeline) {
         LOGGER.info("SkyBox program uniforms: {}", shaderPipeline.program().getUniforms());
      }

      this.initialized = true;
      LOGGER.info("SkyBox pipeline ready");
   }
}
