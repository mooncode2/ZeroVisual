package ru.zero.event.lifecycle;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import ru.zero.event.Event;

@Environment(EnvType.CLIENT)
public final class ClientTickEvent extends Event {
   private final MinecraftClient client;

   public ClientTickEvent(MinecraftClient client) {
      this.client = Objects.requireNonNull(client, "client");
   }

   public MinecraftClient client() {
      return this.client;
   }
}
