package ru.zero.ui.gui.component.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.color.GuiAdvancedColorPicker;
import ru.zero.util.render.core.Renderer2D;

@Environment(EnvType.CLIENT)
public class GuiRenderColorPicker extends GuiScreen {
   public static void renderColorPickerWindow(
         Renderer2D renderer2D,
         HueSetting hueSetting,
         int mouseX,
         int mouseY,
         int outlineColor,
         int bgColor,
         int textColor,
         float alpha
   ) {
      if (hueSetting == null) {
         return;
      }

      if (GuiScreen.colorPickerX == 0.0F && GuiScreen.colorPickerY == 0.0F) {
         return;
      }

      GuiAdvancedColorPicker.render(
            renderer2D,
            hueSetting,
            GuiScreen.colorPickerX,
            GuiScreen.colorPickerY,
            mouseX,
            mouseY,
            outlineColor,
            bgColor,
            textColor,
            alpha
      );
   }
}
