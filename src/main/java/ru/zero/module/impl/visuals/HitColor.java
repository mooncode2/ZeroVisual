package ru.zero.module.impl.visuals;

import java.awt.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.lifecycle.ClientTickEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.util.color.ThemeColorUtil;
import ru.zero.util.render.HitColorOverlay;

@IModule(
      name = "HitColor",
      description = "Заменяет ванильный красный цвет урона на настраиваемый",
      category = Category.Visuals,
      bind = -1
)
@Environment(EnvType.CLIENT)
public class HitColor extends Module {
   public static final BooleanSetting syncWithTheme = new BooleanSetting("Синхронизировать с темой", true);
   /** Hue 0, full saturation — default matches vanilla hurt red. */
   public static final HueSetting color = new HueSetting("Цвет", 0.0F, 1.0F, 1.0F).hidden(() -> syncWithTheme.get());

   public HitColor() {
      this.addSettings(new Setting[] { syncWithTheme, color });
   }

   public static HitColor getModule() {
      if (Zero.get == null || Zero.get.manager == null) {
         return null;
      }

      return Zero.get.manager.get(HitColor.class);
   }

   public static Color resolveColor() {
      if (syncWithTheme.get()) {
         Color theme = ThemeColorUtil.resolveThemeMainColor();
         if (theme != null) {
            return theme;
         }
      }

      return color.getColor();
   }

   @EventInit
   public void onClientTick(ClientTickEvent event) {
      if (this.enable) {
         HitColorOverlay.applyFromModule();
      }
   }

   @Override
   public void onDisable() {
      HitColorOverlay.restoreVanilla();
      super.onDisable();
   }
}
