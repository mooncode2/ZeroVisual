package ru.zero.event.player;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import ru.zero.event.Event;

@Environment(EnvType.CLIENT)
public class AttackEvent extends Event {
   private Entity target;

   @Generated
   public Entity getTarget() {
      return this.target;
   }

   @Generated
   public void setTarget(Entity target) {
      this.target = target;
   }

   @Generated
   public AttackEvent(Entity target) {
      this.target = target;
   }
}
