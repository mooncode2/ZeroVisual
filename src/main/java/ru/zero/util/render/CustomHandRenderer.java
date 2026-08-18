package ru.zero.util.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemDisplayContext;

@Environment(EnvType.CLIENT)
public final class CustomHandRenderer {

   private CustomHandRenderer() {
   }

   public static boolean isFirstPersonItem(ItemDisplayContext ctx) {
      if (ctx == null) return false;
      return ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
   }
}
