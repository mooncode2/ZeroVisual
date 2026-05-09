package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;

@IModule(
   name = "ClickGUI",
   description = "Настройки и стиль интерфейса GUI/HUD",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class ClickGUI extends Module {
   public static final BooleanSetting vanilla = new BooleanSetting("Ванильный", false);

   public ClickGUI() {
      this.addSettings(new Setting[]{vanilla});
      this.enable = true;
   }

   public static boolean isVanillaStyle() {
      if (Zero.get == null || Zero.get.manager == null) {
         return false;
      }

      ClickGUI clickGUI = Zero.get.manager.get(ClickGUI.class);
      return clickGUI != null && clickGUI.enable && vanilla.get();
   }

   @Override
   public void toggle() {
      if (!this.enable) {
         super.toggle();
      }
   }

   @Override
   public void setState(boolean enable) {
      if (!enable) {
         this.enable = true;
         return;
      }

      if (!this.enable) {
         super.setState(true);
      } else {
         this.enable = true;
      }
   }
}
