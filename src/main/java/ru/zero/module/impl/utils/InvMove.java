package ru.zero.module.impl.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import ru.zero.event.EventInit;
import ru.zero.event.lifecycle.ClientTickEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.ui.gui.GuiClient;

/**
 * Разрешает управление в некоторых экранах; не использует модули Combat/Movement/Player.
 */
@IModule(
   name = "Inv Move",
   description = "",
   category = Category.Utils,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class InvMove extends Module {
   @EventInit
   public void onUpdate(ClientTickEvent e) {
      if (mc.player == null) {
         return;
      }

      // Двигаемся в интерфейсах инвентаря/GUI, но не в чате.
      if (mc.currentScreen instanceof ChatScreen) {
         return;
      }

      if (mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof GuiClient) {
         KeyBinding[] movementKeys = new KeyBinding[]{
            mc.options.forwardKey,
            mc.options.backKey,
            mc.options.leftKey,
            mc.options.rightKey,
            mc.options.jumpKey,
            mc.options.sprintKey
         };
         updateKeyBindingState(movementKeys);
      }
   }

   private void updateKeyBindingState(KeyBinding[] keyBindings) {
      for (KeyBinding keyBinding : keyBindings) {
         int keyCode = keyBinding.getDefaultKey().getCode();
         boolean isKeyPressed = InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), keyCode);
         keyBinding.setPressed(isKeyPressed);
      }
   }

   @Override
   public void onDisable() {
      // На выключении не оставляем "залипшие" клавиши.
      mc.options.forwardKey.setPressed(false);
      mc.options.backKey.setPressed(false);
      mc.options.leftKey.setPressed(false);
      mc.options.rightKey.setPressed(false);
      mc.options.jumpKey.setPressed(false);
      mc.options.sprintKey.setPressed(false);
      super.onDisable();
   }
}

