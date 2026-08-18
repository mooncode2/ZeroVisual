package ru.zero.module.impl.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import ru.zero.event.EventInit;
import ru.zero.event.lifecycle.ClientTickEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.MultiBooleanSetting;
import ru.zero.ui.gui.GuiClient;

/**
 * Позволяет двигаться при открытых экранах.
 * <p>
 * Состояние клавиш выставляется по реальной привязке ({@code getBoundKey}), а не по
 * дефолтной, и обновляется каждый клиентский тик до обработки движения.
 */
@IModule(
   name = "Inv Move",
   description = "Позволяет двигаться в открытых меню",
   category = Category.Utils,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class InvMove extends Module {
   public static MultiBooleanSetting screens = new MultiBooleanSetting(
      "Экраны",
      new BooleanSetting("Инвентарь", true),
      new BooleanSetting("Меню клиента", true),
      new BooleanSetting("Чат", false));
   public static BooleanSetting allowSprint = new BooleanSetting("Разрешать спринт", true);
   public static BooleanSetting allowJump = new BooleanSetting("Разрешать прыжок", true);

   public InvMove() {
      this.addSettings(new Setting[] { screens, allowSprint, allowJump });
   }

   @EventInit
   public void onUpdate(ClientTickEvent e) {
      if (!this.enable || mc.player == null || mc.getWindow() == null) {
         return;
      }

      Screen screen = mc.currentScreen;
      if (screen == null) {
         return;
      }

      if (!this.isAllowedScreen(screen)) {
         return;
      }

      this.apply(mc.options.forwardKey, true);
      this.apply(mc.options.backKey, true);
      this.apply(mc.options.leftKey, true);
      this.apply(mc.options.rightKey, true);
      this.apply(mc.options.jumpKey, allowJump.get());
      this.apply(mc.options.sprintKey, allowSprint.get());
   }

   private boolean isAllowedScreen(Screen screen) {
      if (screen instanceof ChatScreen) {
         return screens.get("Чат");
      }

      if (screen instanceof GuiClient) {
         return screens.get("Меню клиента");
      }

      return screens.get("Инвентарь");
   }

   /**
    * Читает физическое состояние привязанной клавиши и синхронизирует KeyBinding.
    */
   private void apply(KeyBinding binding, boolean allowed) {
      if (binding == null) {
         return;
      }

      if (!allowed) {
         binding.setPressed(false);
         return;
      }

      InputUtil.Key key = binding.boundKey;
      if (key == null || key.getCategory() != InputUtil.Type.KEYSYM) {
         binding.setPressed(false);
         return;
      }

      int code = key.getCode();
      if (code == InputUtil.UNKNOWN_KEY.getCode()) {
         binding.setPressed(false);
         return;
      }

      binding.setPressed(InputUtil.isKeyPressed(mc.getWindow(), code));
   }

   @Override
   public void onDisable() {
      this.release();
      super.onDisable();
   }

   private void release() {
      if (mc.options == null) {
         return;
      }

      mc.options.forwardKey.setPressed(false);
      mc.options.backKey.setPressed(false);
      mc.options.leftKey.setPressed(false);
      mc.options.rightKey.setPressed(false);
      mc.options.jumpKey.setPressed(false);
      mc.options.sprintKey.setPressed(false);
   }
}
