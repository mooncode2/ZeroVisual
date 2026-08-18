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
   name = "No Fluid",
   description = "Убирает визуальные эффекты воды и лавы",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class NoFluid extends Module {
   public static final BooleanSetting waterFog = new BooleanSetting("Туман воды", true);
   public static final BooleanSetting lavaFog = new BooleanSetting("Туман лавы", true);
   public static final BooleanSetting waterOverlay = new BooleanSetting("Оверлей воды", true);
   public static final BooleanSetting lavaOverlay = new BooleanSetting("Оверлей лавы", true);

   public NoFluid() {
      this.addSettings(new Setting[] { waterFog, lavaFog, waterOverlay, lavaOverlay });
   }

   public static boolean shouldDisableFluidFog(boolean inWater, boolean inLava) {
      if (Zero.get == null || Zero.get.manager == null) {
         return false;
      }

      NoFluid module = Zero.get.manager.get(NoFluid.class);
      if (module == null || !module.enable) {
         return false;
      }

      if (inWater && waterFog.get()) {
         return true;
      }

      return inLava && lavaFog.get();
   }

   public static boolean shouldDisableWaterOverlay() {
      return isEnabled(waterOverlay);
   }

   public static boolean shouldDisableLavaOverlay() {
      return isEnabled(lavaOverlay);
   }

   private static boolean isEnabled(BooleanSetting setting) {
      if (Zero.get == null || Zero.get.manager == null) {
         return false;
      }

      NoFluid module = Zero.get.manager.get(NoFluid.class);
      return module != null && module.enable && setting.get();
   }
}
