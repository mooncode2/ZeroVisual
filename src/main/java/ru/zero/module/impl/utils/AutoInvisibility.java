package ru.zero.module.impl.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import ru.zero.event.EventInit;
import ru.zero.event.lifecycle.ClientTickEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.client.Lang;

@IModule(
   name = "Auto Invisibility",
   description = "Автоматически пьёт зелье невидимости из хотбара",
   category = Category.Utils,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class AutoInvisibility extends Module {
   private static final int POST_DRINK_COOLDOWN_TICKS = 600;

   public static SliderSetting tickDelay = new SliderSetting("Задержка действия (тики)", 4.0F, 2.0F, 20.0F, 1.0F, false);

   private int cooldownTicks;
   private int postDrinkCooldown;
   private final UtilsItemUseSequence itemUse = new UtilsItemUseSequence();

   public AutoInvisibility() {
      this.addSettings(new Setting[] { tickDelay });
   }

   @Override
   public void onDisable() {
      super.onDisable();
      itemUse.cancel();
      cooldownTicks = 0;
      postDrinkCooldown = 0;
   }

   @EventInit
   public void onTick(ClientTickEvent event) {
      if (mc.player == null || mc.interactionManager == null) {
         return;
      }

      StatusEffectInstance invisibility = mc.player.getStatusEffect(StatusEffects.INVISIBILITY);
      if (invisibility != null && invisibility.getDuration() > 0) {
         if (itemUse.isActive()) {
            itemUse.cancel();
         }
         postDrinkCooldown = 0;
         cooldownTicks = 0;
         return;
      }

      boolean itemUseWasActive = itemUse.isActive();
      if (itemUseWasActive) {
         itemUse.tick(Math.round(tickDelay.get()));
         if (!itemUse.isActive() && itemUse.consumeLastUseSuccessful()) {
            postDrinkCooldown = POST_DRINK_COOLDOWN_TICKS;
         }
         return;
      }

      if (postDrinkCooldown > 0) {
         postDrinkCooldown--;
         return;
      }

      cooldownTicks++;
      if (cooldownTicks < Math.round(tickDelay.get())) {
         return;
      }
      cooldownTicks = 0;

      int slot = UtilsUtil.findInvisibilityHotbarSlot();
      if (slot == -1) {
         UtilsUtil.failAndDisable(this, Lang.t("Невидимость не найдена в хотбаре"));
         return;
      }

      itemUse.start(slot, mc.player.getInventory().getSelectedSlot());
   }
}