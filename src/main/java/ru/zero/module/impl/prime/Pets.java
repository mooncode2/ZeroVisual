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
   name = "Pets",
   description = "",
   category = Category.Prime,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class Pets extends Module {
   private final BooleanSetting allay = new BooleanSetting("Эллей", false);
   private final BooleanSetting bee = new BooleanSetting("Пчела", false);
   private final BooleanSetting vex = new BooleanSetting("Векс", false);
   private boolean syncing;
   private boolean prevAllay;
   private boolean prevBee;
   private boolean prevVex;

   public Pets() {
      this.addSettings(new Setting[] { this.allay, this.bee, this.vex });
      this.syncPrev();
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.handleStateChanges(true);
   }

   @Override
   public void onDisable() {
      PrimeCommandUtil.sendCommand("/p pet off");
      PrimeCommandUtil.sendCommand("/p pet off");
      super.onDisable();
   }

   private void handleStateChanges(boolean forceApply) {
      if (this.syncing) {
         this.syncPrev();
         return;
      }

      if (this.allay.get() && (forceApply || !this.prevAllay)) {
         this.selectOnly(this.allay);
         PrimeCommandUtil.sendCommand("/p pet allay");
      } else if (this.bee.get() && (forceApply || !this.prevBee)) {
         this.selectOnly(this.bee);
         PrimeCommandUtil.sendCommand("/p pet bee");
      } else if (this.vex.get() && (forceApply || !this.prevVex)) {
         this.selectOnly(this.vex);
         PrimeCommandUtil.sendCommand("/p pet vex");
      }

      this.syncPrev();
   }

   @EventInit
   public void onTick(ClientTickEvent event) {
      this.handleStateChanges(false);
   }

   private void selectOnly(BooleanSetting selected) {
      this.syncing = true;
      this.allay.set(selected == this.allay);
      this.bee.set(selected == this.bee);
      this.vex.set(selected == this.vex);
      this.syncing = false;
   }

   private void syncPrev() {
      this.prevAllay = this.allay.get();
      this.prevBee = this.bee.get();
      this.prevVex = this.vex.get();
   }
}
