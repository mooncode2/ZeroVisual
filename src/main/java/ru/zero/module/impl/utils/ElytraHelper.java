package ru.zero.module.impl.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import ru.zero.event.EventInit;
import ru.zero.event.lifecycle.ClientTickEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BindSettings;
import ru.zero.module.api.setting.impl.BooleanSetting;

@IModule(
   name = "ElytraHelper",
   description = "",
   category = Category.Utils,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class ElytraHelper extends Module {
   public static BindSettings actionBind = new BindSettings("Бинд", -1);
   public static BooleanSetting autoTakeoff = new BooleanSetting("Автоматически взлетать", false);
   public static BooleanSetting swapOnGround = new BooleanSetting("Снимать на земле", false);

   private boolean lastBindDown;
   private boolean wasFallFlying;

   private EquipUseState equipState = EquipUseState.idle();
   private FireworkState fireworkState = FireworkState.idle();

   public ElytraHelper() {
      this.addSettings(new Setting[] { actionBind, autoTakeoff, swapOnGround });
   }

   @EventInit
   public void onTick(ClientTickEvent event) {
      if (mc.player == null || mc.interactionManager == null || mc.getWindow() == null) {
         equipState = EquipUseState.idle();
         fireworkState = FireworkState.idle();
         wasFallFlying = false;
         return;
      }

      if (equipState.phase != EquipPhase.IDLE) {
         tickEquipState();
         return;
      }

      if (fireworkState.phase != FireworkPhase.IDLE) {
         tickFireworkState();
         return;
      }

      boolean bindDown = isBindDown(actionBind.get());
      boolean bindPressed = bindDown && !lastBindDown;
      lastBindDown = bindDown;
      if (bindPressed) {
         handleToggleByBind();
         return;
      }

      // Снимать на земле после полета.
      if (swapOnGround.get()) {
         ItemStack chestNow = mc.player.getEquippedStack(EquipmentSlot.CHEST);
         boolean elytraFlightLikeState = chestNow.isOf(Items.ELYTRA)
            && !mc.player.isOnGround()
            && mc.player.getVelocity().y < -0.02;
         if (elytraFlightLikeState) {
            wasFallFlying = true;
         } else if (wasFallFlying && mc.player.isOnGround()) {
            wasFallFlying = false;
            tryEquipBestChestplate(false);
         }
      } else {
         wasFallFlying = false;
      }
   }

   private void handleToggleByBind() {
      ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
      boolean wearingElytra = chest.isOf(Items.ELYTRA);
      if (wearingElytra) {
         tryEquipBestChestplate(false);
      } else {
         tryEquipBestElytra(autoTakeoff.get());
      }
   }

   private void tryEquipBestElytra(boolean withTakeoff) {
      int slot = findBestElytraInventoryIndex();
      if (slot == -1) return;
      startEquipUse(slot, withTakeoff);
   }

   private void tryEquipBestChestplate(boolean withTakeoff) {
      int slot = findBestChestplateInventoryIndex();
      if (slot == -1) return;
      startEquipUse(slot, withTakeoff);
   }

   private void startEquipUse(int invIndex, boolean withTakeoff) {
      if (invIndex >= 0 && invIndex <= 8) {
         equipState = EquipUseState.forHotbar(invIndex, withTakeoff);
      } else {
         equipState = EquipUseState.forInventory(invIndex, withTakeoff);
      }
   }

   private void tickEquipState() {
      if (mc.player == null || mc.interactionManager == null) {
         equipState = EquipUseState.idle();
         return;
      }

      switch (equipState.phase) {
         case SWAP_IN -> {
            int fromSlotId = toHandlerSlotId(equipState.invIndex);
            if (fromSlotId == -1) {
               equipState = EquipUseState.idle();
               return;
            }
            clickSwap(fromSlotId, equipState.targetHotbar);
            equipState.phase = EquipPhase.SELECT;
         }
         case SELECT -> {
            mc.player.getInventory().setSelectedSlot(equipState.targetHotbar);
            equipState.phase = EquipPhase.USE;
         }
         case USE -> {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            equipState.phase = EquipPhase.RESTORE_SELECT;
         }
         case RESTORE_SELECT -> {
            mc.player.getInventory().setSelectedSlot(equipState.restoreSelectedHotbar);
            equipState.phase = equipState.fromInventory ? EquipPhase.SWAP_BACK : EquipPhase.FINISH;
         }
         case SWAP_BACK -> {
            int fromSlotId = toHandlerSlotId(equipState.invIndex);
            if (fromSlotId == -1) {
               equipState = EquipUseState.idle();
               return;
            }
            clickSwap(fromSlotId, equipState.targetHotbar);
            equipState.phase = EquipPhase.FINISH;
         }
         case FINISH -> {
            boolean shouldFirework = equipState.useFireworkAfter;
            equipState = EquipUseState.idle();
            if (shouldFirework) {
               startFireworkUse();
            }
         }
         case IDLE -> equipState = EquipUseState.idle();
      }
   }

   private void startFireworkUse() {
      int hotbarSlot = findInHotbar(Items.FIREWORK_ROCKET);
      if (hotbarSlot != -1) {
         fireworkState = FireworkState.forHotbar(hotbarSlot);
         return;
      }
      int invIndex = findInInventory(Items.FIREWORK_ROCKET);
      if (invIndex != -1) {
         fireworkState = FireworkState.forInventory(invIndex);
      }
   }

   private void tickFireworkState() {
      if (mc.player == null || mc.interactionManager == null) {
         fireworkState = FireworkState.idle();
         return;
      }

      switch (fireworkState.phase) {
         case SWAP_IN -> {
            int fromSlotId = toHandlerSlotId(fireworkState.invIndex);
            if (fromSlotId == -1) {
               fireworkState = FireworkState.idle();
               return;
            }
            clickSwap(fromSlotId, fireworkState.prevSelectedHotbar);
            fireworkState.phase = FireworkPhase.SELECT;
         }
         case SELECT -> {
            mc.player.getInventory().setSelectedSlot(fireworkState.prevSelectedHotbar);
            fireworkState.phase = FireworkPhase.USE;
         }
         case USE -> {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            fireworkState.phase = FireworkPhase.RESTORE_SELECT;
         }
         case RESTORE_SELECT -> {
            mc.player.getInventory().setSelectedSlot(fireworkState.restoreSelectedHotbar);
            fireworkState.phase = fireworkState.fromInventory ? FireworkPhase.SWAP_BACK : FireworkPhase.IDLE;
         }
         case SWAP_BACK -> {
            int fromSlotId = toHandlerSlotId(fireworkState.invIndex);
            if (fromSlotId == -1) {
               fireworkState = FireworkState.idle();
               return;
            }
            clickSwap(fromSlotId, fireworkState.prevSelectedHotbar);
            fireworkState.phase = FireworkPhase.IDLE;
         }
         case IDLE -> fireworkState = FireworkState.idle();
      }
   }

   private void clickSwap(int fromSlotId, int hotbarIndex) {
      if (mc.player == null || mc.interactionManager == null) return;
      ScreenHandler handler = mc.player.currentScreenHandler;
      if (handler == null) return;
      mc.interactionManager.clickSlot(handler.syncId, fromSlotId, hotbarIndex, SlotActionType.SWAP, mc.player);
   }

   private boolean isBindDown(int keyCode) {
      if (keyCode <= 0) return false;
      if (keyCode <= -100) {
         int mouseButton = -100 - keyCode;
         long handle = mc.getWindow().getHandle();
         return handle != 0L && GLFW.glfwGetMouseButton(handle, mouseButton) == 1;
      }
      return InputUtil.isKeyPressed(mc.getWindow(), keyCode);
   }

   private int findBestElytraInventoryIndex() {
      int best = -1;
      double bestScore = -1.0;
      for (int i = 0; i <= 35; i++) {
         ItemStack stack = mc.player.getInventory().getStack(i);
         if (!stack.isOf(Items.ELYTRA)) continue;
         double score = scoreItem(stack, false);
         if (score > bestScore) {
            bestScore = score;
            best = i;
         }
      }
      return best;
   }

   private int findBestChestplateInventoryIndex() {
      int best = -1;
      double bestScore = -1.0;
      for (int i = 0; i <= 35; i++) {
         ItemStack stack = mc.player.getInventory().getStack(i);
         if (!isChestplate(stack)) continue;
         double score = scoreItem(stack, true);
         if (score > bestScore) {
            bestScore = score;
            best = i;
         }
      }
      return best;
   }

   private static boolean isChestplate(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return false;
      return stack.isOf(Items.NETHERITE_CHESTPLATE)
         || stack.isOf(Items.DIAMOND_CHESTPLATE)
         || stack.isOf(Items.IRON_CHESTPLATE)
         || stack.isOf(Items.GOLDEN_CHESTPLATE)
         || stack.isOf(Items.CHAINMAIL_CHESTPLATE)
         || stack.isOf(Items.LEATHER_CHESTPLATE);
   }

   private static double scoreItem(ItemStack stack, boolean includeArmorValue) {
      if (stack == null || stack.isEmpty()) return -1.0;
      double score = 0.0;
      if (stack.isDamageable()) {
         int max = Math.max(1, stack.getMaxDamage());
         int remaining = Math.max(0, max - stack.getDamage());
         score += remaining;
         score += (remaining / (double) max) * 800.0;
      } else {
         score += 400.0;
      }

      if (stack.hasEnchantments()) {
         score += 300.0;
      }

      // Для нагрудников в этих маппингах не тянем ArmorItem-класс:
      // выбираем по прочности и наличию зачарований.
      return score;
   }

   private int findInHotbar(Item item) {
      for (int i = 0; i < 9; i++) {
         if (mc.player.getInventory().getStack(i).isOf(item)) return i;
      }
      return -1;
   }

   private int findInInventory(Item item) {
      for (int i = 9; i <= 35; i++) {
         if (mc.player.getInventory().getStack(i).isOf(item)) return i;
      }
      return -1;
   }

   private static int toHandlerSlotId(int invIndex) {
      if (invIndex >= 9 && invIndex <= 35) return invIndex;
      if (invIndex >= 0 && invIndex <= 8) return 36 + invIndex;
      return -1;
   }

   private enum EquipPhase {
      IDLE,
      SWAP_IN,
      SELECT,
      USE,
      RESTORE_SELECT,
      SWAP_BACK,
      FINISH
   }

   private static final class EquipUseState {
      private EquipPhase phase;
      private final boolean fromInventory;
      private final int invIndex;
      private final int targetHotbar;
      private final int restoreSelectedHotbar;
      private final boolean useFireworkAfter;

      private EquipUseState(EquipPhase phase, boolean fromInventory, int invIndex, int targetHotbar, int restoreSelectedHotbar, boolean useFireworkAfter) {
         this.phase = phase;
         this.fromInventory = fromInventory;
         this.invIndex = invIndex;
         this.targetHotbar = targetHotbar;
         this.restoreSelectedHotbar = restoreSelectedHotbar;
         this.useFireworkAfter = useFireworkAfter;
      }

      static EquipUseState idle() {
         return new EquipUseState(EquipPhase.IDLE, false, -1, -1, -1, false);
      }

      static EquipUseState forHotbar(int hotbarSlot, boolean useFireworkAfter) {
         int restore = Module.mc.player != null ? Module.mc.player.getInventory().getSelectedSlot() : 0;
         return new EquipUseState(EquipPhase.SELECT, false, -1, hotbarSlot, restore, useFireworkAfter);
      }

      static EquipUseState forInventory(int invIndex, boolean useFireworkAfter) {
         int selected = Module.mc.player != null ? Module.mc.player.getInventory().getSelectedSlot() : 0;
         return new EquipUseState(EquipPhase.SWAP_IN, true, invIndex, selected, selected, useFireworkAfter);
      }
   }

   private enum FireworkPhase {
      IDLE,
      SWAP_IN,
      SELECT,
      USE,
      RESTORE_SELECT,
      SWAP_BACK
   }

   private static final class FireworkState {
      private FireworkPhase phase;
      private final boolean fromInventory;
      private final int invIndex;
      private final int prevSelectedHotbar;
      private final int restoreSelectedHotbar;

      private FireworkState(FireworkPhase phase, boolean fromInventory, int invIndex, int prevSelectedHotbar, int restoreSelectedHotbar) {
         this.phase = phase;
         this.fromInventory = fromInventory;
         this.invIndex = invIndex;
         this.prevSelectedHotbar = prevSelectedHotbar;
         this.restoreSelectedHotbar = restoreSelectedHotbar;
      }

      static FireworkState idle() {
         return new FireworkState(FireworkPhase.IDLE, false, -1, -1, -1);
      }

      static FireworkState forHotbar(int hotbarSlot) {
         int restore = Module.mc.player != null ? Module.mc.player.getInventory().getSelectedSlot() : 0;
         return new FireworkState(FireworkPhase.SELECT, false, -1, hotbarSlot, restore);
      }

      static FireworkState forInventory(int invIndex) {
         int selected = Module.mc.player != null ? Module.mc.player.getInventory().getSelectedSlot() : 0;
         return new FireworkState(FireworkPhase.SWAP_IN, true, invIndex, selected, selected);
      }
   }
}

