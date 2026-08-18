package ru.zero.mixin;

import java.awt.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import ru.zero.Zero;
import ru.zero.module.impl.utils.Optimizer;
import ru.zero.module.impl.visuals.CustomWorld;
import ru.zero.module.impl.visuals.NoFluid;
import ru.zero.module.impl.visuals.NoRender;

/**
 * Modifies vanilla fog parameters without cancelling {@link FogRenderer#applyFog}.
 * Keeping the original method alive is important: it rotates and updates Minecraft's
 * fog ring buffer, which Sodium also consumes. Cancelling it and writing the UBO by
 * hand desynchronised the ring buffer after config loading.
 */
@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
   private static final float MIN_FOG_RANGE = 0.125F;

   @Shadow
   protected abstract CameraSubmersionType getCameraSubmersionType(Camera camera);

   @Inject(method = "getFogColor", at = @At("TAIL"), cancellable = true)
   private void zero$getFogColor(
         Camera camera,
         float tickProgress,
         ClientWorld world,
         int viewDistance,
         float skyDarkness,
         CallbackInfoReturnable<Vector4f> cir
   ) {
      if (!CustomWorld.isActive() || !CustomWorld.useFog.get()) {
         return;
      }

      Color color = CustomWorld.getResolvedFogColor();
      cir.setReturnValue(new Vector4f(
            color.getRed() / 255.0F,
            color.getGreen() / 255.0F,
            color.getBlue() / 255.0F,
            color.getAlpha() / 255.0F));
   }

   @ModifyArgs(
         method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
         at = @At(
               value = "INVOKE",
               target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
         ),
         require = 0
   )
   private void zero$modifyFogDistances(
         Args args,
         Camera camera,
         int viewDistance,
         RenderTickCounter tickCounter,
         float skyDarkness,
         ClientWorld world
   ) {
      if (this.shouldDisableDistanceFog(camera, world)) {
         // Never use equal start/end values: the fog shader divides by (end-start).
         // The old implementation wrote far to both, producing NaN and white stripes.
         float start = Math.max(1024.0F, viewDistance * 64.0F);
         float end = start + 16.0F;
         args.set(3, start);
         args.set(4, end);
         args.set(5, start);
         args.set(6, end);
         args.set(7, end);
         args.set(8, end);
         return;
      }

      if (!CustomWorld.isActive() || !CustomWorld.useFog.get()) {
         return;
      }

      float factor = Math.max(0.0F, Math.min(1.0F, CustomWorld.fogDistance.get()));
      float environmentStart = finite((Float) args.get(3)) * factor;
      float environmentEnd = finite((Float) args.get(4)) * factor;
      float renderStart = finite((Float) args.get(5)) * factor;
      float renderEnd = finite((Float) args.get(6)) * factor;
      float skyEnd = finite((Float) args.get(7)) * factor;
      float cloudEnd = finite((Float) args.get(8)) * factor;

      // factor=0 is allowed and means immediate fog, but UBO ranges must remain valid.
      environmentEnd = Math.max(environmentEnd, environmentStart + MIN_FOG_RANGE);
      renderEnd = Math.max(renderEnd, renderStart + MIN_FOG_RANGE);
      skyEnd = Math.max(skyEnd, MIN_FOG_RANGE);
      cloudEnd = Math.max(cloudEnd, MIN_FOG_RANGE);

      args.set(3, environmentStart);
      args.set(4, environmentEnd);
      args.set(5, renderStart);
      args.set(6, renderEnd);
      args.set(7, skyEnd);
      args.set(8, cloudEnd);
   }

   private boolean shouldDisableDistanceFog(Camera camera, ClientWorld world) {
      if (Optimizer.shouldDisableFog()) {
         return true;
      }

      NoRender noRender = Zero.get != null && Zero.get.manager != null
            ? Zero.get.manager.get(NoRender.class)
            : null;
      if (noRender != null && noRender.enable && NoRender.fog.get()) {
         return true;
      }

      CameraSubmersionType submersion = this.getCameraSubmersionType(camera);
      boolean inWater = submersion == CameraSubmersionType.WATER;
      boolean inLava = submersion == CameraSubmersionType.LAVA;

      // Sodium can evaluate its fog state before/after vanilla's submersion cache.
      // Read the fluid at the actual camera position as a stable fallback.
      if (!inWater && !inLava && world != null) {
         BlockPos cameraPos = BlockPos.ofFloored(camera.getCameraPos());
         FluidState fluid = world.getFluidState(cameraPos);
         inWater = fluid.isIn(FluidTags.WATER);
         inLava = fluid.isIn(FluidTags.LAVA);
      }

      return NoFluid.shouldDisableFluidFog(
            inWater,
            inLava);
   }

   private static float finite(float value) {
      return Float.isFinite(value) ? value : 0.0F;
   }
}
