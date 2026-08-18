package ru.zero.ui.gui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.component.render.GuiRenderMain;
import ru.zero.util.render.math.ScaleHelper;

@Environment(EnvType.CLIENT)
public class GuiMouseScrolled extends GuiScreen {
   public static boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
      float[] mouseCoords = ScaleHelper.calc((float)pMouseX, (float)pMouseY);
      float mouseX = mouseCoords[0];
      float mouseY = mouseCoords[1];
      float x1 = GuiScreen.x;
      float y1 = GuiScreen.y;
      float rectWidth = GuiScreen.width;
      float rectHeight = GuiScreen.height;
      if (!GuiScreen.exit && GuiRenderMain.isHovered(mouseX, mouseY, x1, y1, rectWidth, rectHeight)) {
         GuiScreen.getScrollUtil().setEnabled(true);
         // На части мышек/тачпадов вертикальный скролл может приходить в pScrollX.
         double delta = Math.abs(pScrollY) > Math.abs(pScrollX) ? pScrollY : pScrollX;
         if (Math.abs(delta) < 1.0E-4) {
            return false;
         } else {
            GuiScreen.getScrollUtil().handleScroll(delta);
            return true;
         }
      } else {
         return false;
      }
   }
}
