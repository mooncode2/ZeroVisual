package ru.zero.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Colors {
   private static final int DEFAULT_CLIENT_PRIMARY = -5487034;

   private Colors() {
   }

   public static int getDefaultClientPrimary() {
      return -5487034;
   }

   public static int getClientPrimary() {
      return -5487034;
   }
}
