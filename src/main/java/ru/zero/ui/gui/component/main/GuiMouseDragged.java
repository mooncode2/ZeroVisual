package ru.zero.ui.gui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.color.GuiAdvancedColorPicker;
import ru.zero.util.render.math.ScaleHelper;

@Environment(EnvType.CLIENT)
public class GuiMouseDragged extends GuiScreen {
   public static boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
      int mouseX = (int)ScaleHelper.calc((float)pMouseX, (float)pMouseY)[0];
      int mouseY = (int)ScaleHelper.calc((float)pMouseX, (float)pMouseY)[1];
      if (GuiScreen.activeColorPicker != null) {
         HueSetting hueSetting = GuiScreen.activeColorPicker;
         if (GuiAdvancedColorPicker.handleDrag(hueSetting, GuiAdvancedColorPicker.pickerDrawX(GuiScreen.colorPickerX), GuiScreen.colorPickerY, mouseX, mouseY)) {
            return true;
         }
      }

      if (GuiScreen.getScrollUtil().handleScrollbarDrag(mouseY)) {
         return true;
      }

      if (GuiScreen.activeSliderSetting != null) {
         SliderSetting sliderSetting = GuiScreen.activeSliderSetting;
         float progress = (mouseX - GuiScreen.sliderX) / GuiScreen.sliderWidth;
         progress = Math.max(0.0F, Math.min(1.0F, progress));
         sliderSetting.current = sliderSetting.minimum + (sliderSetting.maximum - sliderSetting.minimum) * progress;
         if (Zero.get.configManager != null) {
            Zero.get.configManager.autoSave();
         }

         return true;
      } else {
         return false;
      }
   }
}
