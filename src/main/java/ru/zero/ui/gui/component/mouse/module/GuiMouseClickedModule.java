package ru.zero.ui.gui.component.mouse.module;

import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.component.mouse.setting.GuiMouseClickedSetting;
import ru.zero.ui.gui.component.render.GuiRenderMain;
import ru.zero.ui.gui.component.setting.GuiRenderSetting;
import ru.zero.util.keyboard.Keyboard;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.animation.Direction;
import ru.zero.util.render.math.animation.anim.util.Easings;
import ru.zero.util.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
public class GuiMouseClickedModule extends GuiScreen {
   public static boolean mouseClickedModule(Renderer2D renderer2D, int mouseX, int mouseY, int pButton) {
      float x1 = GuiScreen.x + 104.735F;
      float y1 = GuiScreen.y + 34.025F;
      float rectWidth = 261.5F;
      float rectHeight = 209.5F;
      float clipX = x1 + 5.0F;
      float clipY = y1 + 5.0F;
      float clipWidth = rectWidth - 10.0F;
      float clipHeight = rectHeight - 10.0F;
      if (!GuiRenderMain.isHovered(mouseX, mouseY, clipX, clipY, clipWidth, clipHeight)) {
         return false;
      } else {
         List<Module> filteredModules = GuiScreen.modules;
         if (GuiScreen.activeSearch && !GuiScreen.searchText.isEmpty()) {
            String searchLower = GuiScreen.searchText.toLowerCase().trim();
            filteredModules = GuiScreen.modules.stream().filter(modulex -> modulex.name.toLowerCase().contains(searchLower)).collect(Collectors.toList());
         }

         int index = 1;
         float downY = GuiScreen.getScrollUtil().getScroll();
         float downYSetting1 = 0.0F;
         float downYSetting2 = 0.0F;

         for (Module module : filteredModules) {
            float settingsHeight = 12.0F;
            if (GuiScreen.openSettingsModules.contains(module)) {
               for (Setting setting : module.getSettingsForGUI()) {
                  settingsHeight += GuiRenderSetting.getSettingHeight(renderer2D, setting);
               }

               settingsHeight = Math.max(settingsHeight, 20.0F);
            }

            if (index % 2 == 0) {
               float currentDownY = downY + downYSetting2 - 30.0F;
               float moduleX = GuiScreen.x + 238.35F;
               float moduleY = GuiScreen.y + 43.365F + currentDownY;
               float moduleWidth = 121.47F;
               float moduleHeight = 21.325F;
               if (GuiScreen.openSettingsModules.contains(module) && pButton == 0) {
                  float settingY = GuiScreen.y + 64.69F + currentDownY + 4.0F;
                  float settingX = GuiScreen.x + 238.35F + 9.0F;
                  float settingWidth = 105.47F;
                  float totalSettingsHeight = 0.0F;

                  for (Setting setting : module.getSettingsForGUI()) {
                     float actualSettingY = settingY + totalSettingsHeight;
                     if (GuiMouseClickedSetting.handleSettingClick(renderer2D, setting, settingX, actualSettingY, settingWidth, mouseX, mouseY, pButton)) {
                        return true;
                     }

                     totalSettingsHeight += GuiRenderSetting.getSettingHeight(renderer2D, setting) + 1.0F;
                  }
               }

               if (GuiScreen.openSettingsModules.contains(module)) {
                  downYSetting2 += settingsHeight;
               }

               if (GuiRenderMain.isHovered(mouseX, mouseY, moduleX, moduleY, moduleWidth, moduleHeight) && pButton == 0) {
                  module.toggle();
               }

               if (GuiRenderMain.isHovered(mouseX, mouseY, moduleX, moduleY, moduleWidth, moduleHeight)
                  && pButton == 1
                  && !module.getSettingsForGUI().isEmpty()) {
                  if (GuiScreen.openSettingsModules.contains(module)) {
                     GuiScreen.openSettingsModules.remove(module);
                     GuiScreen.getModuleSettingsAnimation(module).run(0.0, 0.6F, Easings.QUART_OUT);
                     GuiScreen.getModuleSettingsAlphaAnimation(module).run(0.0, 0.16F, Easings.SINE_OUT);
                     if (GuiScreen.activeColorPicker != null && module.getSettingsForGUI().contains(GuiScreen.activeColorPicker)) {
                        GuiScreen.animation15.setDirection(Direction.BACKWARDS);
                        GuiScreen.activeColorPicker = null;
                        GuiScreen.colorPickerX = 0.0F;
                        GuiScreen.colorPickerY = 0.0F;
                     }
                  } else {
                     GuiScreen.openSettingsModules.add(module);
                     GuiScreen.getModuleSettingsAlphaAnimation(module).run(1.0, 0.16F, Easings.SINE_OUT);
                     GuiScreen.getModuleSettingsAnimation(module).run(1.0, 0.6F, Easings.QUART_OUT);
                  }
               }

               if (GuiRenderMain.isHovered(mouseX, mouseY, moduleX, moduleY, moduleWidth, moduleHeight) && pButton == 2) {
                  if (module.binding) {
                     module.binding = false;
                     GuiScreen.activeModuleBind = null;
                     GuiScreen.getModuleBindAnimation(module).run(0.0, 0.2F, Easings.SINE_OUT);
                  } else {
                     if (GuiScreen.activeModuleBind != null) {
                        GuiScreen.activeModuleBind.binding = false;
                        GuiScreen.getModuleBindAnimation(GuiScreen.activeModuleBind).run(0.0, 0.2F, Easings.SINE_OUT);
                     }

                     GuiScreen.activeModuleBind = module;
                     module.binding = true;
                     GuiScreen.getModuleBindAnimation(module).run(1.0, 0.2F, Easings.SINE_OUT);
                  }

                  return true;
               }

               if (module.binding || module.bind != -1) {
                  float moduleNameX = GuiScreen.x + 247.895F;
                  float moduleNameY = GuiScreen.y + 49.555F + currentDownY;
                  float moduleNameWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, module.name, 14.0F).width;
                  float bindX = moduleNameX + moduleNameWidth + 4.0F;
                  float bindY = moduleNameY - 1.0F;
                  String keyText = module.binding ? "..." : Keyboard.keyName(module.bind);
                  float keyTextWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, keyText, 12.0F).width;
                  float minButtonWidth = 16.0F;
                  float buttonWidth = Math.max(minButtonWidth, keyTextWidth + 8.0F);
                  if (GuiRenderMain.isHovered(mouseX, mouseY, bindX, bindY, buttonWidth, 16.0F)) {
                     if (pButton == 2) {
                        if (module.binding) {
                           module.binding = false;
                           GuiScreen.activeModuleBind = null;
                           GuiScreen.getModuleBindAnimation(module).run(0.0, 0.2F, Easings.SINE_OUT);
                        } else {
                           if (GuiScreen.activeModuleBind != null) {
                              GuiScreen.activeModuleBind.binding = false;
                              GuiScreen.getModuleBindAnimation(GuiScreen.activeModuleBind).run(0.0, 0.2F, Easings.SINE_OUT);
                           }

                           GuiScreen.activeModuleBind = module;
                           module.binding = true;
                           GuiScreen.getModuleBindAnimation(module).run(1.0, 0.2F, Easings.SINE_OUT);
                        }

                        return true;
                     }

                     if (module.binding && pButton >= 0 && pButton <= 1) {
                        int mouseKey = -100 - pButton;
                        module.bind = mouseKey;
                        module.binding = false;
                        GuiScreen.activeModuleBind = null;
                        GuiScreen.getModuleBindAnimation(module).run(1.0, 0.2F, Easings.SINE_OUT);
                        return true;
                     }
                  }
               }
            } else {
               float currentDownYx = downY + downYSetting1;
               float moduleXx = GuiScreen.x + 111.885F;
               float moduleYx = GuiScreen.y + 43.365F + currentDownYx;
               float moduleWidthx = 121.47F;
               float moduleHeightx = 21.325F;
               if (GuiScreen.openSettingsModules.contains(module) && pButton == 0) {
                  float settingY = GuiScreen.y + 64.69F + currentDownYx + 4.0F;
                  float settingX = GuiScreen.x + 111.885F + 9.0F;
                  float settingWidth = 105.47F;
                  float totalSettingsHeight = 0.0F;

                  for (Setting setting : module.getSettingsForGUI()) {
                     float actualSettingY = settingY + totalSettingsHeight;
                     if (GuiMouseClickedSetting.handleSettingClick(renderer2D, setting, settingX, actualSettingY, settingWidth, mouseX, mouseY, pButton)) {
                        return true;
                     }

                     totalSettingsHeight += GuiRenderSetting.getSettingHeight(renderer2D, setting) + 1.0F;
                  }
               }

               if (GuiScreen.openSettingsModules.contains(module)) {
                  downYSetting1 += settingsHeight;
               }

               if (module.binding || module.bind != -1) {
                  float moduleNameX = GuiScreen.x + 121.425F;
                  float moduleNameY = GuiScreen.y + 49.555F + currentDownYx;
                  float moduleNameWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, module.name, 14.0F).width;
                  float bindX = moduleNameX + moduleNameWidth + 4.0F;
                  float bindY = moduleNameY - 1.0F;
                  String keyText = module.binding ? "..." : Keyboard.keyName(module.bind);
                  float keyTextWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, keyText, 12.0F).width;
                  float minButtonWidth = 16.0F;
                  float buttonWidth = Math.max(minButtonWidth, keyTextWidth + 8.0F);
                  if (GuiRenderMain.isHovered(mouseX, mouseY, bindX, bindY, buttonWidth, 16.0F)) {
                     if (pButton == 2) {
                        if (module.binding) {
                           module.binding = false;
                           GuiScreen.activeModuleBind = null;
                           GuiScreen.getModuleBindAnimation(module).run(0.0, 0.2F, Easings.SINE_OUT);
                        } else {
                           if (GuiScreen.activeModuleBind != null) {
                              GuiScreen.activeModuleBind.binding = false;
                              GuiScreen.getModuleBindAnimation(GuiScreen.activeModuleBind).run(0.0, 0.2F, Easings.SINE_OUT);
                           }

                           GuiScreen.activeModuleBind = module;
                           module.binding = true;
                           GuiScreen.getModuleBindAnimation(module).run(1.0, 0.2F, Easings.SINE_OUT);
                        }

                        return true;
                     }

                     if (module.binding && pButton >= 0 && pButton <= 1) {
                        int mouseKey = -100 - pButton;
                        module.bind = mouseKey;
                        module.binding = false;
                        GuiScreen.activeModuleBind = null;
                        GuiScreen.getModuleBindAnimation(module).run(1.0, 0.2F, Easings.SINE_OUT);
                        return true;
                     }
                  }
               }

               if (GuiRenderMain.isHovered(mouseX, mouseY, moduleXx, moduleYx, moduleWidthx, moduleHeightx) && pButton == 0) {
                  module.toggle();
               }

               if (GuiRenderMain.isHovered(mouseX, mouseY, moduleXx, moduleYx, moduleWidthx, moduleHeightx)
                  && pButton == 1
                  && !module.getSettingsForGUI().isEmpty()) {
                  if (GuiScreen.openSettingsModules.contains(module)) {
                     GuiScreen.openSettingsModules.remove(module);
                     GuiScreen.getModuleSettingsAnimation(module).run(0.0, 0.6F, Easings.QUART_OUT);
                     GuiScreen.getModuleSettingsAlphaAnimation(module).run(0.0, 0.16F, Easings.SINE_OUT);
                     if (GuiScreen.activeColorPicker != null && module.getSettingsForGUI().contains(GuiScreen.activeColorPicker)) {
                        GuiScreen.animation15.setDirection(Direction.BACKWARDS);
                        GuiScreen.activeColorPicker = null;
                        GuiScreen.colorPickerX = 0.0F;
                        GuiScreen.colorPickerY = 0.0F;
                     }
                  } else {
                     GuiScreen.openSettingsModules.add(module);
                     GuiScreen.getModuleSettingsAlphaAnimation(module).run(1.0, 0.16F, Easings.SINE_OUT);
                     GuiScreen.getModuleSettingsAnimation(module).run(1.0, 0.6F, Easings.QUART_OUT);
                  }
               }

               if (GuiRenderMain.isHovered(mouseX, mouseY, moduleXx, moduleYx, moduleWidthx, moduleHeightx) && pButton == 2) {
                  if (module.binding) {
                     module.binding = false;
                     GuiScreen.activeModuleBind = null;
                     GuiScreen.getModuleBindAnimation(module).run(0.0, 1.0, Easings.SINE_OUT);
                  } else {
                     if (GuiScreen.activeModuleBind != null) {
                        GuiScreen.activeModuleBind.binding = false;
                        GuiScreen.getModuleBindAnimation(GuiScreen.activeModuleBind).run(0.0, 1.0, Easings.SINE_OUT);
                     }

                     GuiScreen.activeModuleBind = module;
                     module.binding = true;
                     GuiScreen.getModuleBindAnimation(module).run(1.0, 1.0, Easings.SINE_OUT);
                  }

                  return true;
               }

               downY += 30.325F;
            }

            index++;
         }

         return false;
      }
   }

   public static float[] findColorPickerPosition(Renderer2D renderer2D, HueSetting hueSetting) {
      if (hueSetting == null) {
         return null;
      } else {
         int index = 1;
         float downY = GuiScreen.getScrollUtil().getScroll();
         float downYSetting1 = 0.0F;
         float downYSetting2 = 0.0F;

         for (Module module : GuiScreen.modules) {
            float settingsHeight = 12.0F;
            if (GuiScreen.openSettingsModules.contains(module)) {
               for (Setting setting : module.getSettingsForGUI()) {
                  settingsHeight += GuiRenderSetting.getSettingHeight(renderer2D, setting);
               }

               settingsHeight = Math.max(settingsHeight, 20.0F);
            }

            if (index % 2 == 0) {
               float currentDownY = downY + downYSetting2 - 30.0F;
               if (GuiScreen.openSettingsModules.contains(module)) {
                  float settingY = GuiScreen.y + 64.69F + currentDownY + 4.0F;
                  float settingX = GuiScreen.x + 238.35F + 9.0F;
                  float settingWidth = 111.47F;
                  float totalSettingsHeight = 0.0F;

                  for (Setting setting : module.getSettingsForGUI()) {
                     if (setting == hueSetting) {
                        float pickerX = settingX + settingWidth - 15.0F;
                        float pickerY = settingY + totalSettingsHeight - 5.0F;
                        return new float[]{pickerX, pickerY};
                     }

                     totalSettingsHeight += GuiRenderSetting.getSettingHeight(renderer2D, setting) + 3.0F;
                  }

                  downYSetting2 += settingsHeight;
               }
            } else {
               float currentDownY = downY + downYSetting1;
               if (GuiScreen.openSettingsModules.contains(module)) {
                  float settingY = GuiScreen.y + 64.69F + currentDownY + 4.0F;
                  float settingX = GuiScreen.x + 111.885F + 9.0F;
                  float settingWidth = 111.47F;
                  float totalSettingsHeight = 0.0F;

                  for (Setting setting : module.getSettingsForGUI()) {
                     if (setting == hueSetting) {
                        float pickerX = settingX + settingWidth - 15.0F;
                        float pickerY = settingY + totalSettingsHeight - 5.0F;
                        return new float[]{pickerX, pickerY};
                     }

                     totalSettingsHeight += GuiRenderSetting.getSettingHeight(renderer2D, setting) + 3.0F;
                  }

                  downYSetting1 += settingsHeight;
               }

               downY += 30.325F;
            }

            index++;
         }

         return null;
      }
   }
}
