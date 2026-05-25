package ru.zero.module.impl.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.SliderSetting;

@IModule(
   name = "Optimizer",
   description = "Оптимизирует HUD/GUI и снижает нагрузку рендера",
   category = Category.Utils,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class Optimizer extends Module {
   public static final BooleanSetting optimizeBlur = new BooleanSetting("Оптимизировать блюр", true);
   public static final BooleanSetting optimizeHudAnimation = new BooleanSetting("Ускорить анимации HUD", true);
   public static final SliderSetting hudAnimationSpeed = new SliderSetting("Скорость анимации HUD", 0.2F, 0.08F, 0.35F, 0.01F, false);
   public static final BooleanSetting disableHudShadow = new BooleanSetting("Отключить тени HUD", true);
   public static final BooleanSetting limitNotifications = new BooleanSetting("Лимит уведомлений", true);
   public static final BooleanSetting disableWeather = new BooleanSetting("Отключить дождь/грому", false);
   public static final BooleanSetting disableFog = new BooleanSetting("Отключить туман", false);
   public static final SliderSetting maxNotifications = new SliderSetting("Макс уведомлений", 3.0F, 1.0F, 8.0F, 1.0F, false);
   public static final SliderSetting hudBlurRadius = new SliderSetting("Радиус HUD blur", 12.0F, 4.0F, 23.0F, 1.0F, false);
   public static final SliderSetting guiBlurRadius = new SliderSetting("Радиус GUI blur", 20.0F, 6.0F, 45.0F, 1.0F, false);

   public Optimizer() {
      this.addSettings(
         new Setting[]{
            optimizeBlur,
            optimizeHudAnimation,
            hudAnimationSpeed,
            disableHudShadow,
            limitNotifications,
            maxNotifications,
            disableWeather,
            disableFog,
            hudBlurRadius,
            guiBlurRadius
         }
      );
   }

   public static boolean isEnabled() {
      if (Zero.get == null || Zero.get.manager == null) {
         return false;
      }

      Optimizer optimizer = Zero.get.manager.get(Optimizer.class);
      return optimizer != null && optimizer.enable;
   }

   public static float optimizeHudBlur(float baseRadius) {
      if (!isEnabled() || !optimizeBlur.get()) {
         return baseRadius;
      }

      return Math.min(baseRadius, hudBlurRadius.current);
   }

   public static float optimizeGuiBlur(float baseRadius) {
      if (!isEnabled() || !optimizeBlur.get()) {
         return baseRadius;
      }

      return Math.min(baseRadius, guiBlurRadius.current);
   }

   public static int getMaxNotifications() {
      if (!isEnabled() || !limitNotifications.get()) {
         return Integer.MAX_VALUE;
      }

      return Math.max(1, Math.round(maxNotifications.current));
   }

   public static float getHudAnimationSpeed(float baseSpeed) {
      if (!isEnabled() || !optimizeHudAnimation.get()) {
         return baseSpeed;
      }

      return Math.max(baseSpeed, hudAnimationSpeed.current);
   }

   public static boolean shouldRenderHudShadow() {
      return !isEnabled() || !disableHudShadow.get();
   }

   public static boolean shouldDisableWeather() {
      return isEnabled() && disableWeather.get();
   }

   public static boolean shouldDisableFog() {
      return isEnabled() && disableFog.get();
   }
}
