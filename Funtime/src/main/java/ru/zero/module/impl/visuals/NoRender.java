package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.effect.StatusEffects;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventUpdate;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;

@IModule(
   name = "No Render",
   description = " ",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class NoRender extends Module {
   public static final BooleanSetting fire = new BooleanSetting("Огонь", true);
   public static final BooleanSetting fog = new BooleanSetting("Туман", true);

   public NoRender() {
      this.addSettings(new Setting[]{fire, fog});
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      if (mc.player != null) {
         if (mc.player.hasStatusEffect(StatusEffects.DARKNESS)) {
            mc.player.removeStatusEffect(StatusEffects.DARKNESS);
         }
      }
   }
}
