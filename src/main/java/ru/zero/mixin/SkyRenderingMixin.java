package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.util.render.sky.EndSkyboxRenderer;

@Environment(EnvType.CLIENT)
@Mixin({ SkyRendering.class })
public class SkyRenderingMixin {
   @Inject(method = { "updateRenderState" }, at = { @At("TAIL") })
   private void zero$captureEndSkyState(ClientWorld world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo ci) {
      EndSkyboxRenderer.captureState(world, tickProgress, camera, state);
   }
}
