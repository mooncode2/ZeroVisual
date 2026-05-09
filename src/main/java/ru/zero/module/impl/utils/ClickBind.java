package ru.zero.module.impl.utils;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
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
import ru.zero.ui.gui.GuiClient;
import ru.zero.ui.gui.GuiScreen;

@IModule(
   name = "ClickBind",
   description = "",
   category = Category.Utils,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class ClickBind extends Module {
   private static final int UNSET = -1;

   public static BindSettings firework = new BindSettings("Феерверк", UNSET);
   public static BindSettings pearl = new BindSettings("Эндер жемчуг", UNSET);
   public static BindSettings windCharge = new BindSettings("Заряд ветра", UNSET);

   private boolean lastFireworkDown;
   private boolean lastPearlDown;
   private boolean lastWindDown;

   private int prevFireworkKey = UNSET;
   private int prevPearlKey = UNSET;
   private int prevWindKey = UNSET;
   private int suppressTicks = 0;

   private ActionState state = ActionState.idle();

   public ClickBind() {
      this.addSettings(new Setting[] { firework, pearl, windCharge });
   }

   @EventInit
   public void onTick(ClientTickEvent e) {
      if (mc.player == null || mc.interactionManager == null || mc.getWindow() == null) {
         state = ActionState.idle();
         return;
      }

      // Если мы в GUI или сейчас назначаем бинд — не даём модулю сработать.
      if (mc.currentScreen instanceof GuiClient || GuiScreen.activeBindSetting != null) {
         suppressTicks = Math.max(suppressTicks, 5);
      }

      // Если ключи поменялись (только что назначили бинд) — тоже не триггерим сразу.
      if (firework.get() != prevFireworkKey || pearl.get() != prevPearlKey || windCharge.get() != prevWindKey) {
         prevFireworkKey = firework.get();
         prevPearlKey = pearl.get();
         prevWindKey = windCharge.get();
         suppressTicks = Math.max(suppressTicks, 5);
      }

      if (suppressTicks > 0) {
         suppressTicks--;
         // синхронизируем edge-detector, чтобы после подавления не выстрелило "на отпуск/нажатие"
         lastFireworkDown = isKeyDown(firework.get());
         lastPearlDown = isKeyDown(pearl.get());
         lastWindDown = isKeyDown(windCharge.get());
         return;
      }

      // Выполняем отложенную операцию (на пару тиков)
      if (state.phase != Phase.IDLE) {
         tickState();
         return;
      }

      boolean fireworkDown = isKeyDown(firework.get());
      boolean pearlDown = isKeyDown(pearl.get());
      boolean windDown = isKeyDown(windCharge.get());

      boolean fireworkPressed = fireworkDown && !lastFireworkDown;
      boolean pearlPressed = pearlDown && !lastPearlDown;
      boolean windPressed = windDown && !lastWindDown;

      lastFireworkDown = fireworkDown;
      lastPearlDown = pearlDown;
      lastWindDown = windDown;

      if (fireworkPressed) {
         startUse(Items.FIREWORK_ROCKET);
      } else if (pearlPressed) {
         startUse(Items.ENDER_PEARL);
      } else if (windPressed) {
         startUse(Items.WIND_CHARGE);
      }
   }

   private boolean isKeyDown(int keyCode) {
      if (keyCode <= 0) {
         return false;
      }
      // В конфиге бинды мыши кодируются как -100 - button (как в GUI биндах).
      if (keyCode <= -100) {
         int mouseButton = -100 - keyCode;
         long handle = mc.getWindow().getHandle();
         return handle != 0L && GLFW.glfwGetMouseButton(handle, mouseButton) == 1;
      }
      return InputUtil.isKeyPressed(mc.getWindow(), keyCode);
   }

   private void startUse(Item item) {
      int hotbarSlot = findInHotbar(item);
      if (hotbarSlot != -1) {
         state = ActionState.forHotbar(hotbarSlot);
         return;
      }

      int invIndex = findInInventory(item);
      if (invIndex != -1) {
         state = ActionState.forInventory(invIndex);
      }
   }

   private void tickState() {
      if (mc.player == null || mc.interactionManager == null) {
         state = ActionState.idle();
         return;
      }

      switch (state.phase) {
         case SWAP_IN -> {
            // swap inventory slot into selected hotbar slot
            int fromSlotId = toHandlerSlotId(state.invIndex);
            if (fromSlotId == -1) {
               state = ActionState.idle();
               return;
            }
            clickSwap(fromSlotId, state.prevSelectedHotbar);
            state.phase = Phase.SELECT;
         }
         case SELECT -> {
            mc.player.getInventory().setSelectedSlot(state.prevSelectedHotbar);
            state.phase = Phase.USE;
         }
         case USE -> {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            state.phase = Phase.RESTORE_SELECT;
         }
         case RESTORE_SELECT -> {
            mc.player.getInventory().setSelectedSlot(state.restoreSelectedHotbar);
            state.phase = state.fromInventory ? Phase.SWAP_BACK : Phase.IDLE;
         }
         case SWAP_BACK -> {
            int fromSlotId = toHandlerSlotId(state.invIndex);
            if (fromSlotId == -1) {
               state = ActionState.idle();
               return;
            }
            clickSwap(fromSlotId, state.prevSelectedHotbar);
            state.phase = Phase.IDLE;
         }
         case IDLE -> state = ActionState.idle();
      }
   }

   private void clickSwap(int fromSlotId, int hotbarIndex) {
      if (mc.player == null || mc.interactionManager == null) return;
      ScreenHandler handler = mc.player.currentScreenHandler;
      if (handler == null) return;
      // SlotActionType.SWAP: "button" = hotbar index 0..8
      mc.interactionManager.clickSlot(handler.syncId, fromSlotId, hotbarIndex, SlotActionType.SWAP, mc.player);
   }

   private static int toHandlerSlotId(int invIndex) {
      // playerScreenHandler mapping: 9..35 inventory, 36..44 hotbar
      if (invIndex >= 9 && invIndex <= 35) {
         return invIndex;
      }
      if (invIndex >= 0 && invIndex <= 8) {
         return 36 + invIndex;
      }
      return -1;
   }

   private int findInHotbar(Item item) {
      if (mc.player == null) return -1;
      for (int i = 0; i < 9; i++) {
         if (mc.player.getInventory().getStack(i).isOf(item)) return i;
      }
      return -1;
   }

   private int findInInventory(Item item) {
      if (mc.player == null) return -1;
      for (int i = 9; i <= 35; i++) {
         if (mc.player.getInventory().getStack(i).isOf(item)) return i;
      }
      return -1;
   }

   private enum Phase {
      IDLE,
      SWAP_IN,
      SELECT,
      USE,
      RESTORE_SELECT,
      SWAP_BACK
   }

   private static final class ActionState {
      private Phase phase;
      private final boolean fromInventory;
      private final int invIndex;
      private final int prevSelectedHotbar;
      private final int restoreSelectedHotbar;

      private ActionState(Phase phase, boolean fromInventory, int invIndex, int prevSelectedHotbar, int restoreSelectedHotbar) {
         this.phase = Objects.requireNonNull(phase);
         this.fromInventory = fromInventory;
         this.invIndex = invIndex;
         this.prevSelectedHotbar = prevSelectedHotbar;
         this.restoreSelectedHotbar = restoreSelectedHotbar;
      }

      static ActionState idle() {
         return new ActionState(Phase.IDLE, false, -1, -1, -1);
      }

      static ActionState forHotbar(int hotbarSlot) {
         int restore = Module.mc.player != null ? Module.mc.player.getInventory().getSelectedSlot() : 0;
         // select target slot, use, restore previous
         ActionState st = new ActionState(Phase.SELECT, false, -1, hotbarSlot, restore);
         return st;
      }

      static ActionState forInventory(int invIndex) {
         int selected = Module.mc.player != null ? Module.mc.player.getInventory().getSelectedSlot() : 0;
         // swap invIndex into selected hotbar, then select/use/restore, then swap back
         return new ActionState(Phase.SWAP_IN, true, invIndex, selected, selected);
      }
   }
}

