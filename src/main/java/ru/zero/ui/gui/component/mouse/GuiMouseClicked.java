package ru.zero.ui.gui.component.mouse;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.component.mouse.category.GuiMouseClickedCategory;
import ru.zero.ui.gui.component.mouse.colorpicker.GuiMouseClickedColorPicker;
import ru.zero.ui.gui.component.mouse.module.GuiMouseClickedModule;
import ru.zero.ui.gui.component.mouse.setting.GuiMouseClickedSetting;
import ru.zero.ui.gui.component.setting.GuiRenderSetting;
import ru.zero.ui.gui.map.GuiServerMapPanel;
import ru.zero.ui.gui.component.render.GuiRenderMain;
import ru.zero.ui.gui.theme.ThemeScreen;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.MathHelper;
import ru.zero.util.render.math.ScaleHelper;
import ru.zero.util.render.math.ScaledResolution;

@Environment(EnvType.CLIENT)
public class GuiMouseClicked extends GuiScreen {
   public static boolean mouseClicked(Renderer2D renderer2D, double pMouseX, double pMouseY, int pButton) {
      int mouseX = (int)ScaleHelper.calc((float)pMouseX, (float)pMouseY)[0];
      int mouseY = (int)ScaleHelper.calc((float)pMouseX, (float)pMouseY)[1];
      ScaledResolution sr = new ScaledResolution(GuiScreen.mc);
      GuiScreen.x = (int)MathHelper.clamp(GuiScreen.x, 0.0F, ScaleHelper.calc(sr.getWidth()) - GuiScreen.width);
      GuiScreen.y = (int)MathHelper.clamp(GuiScreen.y, 0.0F, ScaleHelper.calc(sr.getHeight()) - GuiScreen.height);
      if (!GuiScreen.exit) {
         float searchX = GuiScreen.x + 111.885F;
         float searchY = GuiScreen.y + 6.185F;
         float searchWidth = 124.04F;
         float searchHeight = 21.325F;
         if (pButton == 0 && GuiRenderMain.isHovered(mouseX, mouseY, searchX, searchY, searchWidth, searchHeight)) {
            GuiScreen.activeSearch = true;
            return true;
         }

         if (GuiServerMapPanel.handleMapButtonClick(mouseX, mouseY, pButton)) {
            return true;
         }

         if (GuiScreen.serverMapOpen) {
            GuiServerMapPanel.handlePanelClick(mouseX, mouseY, pButton);
            return true;
         }

         GuiMouseClickedCategory.mouseClickedCategory(mouseX, mouseY);

         if (GuiMouseClickedColorPicker.mouseClickedColorPicker(mouseX, mouseY, pButton)) {
            return true;
         }

         if (GuiScreen.getScrollUtil().handleScrollbarClick(mouseX, mouseY, pButton)) {
            return true;
         }

         if (GuiMouseClickedModule.mouseClickedModule(renderer2D, mouseX, mouseY, pButton)) {
            return true;
         }

         float settingsButtonX = GuiScreen.x + 338.555F;
         float settingsButtonY = GuiScreen.y + 6.185F;
         float settingsButtonWidth = 21.325F;
         float settingsButtonHeight = 21.325F;
         if (pButton == 0 && GuiRenderMain.isHovered(mouseX, mouseY, settingsButtonX, settingsButtonY, settingsButtonWidth, settingsButtonHeight)) {
            GuiScreen.showClientSettingsPopup = !GuiScreen.showClientSettingsPopup;
            return true;
         }

          if (GuiScreen.showClientSettingsPopup && pButton == 0) {
             float popupWidth = 100.0F;
             float popupHeight = 126.0F;
             float popupX = GuiScreen.x + 450.0F + 21.325F - popupWidth;
             float popupY = GuiScreen.y - 15.0F + 21.325F + 5.0F;
              if (GuiRenderMain.isHovered(mouseX, mouseY, popupX, popupY, popupWidth, popupHeight)) {
                 float settingY = popupY + 10.0F;
                 float settingX = popupX + 10.0F;
                 float settingWidth = popupWidth - 20.0F;
                 float nextY = settingY + 3.0F;
                 if (GuiMouseClickedSetting.handleSettingClick(renderer2D, GuiScreen.clientSoundSetting, settingX, nextY, settingWidth, mouseX, mouseY, pButton)) {
                    return true;
                 }

                 nextY = nextY + GuiRenderSetting.getSettingHeight(renderer2D, GuiScreen.clientSoundSetting) + 3.0F;
                 if (GuiMouseClickedSetting.handleSettingClick(renderer2D, GuiScreen.clientLiquidGlassSetting, settingX, nextY, settingWidth, mouseX, mouseY, pButton)) {
                    return true;
                 }
              } else {
                 GuiScreen.showClientSettingsPopup = false;
              }
          }

         ThemeScreen.mouseClickedTheme(pMouseX, pMouseY, pButton);
      }

      if (GuiScreen.activeBindSetting != null && pButton >= 0 && pButton <= 7) {
         int mouseKey = -100 - pButton;
         GuiScreen.activeBindSetting.key = mouseKey;
         GuiScreen.activeBindSetting.active = false;
         GuiScreen.activeBindSetting = null;
         return true;
      } else {
         return false;
      }
   }
}
