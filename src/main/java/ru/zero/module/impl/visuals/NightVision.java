package ru.zero.module.impl.visuals;

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
import ru.zero.module.api.setting.impl.ModeSetting;

@IModule(
   name = "Zero Vision",
   description = " ",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class NightVision extends Module {
   public ModeSetting mode = new ModeSetting("Тип", "Гамма", "Гамма", "Эффект");

   public NightVision() {
      this.addSettings(new Setting[]{this.mode});
   }

   @Override
   public void onEnable() {
      super.onEnable();
      if (mc.worldRenderer != null && this.mode.is("Гамма")) {
         mc.worldRenderer.reload();
      }
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (mc.worldRenderer != null && this.mode.is("Гамма")) {
         mc.worldRenderer.reload();
      }

      if (this.mode.is("Эффект")) {
         mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
      }
   }

   @EventInit
   public void onUpdate(ClientTickEvent e) {
      if (mc.player != null) {
         if (this.mode.is("Гамма")) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
         }

         if (this.mode.is("Эффект")) {
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 300, 0, false, false));
         }
      }
   }
}
