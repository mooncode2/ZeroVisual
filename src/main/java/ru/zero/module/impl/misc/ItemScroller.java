package ru.zero.module.impl.misc;

import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.lwjgl.glfw.GLFW;
import ru.zero.event.EventInit;
import ru.zero.event.lifecycle.ClientTickEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.other.TimerUtil;

/**
 * Ускоренный перенос предметов в GUI; работает только с событиями тика и экрана, без модулей Combat/Movement/Player.
 */
@IModule(
   name = "Item Scroller",
   description = "Быстрое перемещение предметов в инвентаре (Shift+ЛКМ)",
   category = Category.Utils,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class ItemScroller extends Module {
   public static SliderSetting delay = new SliderSetting("Задержка (мс)", 50.0F, 0.0F, 200.0F, 1.0F, false);
   public final TimerUtil time = new TimerUtil();
   private static ItemScroller instance;

   public ItemScroller() {
      this.addSettings(new Setting[]{delay});
      instance = this;
      this.time.reset();
   }

   public static ItemScroller getInstance() {
      return instance;
   }

   @EventInit
   public void update(ClientTickEvent event) {
      if (mc.player != null && mc.currentScreen != null) {
         if (mc.currentScreen instanceof HandledScreen<?> screen) {
            if (mc.getWindow() != null) {
               long windowHandle = mc.getWindow().getHandle();
               boolean isShiftPressed = GLFW.glfwGetKey(windowHandle, 340) == 1 || GLFW.glfwGetKey(windowHandle, 344) == 1;
               boolean isLeftMousePressed = GLFW.glfwGetMouseButton(windowHandle, 0) == 1;
               if (isShiftPressed && isLeftMousePressed) {
                  double[] mouseX = new double[1];
                  double[] mouseY = new double[1];
                  GLFW.glfwGetCursorPos(windowHandle, mouseX, mouseY);

                  try {
                     Method getSlotAtMethod = HandledScreen.class.getDeclaredMethod("getSlotAt", double.class, double.class);
                     getSlotAtMethod.setAccessible(true);
                     Slot slot = (Slot)getSlotAtMethod.invoke(screen, mouseX[0], mouseY[0]);
                     if (slot != null && slot.hasStack() && this.time.hasTimeElapsed((long)delay.get())) {
                        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                        this.time.reset();
                     }
                  } catch (Exception var11) {
                  }
               }
            }
         }
      }
   }
}
