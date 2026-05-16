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

   /**
    * Аспект для {@code perspective()}: при выключенном модуле — реальный аспект framebuffer;
    * при включённом — выбранное пользователем соотношение (без смешения с GUI scale).
    */
   public static float getProjectionAspect() {
      if (mc == null || mc.getWindow() == null) {
         return 16.0F / 9.0F;
      }
      int fbW = mc.getWindow().getFramebufferWidth();
      int fbH = mc.getWindow().getFramebufferHeight();
      float framebufferAspect = (float) fbW / Math.max(1, fbH);
      if (Zero.get == null || Zero.get.manager == null) {
         return framebufferAspect;
      }
      AspectRation module = Zero.get.manager.get(AspectRation.class);
      if (module == null || !module.enable) {
         return framebufferAspect;
      }
      String mode = aspect.get();
      float forced = switch (mode) {
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
      return forced > 1.0e-4F ? forced : framebufferAspect;
   }
}
