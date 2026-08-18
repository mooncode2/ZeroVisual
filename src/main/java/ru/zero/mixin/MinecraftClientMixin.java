package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.Zero;
import ru.zero.compat.LunarCompat;
import ru.zero.event.EventManager;
import ru.zero.event.impl.EventChangeWorld;
import ru.zero.event.lifecycle.ClientTickEvent;

@Environment(EnvType.CLIENT)
@Mixin({ MinecraftClient.class })
public abstract class MinecraftClientMixin {
   @Inject(method = { "tick" }, at = { @At("HEAD") })
   private void initRenderer(CallbackInfo ci) {
      if (Zero.isModInitialized()) {
         Zero.ensureRendererInitialized();
      }
   }

    @Inject(method = { "tick" }, at = { @At("TAIL") })
    private void publishClientTick(CallbackInfo ci) {
       if (Zero.isModInitialized()) {
          MinecraftClient client = (MinecraftClient) (Object) this;
          if (!client.isPaused()) {
             ClientPlayerEntity player = client.player;
             ClientWorld world = client.world;
             if (player != null && world != null) {
                LunarCompat.onClientTickInWorld();
                if (EventManager.hasListeners(ClientTickEvent.class)) {
                   EventManager.call(new ClientTickEvent(client));
                }
             }
          }
       }
    }

    @Inject(method = { "joinWorld" }, at = { @At("TAIL") })
    public void loadWorld(CallbackInfo ci) {
       if (EventManager.hasListeners(EventChangeWorld.class)) {
          EventManager.call(new EventChangeWorld());
       }
    }
}
