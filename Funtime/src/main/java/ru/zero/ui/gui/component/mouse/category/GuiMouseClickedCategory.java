package ru.zero.ui.gui.component.mouse.category;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.api.Category;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.component.render.GuiRenderMain;
import ru.zero.util.render.math.animation.Direction;

@Environment(EnvType.CLIENT)
public class GuiMouseClickedCategory extends GuiScreen {
   public static void mouseClickedCategory(int mouseX, int mouseY) {
      float x1 = GuiScreen.x;
      float y1 = GuiScreen.y;
      float downY = 0.0F;

      for (Category category : GuiScreen.categories) {
         if (GuiRenderMain.isHovered(mouseX, mouseY, x1, y1 + 43.365F + downY - 2.0F, 104.34F, 21.325F) && GuiScreen.selectedCategories != category) {
            GuiScreen.animation15.setDirection(Direction.BACKWARDS);
            GuiScreen.activeColorPicker = null;
            GuiScreen.selectedCategories = category;
            GuiScreen.modules = Zero.get.manager.getType(GuiScreen.selectedCategories);
            GuiScreen.categoryAnimation.reset();
            GuiScreen.moduleAnimation.reset();
            GuiScreen.getScrollUtil().reset();
            Zero.get.guiManager.setGuiCategory(category);
         }

         downY += 24.0F;
      }
   }
}
