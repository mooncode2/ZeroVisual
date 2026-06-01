package ru.zero.module.impl.visuals;

import java.awt.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventChangeWorld;
import ru.zero.event.impl.EventUpdate;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.Theme;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.module.api.setting.impl.SliderSetting;

@IModule(
   name = "Custom World",
   category = Category.Visuals,
   description = "Render ESP on Player",
   bind = -1
)
@Environment(EnvType.CLIENT)
public class CustomWorld extends Module {
   public static ModeSetting timeOfDay = new ModeSetting("Время суток", "Ночь", "День", "Закат", "Рассвет", "Ночь", "Полночь", "Полдень");
   public static BooleanSetting useFog = new BooleanSetting("Туман", true);
   public static BooleanSetting alwaysClear = new BooleanSetting("Всегда ясно", false);
   public static BooleanSetting syncFogWithTheme = new BooleanSetting("Синхронизировать с темой", true).hidden(() -> !useFog.get());
   public static HueSetting fogColor = new HueSetting("Цвет тумана", 15.0F, 1.0F, 1.0F).hidden(() -> !useFog.get() || syncFogWithTheme.get());
   public static SliderSetting fogDistance = new SliderSetting("Дистанция тумана", 1.0F, 0.0F, 1.0F, 0.01F, false);
   public static long customTime = -1L;

   public CustomWorld() {
      this.addSettings(new Setting[]{timeOfDay, alwaysClear, useFog, syncFogWithTheme, fogColor, fogDistance});
   }

   public static boolean isActive() {
      if (Zero.get == null || Zero.get.manager == null) {
         return false;
      }

      CustomWorld module = Zero.get.manager.get(CustomWorld.class);
      return module != null && module.enable;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.updateTargetTime();
   }

   @Override
   protected void onConfigLoadEnable() {
      this.updateTargetTime();
   }

   @Override
   public void onDisable() {
      super.onDisable();
      customTime = -1L;
   }

   @Override
   protected void onConfigLoadDisable() {
      customTime = -1L;
   }

   @EventInit
   public void onWorldChange(EventChangeWorld event) {
      if (this.enable) {
         this.updateTargetTime();
      }
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      if (this.enable) {
         if (mc.world != null) {
            this.updateTargetTime();
         }
      } else if (customTime >= 0L) {
         customTime = -1L;
      }
   }

   private void updateTargetTime() {
      String currentMode = timeOfDay.get();
      switch (currentMode) {
         case "День":
         case "Р”РµРЅСЊ":
            customTime = 1000L;
            break;
         case "Закат":
         case "Р—Р°РєР°С‚":
            customTime = 12000L;
            break;
         case "Рассвет":
         case "Р Р°СЃСЃРІРµС‚":
            customTime = 23000L;
            break;
         case "Полночь":
         case "РџРѕР»РЅРѕС‡СЊ":
            customTime = 13000L;
            break;
         case "Ночь":
         case "РќРѕС‡СЊ":
            customTime = 18000L;
            break;
         case "Полдень":
         case "РџРѕР»РґРµРЅСЊ":
            customTime = 6000L;
            break;
         default:
            customTime = 18000L;
      }
   }

   public static Color getResolvedFogColor() {
      if (syncFogWithTheme.get() && Zero.get != null && Zero.get.guiManager != null) {
         Theme theme = Zero.get.guiManager.getCurrentTheme();
         if (theme != null && theme.getMain() != null) {
            return theme.getMain();
         }
      }

      return fogColor.getColor();
   }
}
