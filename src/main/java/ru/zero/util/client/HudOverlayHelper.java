package ru.zero.util.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.compat.LunarCompat;
import ru.zero.module.impl.visuals.Crosshair;
import ru.zero.module.impl.visuals.Hud;

@Environment(EnvType.CLIENT)
public final class HudOverlayHelper {

   private HudOverlayHelper() {
   }

   public static boolean isHudModuleEnabled() {
      if (Zero.get == null || Zero.get.manager == null) {
         return false;
      }

      Hud hud = Zero.get.manager.get(Hud.class);
      return hud != null && hud.enable;
   }

   /** Lunar uses the vanilla hotbar — custom hotbar needs DrawContext item pass in a different pipeline. */
   public static boolean shouldUseCustomHotbar() {
      if (LunarCompat.isLunarClient()) {
         return false;
      }

      return isHudModuleEnabled() && Hud.element.get("Хот бар");
   }

   public static boolean shouldUseCustomPotions() {
      if (LunarCompat.isLunarClient()) {
         return false;
      }

      return isHudModuleEnabled() && Hud.element.get("Список зелий");
   }

   public static boolean shouldUseCustomScoreboard() {
      if (LunarCompat.isLunarClient()) {
         return false;
      }

      return isHudModuleEnabled() && Hud.element.get("Скорборд");
   }

   public static boolean shouldUseCustomBossBar() {
      if (LunarCompat.isLunarClient()) {
         return false;
      }

      return isHudModuleEnabled() && Hud.element.get("Босс бар");
   }

   public static boolean shouldReplaceVanillaCrosshair() {
      if (Zero.get == null || Zero.get.manager == null) {
         return false;
      }

      Crosshair crosshair = Zero.get.manager.get(Crosshair.class);
      return crosshair != null && crosshair.enable;
   }

}
