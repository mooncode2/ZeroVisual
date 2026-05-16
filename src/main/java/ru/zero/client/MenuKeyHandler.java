package ru.zero.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.lifecycle.ClientTickEvent;
import ru.zero.ui.gui.GuiClient;

@Environment(EnvType.CLIENT)
public final class MenuKeyHandler {
   private int lastBoundKey = Integer.MIN_VALUE;

   public static void register() {
      ru.zero.event.EventManager.register(new MenuKeyHandler());
   }

   @EventInit
   public void onClientTick(ClientTickEvent event) {
      if (!Zero.isModInitialized() || Zero.get == null || ZeroKeyBindings.OPEN_MENU == null) {
         return;
      }

      MinecraftClient client = event.client();
      if (client == null || client.currentScreen != null) {
         return;
      }

      int boundKey = ZeroKeyBindings.getBoundKeyCode();
      if (boundKey != this.lastBoundKey) {
         this.lastBoundKey = boundKey;
         ZeroKeyBindings.syncToMenuModule();
      }

      while (ZeroKeyBindings.OPEN_MENU.wasPressed()) {
         GuiClient gui = Zero.get.getGuiClient();
         if (gui == null) {
            return;
         }

         client.setScreen(gui);
         if (client.mouse != null) {
            client.mouse.unlockCursor();
         }
      }
   }
}
