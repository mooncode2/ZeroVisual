package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.SliderSetting;

@IModule(
   name = "SkyBox",
   category = Category.Visuals,
   description = "Небо Энда из Solas: туманность, звёзды, чёрная дыра и вспышки",
   bind = -1
)
@Environment(EnvType.CLIENT)
public class SkyBox extends Module {
   public static BooleanSetting nebula = new BooleanSetting("Туманность", true);
   public static BooleanSetting stars = new BooleanSetting("Звёзды", true);
   public static BooleanSetting blackHole = new BooleanSetting("Чёрная дыра", true);
   public static BooleanSetting flashes = new BooleanSetting("Вспышки", true);
   public static SliderSetting nebulaBrightness = new SliderSetting("Яркость туманности", 3.0F, 0.0F, 4.0F, 0.05F, false).hidden(() -> !nebula.get());
   public static SliderSetting starBrightness = new SliderSetting("Яркость звёзд", 1.0F, 0.0F, 2.0F, 0.05F, false).hidden(() -> !stars.get());
   public static SliderSetting blackHoleSize = new SliderSetting("Размер чёрной дыры", 1.0F, 0.25F, 3.0F, 0.05F, false).hidden(() -> !blackHole.get());
   public static SliderSetting flashBrightness = new SliderSetting("Яркость вспышек", 1.25F, 0.0F, 4.0F, 0.05F, false).hidden(() -> !flashes.get());

   public SkyBox() {
      this.addSettings(new Setting[]{nebula, stars, blackHole, flashes, nebulaBrightness, starBrightness, blackHoleSize, flashBrightness});
   }

   public static boolean isActive() {
      if (Zero.get == null || Zero.get.manager == null) {
         return false;
      }

      SkyBox module = Zero.get.manager.get(SkyBox.class);
      return module != null && module.enable;
   }
}
