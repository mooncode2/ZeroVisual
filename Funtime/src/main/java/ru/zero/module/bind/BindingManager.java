package ru.zero.module.bind;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import ru.zero.Zero;
import ru.zero.client.ZeroKeyBindings;
import ru.zero.module.impl.client.MenuSettingsModule;
import ru.zero.event.EventInit;
import ru.zero.event.EventManager;
import ru.zero.event.input.KeyInputEvent;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.ui.gui.GuiScreen;

@Environment(EnvType.CLIENT)
public class BindingManager {
   private static final BindingManager INSTANCE = new BindingManager();
   private boolean initialized = false;
   private boolean awaitingCapture = false;

   public static BindingManager getInstance() {
      return INSTANCE;
   }

   public void initialize() {
      if (!this.initialized) {
         EventManager.register(this);
         this.initialized = true;
      }
   }

   @EventInit
   public void onKeyInput(KeyInputEvent event) {
      if (event.action() != GLFW.GLFW_PRESS) {
         return;
      }

      if (Module.isConfigLoadInProgress()) {
         return;
      }

      if (isIgnoredBindKey(event.key())) {
         return;
      }

      if (GuiScreen.activeBindSetting != null || GuiScreen.activeModuleBind != null || this.awaitingCapture) {
         return;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.currentScreen != null) {
         return;
      }

      if (Zero.get.manager == null) {
         return;
      }

      Module[] modules = Zero.get.manager.getBind(event.key());
      if (modules != null) {
         for (Module module : modules) {
            module.toggle();
         }
      }
   }

   private static boolean isIgnoredBindKey(int key) {
      if (key <= 0 || key == GLFW.GLFW_KEY_UNKNOWN) {
         return true;
      }

      return switch (key) {
         case GLFW.GLFW_KEY_PAUSE, GLFW.GLFW_KEY_PRINT_SCREEN, GLFW.GLFW_KEY_SCROLL_LOCK, GLFW.GLFW_KEY_NUM_LOCK,
               GLFW.GLFW_KEY_CAPS_LOCK, GLFW.GLFW_KEY_MENU,
               314, 315, 316, 317, 318, 319, 320, 321, 322, 323 -> true;
         default -> false;
      };
   }

   public void clearAllBindings() {
   }

   public void clearModuleBinds(String name) {
   }

   public void setAwaitingCapture(boolean awaiting) {
      this.awaitingCapture = awaiting;
   }

   public boolean isAwaitingCapture() {
      return this.awaitingCapture;
   }

   public void updateModuleBinding(Module module, int keyCode, BindingMode mode) {
      if (module != null) {
         module.bind = keyCode;
         if (module instanceof MenuSettingsModule) {
            ZeroKeyBindings.syncFromMenuModule();
         }
      }
   }

   public void putSettingBinding(Module module, Setting setting, BindingMode mode, int keyCode, Object targetValue) {
   }

   public void removeSettingBinding(String moduleName, String settingName) {
   }

   public Object getSettingBinding(String moduleName, String settingName) {
      return null;
   }

   public String formatKeyName(int keyCode) {
      if (keyCode == -1) {
         return "None";
      } else if (keyCode >= 65 && keyCode <= 90) {
         return String.valueOf((char)(65 + (keyCode - 65)));
      } else if (keyCode >= 48 && keyCode <= 57) {
         return String.valueOf((char)(48 + (keyCode - 48)));
      } else if (keyCode == 32) {
         return "Space";
      } else if (keyCode == 257) {
         return "Enter";
      } else if (keyCode == 256) {
         return "Escape";
      } else if (keyCode == 259) {
         return "Backspace";
      } else if (keyCode == 258) {
         return "Tab";
      } else if (keyCode == 340 || keyCode == 344) {
         return "Shift";
      } else if (keyCode == 341 || keyCode == 345) {
         return "Ctrl";
      } else if (keyCode == 342 || keyCode == 346) {
         return "Alt";
      } else {
         return keyCode >= 290 && keyCode <= 314 ? "F" + (keyCode - 290 + 1) : "Key " + keyCode;
      }
   }
}
