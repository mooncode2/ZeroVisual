package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.SliderSetting;

@IModule(
   name = "CustomHand",
   description = "Шейдер темы на руке/предмете с переливом и свечением по краям",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class CustomHand extends Module {

   public static BooleanSetting fill = new BooleanSetting("Заливка темы", true);
   public static BooleanSetting edgeGlow = new BooleanSetting("Свечение по краям", true);
   public static SliderSetting glowStrength = new SliderSetting("Сила свечения", 1.0F, 0.0F, 3.0F, 0.05F, false)
         .hidden(() -> !edgeGlow.get());
   public static SliderSetting shimmerSpeed = new SliderSetting("Скорость перелива", 7.0F, 1.0F, 20.0F, 0.5F, false)
         .hidden(() -> !fill.get());

   public CustomHand() {
      this.addSettings(new Setting[] { fill, edgeGlow, glowStrength, shimmerSpeed });
   }
}
