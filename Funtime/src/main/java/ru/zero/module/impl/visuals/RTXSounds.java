package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventUpdate;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.ModeSetting;

@IModule(
   name = "RTX Sounds",
   description = " ",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class RTXSounds extends Module {
   public static ModeSetting performancePriority = new ModeSetting("Качество звука", "Производительность", "Производительность", "Качество");
   public static BooleanSetting stereo = new BooleanSetting("3д стерео", true);
   public static BooleanSetting tone = new BooleanSetting("Тон", true);

   public RTXSounds() {
      this.addSettings(new Setting[]{performancePriority, stereo, tone});
   }

   @Override
   public void onEnable() {
      super.onEnable();
      if (mc.player != null) {
         Zero.rtx.updateMixer();
      }
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      if (Zero.rtx != null) {
         Zero.rtx.updateMixer();
      }
   }

   @Override
   public void onDisable() {
      super.onDisable();
      Zero.rtx.setState(false);
   }
}
