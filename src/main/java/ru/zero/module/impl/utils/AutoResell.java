package ru.zero.module.impl.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import ru.zero.event.EventInit;
import ru.zero.event.lifecycle.ClientTickEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.client.Lang;

@IModule(
   name = "AutoResell",
   description = "Автоматически перевыставляет предметы на аукционе",
   category = Category.Utils,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class AutoResell extends Module {
   private static final int SLOT_ENDER_CHEST = 47;
   private static final int SLOT_CLOCK = 53;
   private static final int OPEN_TIMEOUT_TICKS = 80;

   public static SliderSetting intervalSeconds = new SliderSetting("Через сколько перевыставлять (с)", 60.0F, 60.0F, 9600.0F, 1.0F, false);
   public static SliderSetting tickDelay = new SliderSetting("Задержка (тики)", 4.0F, 2.0F, 20.0F, 1.0F, false);
   public static BooleanSetting inPvpMode = new BooleanSetting("В пвп режиме", false);

   private Phase phase = Phase.WAIT_INTERVAL;
   private int waitTicks;
   private int actionTicks;
   private boolean warnedTenSeconds;
   private boolean clickedEnderChest;
   private boolean clickedClock;

   public AutoResell() {
      this.addSettings(new Setting[] { intervalSeconds, tickDelay, inPvpMode });
   }

   @Override
   public void onEnable() {
      super.onEnable();
      resetCycle();
   }

   @Override
   public void onDisable() {
      super.onDisable();
      phase = Phase.WAIT_INTERVAL;
      waitTicks = 0;
      actionTicks = 0;
      warnedTenSeconds = false;
      clickedEnderChest = false;
      clickedClock = false;
   }

   @EventInit
   public void onTick(ClientTickEvent event) {
      if (mc.player == null || mc.interactionManager == null) {
         return;
      }

      switch (phase) {
         case WAIT_INTERVAL -> tickWaitInterval();
         case OPEN_AH -> tickOpenAh();
         case WAIT_AFTER_OPEN -> tickActionDelay(Phase.CLICK_ENDER_CHEST);
         case CLICK_ENDER_CHEST -> tickClickSlot(SLOT_ENDER_CHEST, Items.ENDER_CHEST, Phase.WAIT_AFTER_ENDER, "ender_chest");
         case WAIT_AFTER_ENDER -> tickActionDelay(Phase.CLICK_CLOCK);
         case CLICK_CLOCK -> tickClickSlot(SLOT_CLOCK, Items.CLOCK, Phase.WAIT_AFTER_CLOCK, "clock");
         case WAIT_AFTER_CLOCK -> tickActionDelay(Phase.CLOSE);
         case CLOSE -> tickClose();
      }
   }

   private void tickWaitInterval() {
      int intervalTicks = Math.max(1, Math.round(intervalSeconds.get() * 20.0F));
      int warningAt = Math.max(0, intervalTicks - 200);

      if (!warnedTenSeconds && waitTicks >= warningAt && intervalTicks > 200) {
         warnedTenSeconds = true;
         UtilsUtil.notifyPlayer(Lang.t("Через 10 секунд предметы будут перевыставлены"));
      }

      if (waitTicks >= intervalTicks) {
         if (!inPvpMode.get() && UtilsUtil.hasRedBossBar()) {
            UtilsUtil.notifyFailure(Lang.t("Перевыставление неуспешно: активен PvP (красный BossBar)"));
            resetCycle();
            return;
         }
         startResellSequence();
         return;
      }

      waitTicks++;
   }

   private void startResellSequence() {
      clickedEnderChest = false;
      clickedClock = false;
      UtilsUtil.sendCommand("/ah");
      phase = Phase.OPEN_AH;
      actionTicks = 0;
   }

   private void tickOpenAh() {
      actionTicks++;
      if (!(mc.currentScreen instanceof HandledScreen<?>)) {
         if (actionTicks >= OPEN_TIMEOUT_TICKS) {
            failResell(Lang.t("не удалось открыть аукцион (/ah)"));
         }
         return;
      }
      phase = Phase.WAIT_AFTER_OPEN;
      actionTicks = 0;
   }

   private void tickActionDelay(Phase next) {
      if (!(mc.currentScreen instanceof HandledScreen<?>)) {
         failResell(Lang.t("аукцион закрылся до завершения"));
         return;
      }

      actionTicks++;
      if (actionTicks >= Math.round(tickDelay.get())) {
         phase = next;
         actionTicks = 0;
      }
   }

   private void tickClickSlot(int slotNumber, net.minecraft.item.Item expectedItem, Phase next, String itemName) {
      if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
         failResell(Lang.t("аукцион закрылся до завершения"));
         return;
      }

      int slotIndex = slotNumber - 1;
      ScreenHandler handler = screen.getScreenHandler();
      if (slotIndex < 0 || slotIndex >= handler.slots.size()) {
         failResell("слот " + slotNumber + " не найден в меню аукциона");
         return;
      }

      if (!handler.getSlot(slotIndex).getStack().isOf(expectedItem)) {
         failResell("в слоте " + slotNumber + " нет предмета " + itemName);
         return;
      }

      mc.interactionManager.clickSlot(handler.syncId, slotIndex, 0, SlotActionType.PICKUP, mc.player);
      if (expectedItem == Items.ENDER_CHEST) {
         clickedEnderChest = true;
      } else if (expectedItem == Items.CLOCK) {
         clickedClock = true;
      }
      phase = next;
      actionTicks = 0;
   }

   private void tickClose() {
      if (mc.currentScreen != null) {
         mc.player.closeHandledScreen();
      }

      if (clickedEnderChest && clickedClock) {
         UtilsUtil.notifySuccess(Lang.t("Перевыставление успешно"));
         resetCycle();
      } else {
         failResell(Lang.t("не все действия в аукционе выполнены"));
      }
   }

   private void failResell(String reason) {
      UtilsUtil.notifyFailure(Lang.t("Перевыставление неуспешно: ") + reason);
      if (mc.currentScreen != null) {
         mc.player.closeHandledScreen();
      }
      resetCycle();
   }

   private void resetCycle() {
      phase = Phase.WAIT_INTERVAL;
      waitTicks = 0;
      actionTicks = 0;
      warnedTenSeconds = false;
      clickedEnderChest = false;
      clickedClock = false;
   }

   private enum Phase {
      WAIT_INTERVAL,
      OPEN_AH,
      WAIT_AFTER_OPEN,
      CLICK_ENDER_CHEST,
      WAIT_AFTER_ENDER,
      CLICK_CLOCK,
      WAIT_AFTER_CLOCK,
      CLOSE
   }
}