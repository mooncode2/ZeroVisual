package ru.zero.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.zero.module.impl.client.MenuSettingsModule;

@Environment(EnvType.CLIENT)
public final class ZeroKeyBindings {
   public static final int DEFAULT_MENU_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;

   public static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("zero", "zero"));
   public static KeyBinding OPEN_MENU;

   private ZeroKeyBindings() {
   }

   public static void register() {
      OPEN_MENU = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.zero.open_menu", InputUtil.Type.KEYSYM, DEFAULT_MENU_KEY, CATEGORY)
      );
   }

   public static int getBoundKeyCode() {
      if (OPEN_MENU == null) {
         return DEFAULT_MENU_KEY;
      }

      InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(OPEN_MENU);
      return key != null && key.getCode() != InputUtil.UNKNOWN_KEY.getCode() ? key.getCode() : -1;
   }

   public static void setBoundKeyCode(int keyCode) {
      if (OPEN_MENU == null) {
         return;
      }

      if (keyCode <= 0) {
         OPEN_MENU.setBoundKey(InputUtil.UNKNOWN_KEY);
      } else {
         OPEN_MENU.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
      }
   }

   public static void syncFromMenuModule() {
      MenuSettingsModule module = MenuSettingsModule.getInstanceIfAvailable();
      if (module == null) {
         return;
      }

      if (module.bind > 0) {
         setBoundKeyCode(module.bind);
      } else {
         setBoundKeyCode(DEFAULT_MENU_KEY);
         module.bind = DEFAULT_MENU_KEY;
      }
   }

   public static void syncToMenuModule() {
      MenuSettingsModule module = MenuSettingsModule.getInstanceIfAvailable();
      if (module == null) {
         return;
      }

      int keyCode = getBoundKeyCode();
      module.bind = keyCode > 0 ? keyCode : -1;
   }
}
