package ru.zero.ui.gui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.ui.gui.GuiScreen;

@Environment(EnvType.CLIENT)
public class GuiCharTyped extends GuiScreen {
   public static boolean charTyped(char codePoint, int modifiers) {
      if (GuiScreen.friendInputActive) {
         if (codePoint == '\b') {
            if (!GuiScreen.friendInputText.isEmpty()) {
               GuiScreen.friendInputText = GuiScreen.friendInputText.substring(0, GuiScreen.friendInputText.length() - 1);
            }

            return true;
         }

         if (codePoint >= ' ' && codePoint != 127) {
            if (GuiScreen.friendInputText.length() < 16) {
               GuiScreen.friendInputText = GuiScreen.friendInputText + codePoint;
            }

            return true;
         }
      }

      if (GuiScreen.activeStringSetting != null) {
         if (codePoint == '\b') {
            if (!GuiScreen.activeStringSetting.input.isEmpty()) {
               GuiScreen.activeStringSetting.input = GuiScreen.activeStringSetting.input.substring(0, GuiScreen.activeStringSetting.input.length() - 1);
               if (Zero.get.configManager != null) {
                  Zero.get.configManager.autoSave();
               }
            }

            return true;
         }

         if (codePoint >= ' ' && codePoint != 127) {
            if (GuiScreen.activeStringSetting.input.length() < 16) {
               GuiScreen.activeStringSetting.input = GuiScreen.activeStringSetting.input + codePoint;
               if (Zero.get.configManager != null) {
                  Zero.get.configManager.autoSave();
               }
            }

            return true;
         }
      }

      if (GuiScreen.activeSearch) {
         if (codePoint == '\b') {
            return true;
         }

         if (codePoint >= ' '
            && codePoint != 127
            && (codePoint >= 'a' && codePoint <= 'z' || codePoint >= 'A' && codePoint <= 'Z' || codePoint >= '0' && codePoint <= '9' || codePoint == ' ')) {
            if (GuiScreen.searchText.length() < 50) {
               GuiScreen.searchText = GuiScreen.searchText + codePoint;
            }

            return true;
         }
      }

      return false;
   }
}
