package ru.zero.module.impl.prime;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;

@IModule(
   name = "Prime Particles",
   description = "",
   category = Category.Prime,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class PrimeParticles extends Module {
   @Override
   public void onEnable() {
      super.onEnable();
      PrimeCommandUtil.sendCommand("/p cosmetics part");
   }

   @Override
   public void onDisable() {
      PrimeCommandUtil.sendCommand("/p cosmetics part off");
      super.onDisable();
   }
}
