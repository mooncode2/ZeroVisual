package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.event.EventInit;
import ru.zero.event.render.RenderEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.SliderSetting;

@IModule(name = "Test", description = "", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class Test extends Module {
   public static SliderSetting a6 = new SliderSetting("Громкость", 5.0F, 1.0F, 100.0F, 1.0F, false);
   // public MP3Player player = new MP3Player();

   public Test() {
      this.addSettings(new Setting[] { a6 });
   }

   @Override
   public void onEnable() {
      super.onEnable();
   }

   @EventInit
   public void onRender(RenderEvent e) {
   }

   @Override
   public void onDisable() {
      super.onDisable();
   }
}
