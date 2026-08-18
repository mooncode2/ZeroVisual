package ru.zero.module.impl.funtime;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.event.EventInit;
import ru.zero.event.lifecycle.ClientTickEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.SliderSetting;

@IModule(
   name = "AutoEat",
   description = "Автоматически ест еду из хотбара при низкой сытости",
   category = Category.FunTime,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class AutoEat extends Module {
   public static SliderSetting tickDelay = new SliderSetting("Задержка действия (тики)", 4.0F, 2.0F, 20.0F, 1.0F, false);
   public static SliderSetting foodPoints = new SliderSetting("Поинты еды", 16.0F, 0.0F, 20.0F, 1.0F, false);

   private int cooldownTicks;
   private final FunTimeItemUseSequence itemUse = new FunTimeItemUseSequence();

   public AutoEat() {
      this.addSettings(new Setting[] { tickDelay, foodPoints });
   }

   @Override
   public void onDisable() {
      super.onDisable();
      itemUse.cancel();
      cooldownTicks = 0;
   }

   @EventInit
   public void onTick(ClientTickEvent event) {
      if (mc.player == null || mc.interactionManager == null) {
         return;
      }

      if (itemUse.isActive()) {
         itemUse.tick(Math.round(tickDelay.get()));
         return;
      }

      cooldownTicks++;
      if (cooldownTicks < Math.round(tickDelay.get())) {
         return;
      }
      cooldownTicks = 0;

      if (mc.player.getHungerManager().getFoodLevel() >= Math.round(foodPoints.get())) {
         return;
      }

      int slot = FunTimeUtil.findFoodHotbarSlot();
      if (slot == -1) {
         FunTimeUtil.failAndDisable(this, "Еда не найдена в хотбаре");
         return;
      }

      itemUse.start(slot, mc.player.getInventory().getSelectedSlot());
   }
}
