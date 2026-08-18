package ru.zero.util.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.impl.utils.ClientTune;

@Environment(EnvType.CLIENT)
public final class ModuleSounds {
   private ModuleSounds() {
   }

   public static void playToggle(boolean enabled) {
      if (Zero.get == null || Zero.get.manager == null) {
         return;
      }

      ClientTune tune = Zero.get.manager.get(ClientTune.class);
      if (tune != null && tune.enable) {
         tune.playToggle(enabled);
         return;
      }

      ClientSounds.playModuleToggle(enabled);
   }
}
