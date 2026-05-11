package ru.zero.module.impl.prime;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.event.EventInit;
import ru.zero.event.lifecycle.ClientTickEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;

@IModule(
   name = "Kill effect",
   description = "",
   category = Category.Prime,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class KillEffect extends Module {
   private final BooleanSetting lightning = new BooleanSetting("Молния", false);
   private final BooleanSetting lava = new BooleanSetting("Лава", false);
   private final BooleanSetting cloud = new BooleanSetting("Облако", false);
   private boolean syncing;
   private boolean prevLightning;
   private boolean prevLava;
   private boolean prevCloud;

   public KillEffect() {
      this.addSettings(new Setting[] { this.lightning, this.lava, this.cloud });
      this.syncPrev();
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.handleStateChanges(true);
   }

   @Override
   public void onDisable() {
      PrimeCommandUtil.sendCommand("/p cosmetics dragon_breath");
      super.onDisable();
   }

   @EventInit
   public void onTick(ClientTickEvent event) {
      this.handleStateChanges(false);
   }

   private void handleStateChanges(boolean forceApply) {
      if (this.syncing) {
         this.syncPrev();
         return;
      }

      if (this.lightning.get() && (forceApply || !this.prevLightning)) {
         this.selectOnly(this.lightning);
         PrimeCommandUtil.sendCommand("/p cosmetics light");
      } else if (this.lava.get() && (forceApply || !this.prevLava)) {
         this.selectOnly(this.lava);
         PrimeCommandUtil.sendCommand("/p cosmetics lava");
      } else if (this.cloud.get() && (forceApply || !this.prevCloud)) {
         this.selectOnly(this.cloud);
         PrimeCommandUtil.sendCommand("/p cosmetics cloud");
      }

      this.syncPrev();
   }

   private void selectOnly(BooleanSetting selected) {
      this.syncing = true;
      this.lightning.set(selected == this.lightning);
      this.lava.set(selected == this.lava);
      this.cloud.set(selected == this.cloud);
      this.syncing = false;
   }

   private void syncPrev() {
      this.prevLightning = this.lightning.get();
      this.prevLava = this.lava.get();
      this.prevCloud = this.cloud.get();
   }
}
