package ru.zero.ui.gui.component.mouse.colorpicker;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.color.GuiAdvancedColorPicker;
import ru.zero.ui.gui.color.ScreenColorSampler;
import ru.zero.ui.gui.component.render.GuiRenderMain;

@Environment(EnvType.CLIENT)
public class GuiMouseClickedColorPicker extends GuiScreen {
   public static boolean mouseClickedColorPicker(int mouseX, int mouseY, int pButton) {
      if (GuiScreen.activeColorPicker == null || !(GuiScreen.activeColorPicker instanceof HueSetting hueSetting)) {
         return false;
      }

      if (GuiScreen.colorPickerX == 0.0F && GuiScreen.colorPickerY == 0.0F) {
         return false;
      }

      if (GuiScreen.pickingScreenColor) {
         if (pButton == 1) {
            GuiScreen.pickingScreenColor = false;
         } else if (pButton == 0) {
            hueSetting.setColor(ScreenColorSampler.sample(mouseX, mouseY));
            GuiScreen.pickingScreenColor = false;
            if (Zero.get != null && Zero.get.configManager != null) {
               Zero.get.configManager.autoSave();
            }
         }
         return true;
      }

      float drawX = GuiAdvancedColorPicker.pickerDrawX(GuiScreen.colorPickerX);
      if (GuiAdvancedColorPicker.handleClick(hueSetting, drawX, GuiScreen.colorPickerY, mouseX, mouseY, pButton)) {
         return true;
      }

      float pickerHeight = GuiAdvancedColorPicker.height(hueSetting);
      if (GuiRenderMain.isHovered(mouseX, mouseY, drawX, GuiScreen.colorPickerY, GuiAdvancedColorPicker.WIDTH, pickerHeight)) {
         return true;
      }

      return false;
   }
}
