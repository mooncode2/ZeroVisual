package ru.zero.event.player;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.event.Event;

@Environment(EnvType.CLIENT)
public class UsingItemEvent extends Event {
   byte type;

   @Generated
   public byte getType() {
      return this.type;
   }

   @Generated
   public void setType(byte type) {
      this.type = type;
   }

   @Generated
   public UsingItemEvent(byte type) {
      this.type = type;
   }
}
