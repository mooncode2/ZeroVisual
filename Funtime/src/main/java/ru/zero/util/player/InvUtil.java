package ru.zero.util.player;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import ru.zero.util.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class InvUtil implements IMinecraft {
   public static int find(Item item) {
      int slot = -1;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = mc.player.getInventory().getStack(i);
         if (!stack.isEmpty() && stack.getItem() == item) {
            slot = i;
            break;
         }
      }

      if (slot < 9 && slot != -1) {
         slot += 36;
      }

      return slot;
   }

   public static int find(Item item, boolean ignoreEnchanted) {
      return find(item, ignoreEnchanted, false);
   }

   public static int find(Item item, boolean ignoreEnchanted, boolean onlyEnchanted) {
      int slot = -1;
      if (onlyEnchanted) {
         for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item && stack.hasEnchantments()) {
               slot = i;
               break;
            }
         }
      } else {
         for (int ix = 0; ix < 36; ix++) {
            ItemStack stack = mc.player.getInventory().getStack(ix);
            if (!stack.isEmpty() && stack.getItem() == item && !stack.hasEnchantments()) {
               slot = ix;
               break;
            }
         }

         if (slot == -1 && !ignoreEnchanted) {
            for (int ixx = 0; ixx < 36; ixx++) {
               ItemStack stack = mc.player.getInventory().getStack(ixx);
               if (!stack.isEmpty() && stack.getItem() == item) {
                  slot = ixx;
                  break;
               }
            }
         }
      }

      if (slot < 9 && slot != -1) {
         slot += 36;
      }

      return slot;
   }

   @Generated
   private InvUtil() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
