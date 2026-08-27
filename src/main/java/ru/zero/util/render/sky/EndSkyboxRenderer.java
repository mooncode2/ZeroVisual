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
import ru.zero.mixin.GameRendererAccessor;
import ru.zero.module.impl.visuals.SkyBox;
import ru.zero.util.render.backends.gl.ResourceUtils;

/**
 * SkyBox: небо Эндера из Solas по Septonious (туманность, звёзды,
 * анимированная чёрная дыра, вспышки) вместо ванильного скайбокса.
 * Полностью заменяет ванильный sky-пасс во всех измерениях.
 */
@Environment(EnvType.CLIENT)
public final class EndSkyboxRenderer {
   private static final Logger LOGGER = LogManager.getLogger("Zero SkyBox");
   private static final EndSkyboxRenderer INSTANCE = new EndSkyboxRenderer();
   private static final String NOISE_TEXTURE_PATH = "assets/zero/shaders/skybox/tex/noise.png";
   private static final int UNIFORM_FLOATS = 40;
   private static final int VERTEX_FLOATS = 5;
   private static final float SUN_PATH_ROTATION = -70.0F;
   private static final Vector3f SUN_DIR = new Vector3f(
         1.0F,
         MathHelper.cos((float) Math.toRadians(SUN_PATH_ROTATION)) * 2000.0F,
         -MathHelper.sin((float) Math.toRadians(SUN_PATH_ROTATION)) * 2000.0F
   ).normalize();

   private final Matrix4f camRotInv = new Matrix4f();
   private final Quaternionf conjugate = new Quaternionf();
   private final Vector3f flashDirWorld = new Vector3f(0.0F, 0.0F, -1.0F);

   private float tanHalfFovX = 1.0F;
   private float tanHalfFovY = 1.0F;
   private float timeCounter;
   private float camX;
   private float camZ;
   private float flashIntensity;
   private volatile boolean captured;
   private boolean initialized;
   private boolean broken;

   private RenderPipeline pipeline;
   private GpuBuffer vertexBuffer;
   private GpuBuffer uniformBuffer;
   private GpuTexture noiseTexture;
   private GpuTextureView noiseView;
   private GpuSampler noiseSampler;

   private EndSkyboxRenderer() {
   }

   /**
    * Захват состояния камеры за кадр. Вызывается из миксина
    * SkyRendering.updateRenderState до выполнения проходов кадра.
    */
   public static void captureState(ClientWorld world, float tickProgress, Camera camera, SkyRenderState skyState) {
      INSTANCE.capture(world, tickProgress, camera, skyState);
   }

   /**
    * true - модуль активен и должен заменить ванильный sky-пасс своим.
    */
   public static boolean shouldReplaceSky() {
      return INSTANCE.captured && !INSTANCE.broken;
   }

   /**
    * Отрисовка неба внутри frame graph-пасса (выполняется вместо ванильного рендера неба).
    */
   public static void drawSkyPass() {
      INSTANCE.draw();
   }

   public static void destroy() {
      INSTANCE.destroyResources();
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

      //Матрица поворота view -> world (как в GameRenderer.renderWorld)
      this.camRotInv.rotation(camera.getRotation().conjugate(this.conjugate));

      //FOV и проекция для восстановления направления луча по пикселю экрана
      float fov = ((GameRendererAccessor) client.gameRenderer).invokeGetFov(camera, tickProgress, true);
      Matrix4f projection = client.gameRenderer.getBasicProjectionMatrix(fov);
      if (projection.m00() == 0.0F || projection.m11() == 0.0F) {
         return;
      }

      this.tanHalfFovX = 1.0F / projection.m00();
      this.tanHalfFovY = 1.0F / projection.m11();

      //Аналог frameTimeCounter из Iris (секунды, с плавным довеском частичного тика)
      this.timeCounter = (world.getTime() % 24000000L) * 0.05F + tickProgress * 0.05F;

      this.camX = (float) camera.getCameraPos().x;
      this.camZ = (float) camera.getCameraPos().z;

      //Вспышки Энда: направление берётся как в ванильном drawEndLightFlash
      float flashPitch = skyState != null ? skyState.endFlashPitch : 0.0F;
      this.flashIntensity = skyState != null ? Math.max(skyState.endFlashIntensity, 0.0F) : 0.0F;
      float pitchRad = (float) Math.toRadians(flashPitch);
      this.flashDirWorld.set(0.0F, -MathHelper.sin(pitchRad), -MathHelper.cos(pitchRad));
      this.camRotInv.transformDirection(this.flashDirWorld);

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
         this.camRotInv.get(uniformFloats);
         uniformFloats.put(this.tanHalfFovX).put(this.tanHalfFovY).put(this.timeCounter).put(0.0F);
         uniformFloats.put(this.flashDirWorld.x).put(this.flashDirWorld.y).put(this.flashDirWorld.z).put(this.flashIntensity);
         uniformFloats.put(this.camX).put(this.camZ).put(0.0F).put(0.0F);
         uniformFloats.put(SUN_DIR.x).put(SUN_DIR.y).put(SUN_DIR.z).put(SUN_PATH_ROTATION);
         uniformFloats.put(SkyBox.nebula.get() ? 1.0F : 0.0F)
               .put(SkyBox.stars.get() ? 1.0F : 0.0F)
               .put(SkyBox.blackHole.get() ? 1.0F : 0.0F)
               .put(SkyBox.flashes.get() ? 1.0F : 0.0F);
         uniformFloats.put(SkyBox.starBrightness.get())
               .put(SkyBox.nebulaBrightness.get())
               .put(SkyBox.blackHoleSize.get())
               .put(SkyBox.flashBrightness.get());
         encoder.writeToBuffer(this.uniformBuffer.slice(), uniformData);

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

   private synchronized void ensureResources() {
      if (this.initialized || this.broken) {
         return;
      }

      GpuDevice device = RenderSystem.getDevice();

      this.uniformBuffer = device.createBuffer(
            () -> "Zero SkyBox uniforms", GpuBuffer.USAGE_UNIFORM, UNIFORM_FLOATS * 4);

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
            AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, 0, OptionalDouble.empty());

      this.pipeline = RenderPipeline.builder()
            .withLocation(Identifier.of("zero", "end_sky"))
            .withVertexShader("zero:end_sky")
            .withFragmentShader("zero:end_sky")
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

      this.initialized = true;
      LOGGER.info("SkyBox pipeline ready");
   }
}
