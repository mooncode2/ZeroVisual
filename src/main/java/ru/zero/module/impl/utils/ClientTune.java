package ru.zero.module.impl.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.client.CustomAudio;

@IModule(name = "Client Tune", description = "Звуки включения и выключения модулей", category = Category.Utils, bind = -1)
@Environment(EnvType.CLIENT)
public class ClientTune extends Module {
   public static ModeSetting pack = new ModeSetting("Звук", "Default", "Default", "Akron", "Celon", "Nuron", "Old");
   public static SliderSetting volume = new SliderSetting("Громкость", 75.0F, 0.0F, 100.0F, 1.0F, true);

   public ClientTune() {
      this.addSettings(new Setting[] { pack, volume });
   }

   public void playToggle(boolean enabled) {
      float vol = volume.get() / 100.0F;

      switch (pack.get()) {
         case "Akron" -> CustomAudio.playWav(enabled ? "akron" : "akroff", vol);
         case "Celon" -> CustomAudio.playWav(enabled ? "celon" : "celoff", vol);
         case "Nuron" -> CustomAudio.playWav(enabled ? "nuron" : "nuroff", vol);
         case "Old" -> CustomAudio.playWav(enabled ? "enableold" : "disableold", vol);
         default -> CustomAudio.playOgg("toggle", vol, enabled ? 1.08F : 0.92F);
      }
   }
}
