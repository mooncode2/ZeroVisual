package ru.zero.util.color;

import java.awt.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.api.Theme;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.HueSetting;

@Environment(EnvType.CLIENT)
public final class ThemeColorUtil {

   private ThemeColorUtil() {
   }

   public static Color resolveThemeMainColor() {
      if (Zero.get != null && Zero.get.guiManager != null) {
         Theme theme = Zero.get.guiManager.getCurrentTheme();
         if (theme != null && theme.getMain() != null) {
            return theme.getMain();
         }
      }

      return null;
   }

   public static Color resolveColor(BooleanSetting syncWithTheme, HueSetting colorSetting) {
      if (syncWithTheme.get()) {
         Color themeColor = resolveThemeMainColor();
         if (themeColor != null) {
            return themeColor;
         }
      }

      return colorSetting.getColor();
   }

   public static int resolveRgb(BooleanSetting syncWithTheme, HueSetting colorSetting) {
      return resolveColor(syncWithTheme, colorSetting).getRGB();
   }
}
