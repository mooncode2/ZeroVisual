package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.event.EventManager;
import ru.zero.event.player.EventRotation;

@Environment(EnvType.CLIENT)
@Mixin({Camera.class})
public abstract class CameraMixin {
   @Unique
   private EventRotation night$rotationEvent;
   @Unique
   private float night$originalYaw;
   @Unique
   private float night$originalPitch;

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Inject(
      method = {"update"},
      at = {@At("HEAD")}
   )
   private void onUpdateHead(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
      if (focusedEntity != null) {
         this.night$originalYaw = focusedEntity.getYaw(tickProgress);
         this.night$originalPitch = focusedEntity.getPitch(tickProgress);
         this.night$rotationEvent = new EventRotation(this.night$originalYaw, this.night$originalPitch, tickProgress);
         EventManager.call(this.night$rotationEvent);
      } else {
         this.night$rotationEvent = null;
      }
   }

   @Inject(
      method = {"update"},
      at = {@At("RETURN")}
   )
   private void onUpdateReturn(CallbackInfo ci) {
      if (this.night$rotationEvent != null
            && (this.night$rotationEvent.getYaw() != this.night$originalYaw
                  || this.night$rotationEvent.getPitch() != this.night$originalPitch)) {
         this.setRotation(this.night$rotationEvent.getYaw(), this.night$rotationEvent.getPitch());
      }

      this.night$rotationEvent = null;
   }
}
