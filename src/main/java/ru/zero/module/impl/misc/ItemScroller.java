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
   private static Method cachedGetSlotAtMethod;
   private static boolean methodLookupFailed;
   private static final double[] scratchMouseX = new double[1];
   private static final double[] scratchMouseY = new double[1];

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
      if (mc.player == null || mc.currentScreen == null || !(mc.currentScreen instanceof HandledScreen<?> screen)) {
         return;
      }
      if (mc.getWindow() == null) {
         return;
      }
      long windowHandle = mc.getWindow().getHandle();
      boolean isShiftPressed = GLFW.glfwGetKey(windowHandle, 340) == 1 || GLFW.glfwGetKey(windowHandle, 344) == 1;
      boolean isLeftMousePressed = GLFW.glfwGetMouseButton(windowHandle, 0) == 1;
      if (!isShiftPressed || !isLeftMousePressed) {
         return;
      }
      if (!this.time.hasTimeElapsed((long) delay.get())) {
         return;
      }

      double[] mouseX = scratchMouseX;
      double[] mouseY = scratchMouseY;
      GLFW.glfwGetCursorPos(windowHandle, mouseX, mouseY);

      // glfwGetCursorPos returns framebuffer pixel coordinates, but HandledScreen.getSlotAt
      // expects scaled (GUI) coordinates — divide by the window scale factor.
      double guiScale = mc.getWindow().getScaleFactor();
      if (guiScale <= 0.0) {
         guiScale = 1.0;
      }
      double scaledX = mouseX[0] / guiScale;
      double scaledY = mouseY[0] / guiScale;

      Method getSlotAtMethod = resolveGetSlotAt();
      if (getSlotAtMethod == null) {
         return;
      }
      try {
         Slot slot = (Slot) getSlotAtMethod.invoke(screen, scaledX, scaledY);
         if (slot != null && slot.hasStack()) {
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
            this.time.reset();
         }
      } catch (Exception ignored) {
      }
   }

   private static Method resolveGetSlotAt() {
      if (cachedGetSlotAtMethod != null) {
         return cachedGetSlotAtMethod;
      }
      if (methodLookupFailed) {
         return null;
      }
      try {
         Method m = HandledScreen.class.getDeclaredMethod("getSlotAt", double.class, double.class);
         m.setAccessible(true);
         cachedGetSlotAtMethod = m;
         return m;
      } catch (NoSuchMethodException first) {
         // MC 1.21.x may use a different method name — try alternatives.
         for (Method m : HandledScreen.class.getDeclaredMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 2 && params[0] == double.class && params[1] == double.class
                  && (m.getReturnType() == Slot.class || m.getReturnType() == Object.class)) {
               m.setAccessible(true);
               cachedGetSlotAtMethod = m;
               return m;
            }
         }
         methodLookupFailed = true;
         return null;
      } catch (Exception ignored) {
         methodLookupFailed = true;
         return null;
      }
   }
}