package ru.zero.module.impl.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.client.CustomAudio;

@IModule(name = "Hit Sound", description = "Кастомные звуки удара", category = Category.Utils, bind = -1)
@Environment(EnvType.CLIENT)
public class HitSound extends Module {
   public static ModeSetting sound = new ModeSetting("Звук", "Bell", "Bell", "Bonk", "Bubble", "Metallic", "TF 2");
   public static BooleanSetting critOnly = new BooleanSetting("Только при крите", false);
   public static SliderSetting volume = new SliderSetting("Громкость", 75.0F, 0.0F, 100.0F, 1.0F, true);

   public HitSound() {
      this.addSettings(new Setting[] { sound, critOnly, volume });
   }

   public void playSelectedSound(boolean crit) {
      float vol = volume.get() / 100.0F;
      String selected = sound.get();

      switch (selected) {
         case "Bell" -> CustomAudio.playWav("bell", vol);
         case "Bonk" -> CustomAudio.playWav("bonk", vol);
         case "Bubble" -> CustomAudio.playWav("bubble", vol);
         case "Metallic" -> CustomAudio.playWav("metallic", vol);
         case "TF 2" -> CustomAudio.playRandomTf2Crit(vol);
         default -> CustomAudio.playWav("bell", vol);
      }
   }
}
