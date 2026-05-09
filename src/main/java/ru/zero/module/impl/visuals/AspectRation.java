package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.keyboard.ScaledResolution;

@IModule(
   name = "Aspect Ration",
   description = " ",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class AspectRation extends Module {
   public static final ModeSetting aspect = new ModeSetting(
      "Соотношение экрана", "16:9", "16:9", "4:3", "1:1", "16:10", "21:9", "32:9", "5:4", "2:1", "Кастомное"
   );
   public static final SliderSetting customAspect = new SliderSetting("Кастомое значние", 2.0F, 1.0F, 3.0F, 0.1F, false).hidden(() -> !aspect.is("Кастомное"));

   public AspectRation() {
      this.addSettings(new Setting[]{aspect, customAspect});
   }

   public static float getAspectRation() {
      ScaledResolution sr = new ScaledResolution(mc);
      if (!Zero.get.manager.getModule(AspectRation.class).enable) {
         return 0.0F;
      } else {
         float aspect1 = (float)sr.getWidth() / sr.getHeight();
         String var3 = aspect.get();

         float newAspect = switch (var3) {
            case "16:9" -> 1.7777778F;
            case "4:3" -> 1.3333334F;
            case "1:1" -> 1.0F;
            case "16:10" -> 1.6F;
            case "21:9" -> 2.3333333F;
            case "32:9" -> 3.5555556F;
            case "5:4" -> 1.25F;
            case "2:1" -> 2.0F;
            default -> customAspect.get();
         };
         return newAspect - aspect1;
      }
   }
}
