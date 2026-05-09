package ru.zero.ui.gui.component.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.module.impl.visuals.ClickGUI;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.component.setting.GuiRenderSetting;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.keyboard.Keyboard;
import ru.zero.util.player.MovementManager;
import ru.zero.util.render.animation.util.Easings;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.MathHelper;
import ru.zero.util.render.math.animation.Direction;
import ru.zero.util.render.text.FontRegistry;
import ru.zero.util.render.utils.KeyUtil;

@Environment(EnvType.CLIENT)
public class GuiRenderMain extends GuiScreen {
   public static void renderMain(Renderer2D renderer2D, MatrixStack pose, int mouseX, int mouseY, float mainAlpha) {
      if (mainAlpha <= 0.001F) {
         return;
      }

      boolean searchActive = GuiScreen.activeSearch;
      MovementManager movementManager = MovementManager.getInstance();
      if (searchActive) {
         movementManager.lockMovement("Search");
      } else {
         movementManager.unlockMovement("Search");
      }

      if (searchActive) {
         boolean backspaceDown = KeyUtil.isKeyDown(259);
         long currentTime = System.currentTimeMillis();
         if (backspaceDown) {
            if (!GuiScreen.backspaceHeld) {
               GuiScreen.backspaceHeld = true;
               GuiScreen.firstBackspacePressTime = currentTime;
               GuiScreen.lastBackspaceTime = currentTime;
               if (!GuiScreen.searchText.isEmpty()) {
                  GuiScreen.searchText = GuiScreen.searchText.substring(0, GuiScreen.searchText.length() - 1);
               }
            } else if (currentTime - GuiScreen.firstBackspacePressTime > 500L && currentTime - GuiScreen.lastBackspaceTime > 30L) {
               if (!GuiScreen.searchText.isEmpty()) {
                  GuiScreen.searchText = GuiScreen.searchText.substring(0, GuiScreen.searchText.length() - 1);
               }

               GuiScreen.lastBackspaceTime = currentTime;
            }
         } else {
            GuiScreen.backspaceHeld = false;
            GuiScreen.firstBackspacePressTime = 0L;
         }
      } else {
         GuiScreen.backspaceHeld = false;
         GuiScreen.firstBackspacePressTime = 0L;
      }

      int outlineColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(20.4F * mainAlpha));
      int backGroundThreeColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(10.2F * mainAlpha));
      int mainColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(255.0F * mainAlpha));
      int mainColor6 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(15.3F * mainAlpha));
      int mainColor40 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(102.0F * mainAlpha));
      int textColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int)(255.0F * mainAlpha));
      int backGroundOneColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), (int)(178.5F * mainAlpha));
      Color mainColorGlow35 = Renderer2D.ColorUtil.getColor(Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(56.0F * mainAlpha)));
      boolean vanillaStyle = ClickGUI.isVanillaStyle();
      if (vanillaStyle) {
         outlineColor = Renderer2D.ColorUtil.replAlpha(new Color(86, 86, 86).getRGB(), (int)(190.0F * mainAlpha));
         backGroundThreeColor = Renderer2D.ColorUtil.replAlpha(new Color(57, 57, 57).getRGB(), (int)(220.0F * mainAlpha));
         mainColor = Renderer2D.ColorUtil.replAlpha(new Color(170, 170, 170).getRGB(), (int)(255.0F * mainAlpha));
         mainColor6 = Renderer2D.ColorUtil.replAlpha(new Color(95, 95, 95).getRGB(), (int)(230.0F * mainAlpha));
         mainColor40 = Renderer2D.ColorUtil.replAlpha(new Color(138, 138, 138).getRGB(), (int)(255.0F * mainAlpha));
         textColor = Renderer2D.ColorUtil.replAlpha(new Color(230, 230, 230).getRGB(), (int)(255.0F * mainAlpha));
         backGroundOneColor = Renderer2D.ColorUtil.replAlpha(new Color(44, 44, 44).getRGB(), (int)(225.0F * mainAlpha));
         mainColorGlow35 = new Color(0, 0, 0, 0);
      }
      float x1 = GuiScreen.x + 104.735F;
      float y1 = GuiScreen.y + 34.025F;
      float rectWidth = 261.5F;
      float rectHeight = 209.5F;
      float clipX = x1 + 5.0F;
      float clipY = y1 + 5.0F;
      float clipWidth = rectWidth - 10.0F;
      float clipHeight = rectHeight - 10.0F;
      renderer2D.pushRoundedClipRect(clipX, clipY, clipWidth, clipHeight, 0.0F, 0.0F, 0.0F, 0.0F);

      List<Module> filteredModules = GuiScreen.modules;
      if (searchActive && !GuiScreen.searchText.isEmpty()) {
         String searchLower = GuiScreen.searchText.toLowerCase().trim();
         ArrayList<Module> result = new ArrayList<>();
         for (Module module : GuiScreen.modules) {
            if (module.name.toLowerCase().contains(searchLower)) {
               result.add(module);
            }
         }

         filteredModules = result;
      }

      Map<Module, List<Setting>> settingsCache = new HashMap<>(Math.max(16, filteredModules.size()));
      Map<Module, Float> settingsHeightCache = new HashMap<>(Math.max(16, filteredModules.size()));
      for (Module module : filteredModules) {
         settingsCache.put(module, module.getSettingsForGUI());
      }

      float calcDownY = 0.0F;
      float calcDownYSetting1 = 0.0F;
      float calcDownYSetting2 = 0.0F;
      float maxHeightColumn1 = 0.0F;
      float maxHeightColumn2 = 0.0F;
      int calcIndex = 1;

      for (Module module : filteredModules) {
         module.animation.update();
         module.animation.run(module.enable ? 1.0 : 0.0, 0.15F, Easings.SINE_OUT);
         module.animation1.setDirection(module.enable ? Direction.FORWARDS : Direction.BACKWARDS);
         float animPC = module.animation.get();
         float settingsHeight = 12.0F;
         float fullSettingsHeight = 12.0F;
         float settingsAnim = GuiScreen.getModuleSettingsAnimation(module).get();
         float settingsAlphaAnim = GuiScreen.getModuleSettingsAlphaAnimation(module).get();
         if (GuiScreen.openSettingsModules.contains(module) || settingsAnim > 0.0F || settingsAlphaAnim > 0.0F) {
            List<Setting> moduleSettings = settingsCache.get(module);
            for (Setting setting : moduleSettings) {
               fullSettingsHeight += GuiRenderSetting.getSettingHeight(renderer2D, setting);
            }

            fullSettingsHeight = Math.max(fullSettingsHeight, 20.0F);
            settingsHeight = 12.0F + (fullSettingsHeight - 12.0F) * settingsAnim;
         }
         settingsHeightCache.put(module, settingsHeight);

         if (calcIndex % 2 == 0) {
            float currentDownY = calcDownY + calcDownYSetting2 - 30.0F;
            float moduleHeight = 21.325F;
            moduleHeight += settingsHeight;
            float totalY = currentDownY + moduleHeight;
            maxHeightColumn2 = Math.max(maxHeightColumn2, totalY);
            calcDownYSetting2 += settingsHeight;
         } else {
            float currentDownY = calcDownY + calcDownYSetting1;
            float moduleHeight = 21.325F;
            moduleHeight += settingsHeight;
            float totalY = currentDownY + moduleHeight;
            maxHeightColumn1 = Math.max(maxHeightColumn1, totalY);
            calcDownYSetting1 += settingsHeight;
            calcDownY += 30.325F;
         }

         calcIndex++;
      }

      float totalHeight = Math.max(maxHeightColumn1, maxHeightColumn2);
      float contentHeight = totalHeight + 8.0F;
      float x2 = GuiScreen.x + 104.735F;
      float y2 = GuiScreen.y + 34.025F;
      boolean check = isHovered(mouseX, mouseY, x2 + 5.0F, y2 + 5.0F, rectWidth - 10.0F, rectHeight - 10.0F);
      GuiScreen.getScrollUtil().setSpeed(6.0F);
      GuiScreen.getScrollUtil().setEnabled(check);
      GuiScreen.getScrollUtil().update();
      GuiScreen.getScrollUtil().setMax(contentHeight, rectHeight - 10.0F);
      float yShar = -0.35F;
      float yZnar = -0.7F;
      int index = 1;
      float downY = GuiScreen.getScrollUtil().getScroll();
      float downYSetting1 = 0.0F;
      float downYSetting2 = 0.0F;

      for (Module module : filteredModules) {
         if (index % 2 == 0) {
            float animPCx = module.animation.get();
            float currentDownY = downY + downYSetting2 - 30.0F;
            float settingsAnimx = GuiScreen.getModuleSettingsAnimation(module).get();
            float settingsAlphaAnimx = GuiScreen.getModuleSettingsAlphaAnimation(module).get();
            float settingsHeightx = settingsHeightCache.getOrDefault(module, 12.0F);
            List<Setting> moduleSettings = settingsCache.get(module);

            if (!(settingsAnimx > 0.0F) && !(settingsAlphaAnimx > 0.0F)) {
               renderer2D.rectOutline(GuiScreen.x + 238.35F, GuiScreen.y + 43.365F + currentDownY, 121.47F, 21.325F, 6.5F, outlineColor, 0.1F);
               renderer2D.rect(GuiScreen.x + 238.35F, GuiScreen.y + 43.365F + currentDownY, 121.47F, 21.325F, 6.5F, backGroundThreeColor);
            } else {
               renderer2D.rectOutline(GuiScreen.x + 238.35F, GuiScreen.y + 43.365F + currentDownY, 121.47F, 21.325F + settingsHeightx, 6.5F, outlineColor, 0.1F);
               renderer2D.rect(GuiScreen.x + 238.35F, GuiScreen.y + 43.365F + currentDownY, 121.47F, 21.325F + settingsHeightx, 6.5F, backGroundThreeColor);
               if (settingsAlphaAnimx > 0.01F) {
                  renderer2D.rect(
                     GuiScreen.x + 238.515F, GuiScreen.y + 64.69F + currentDownY, 121.47F, 1.0F, ColorUtil.multAlpha(outlineColor, settingsAlphaAnimx)
                  );
               }
            }

            float moduleNameX = GuiScreen.x + 247.895F;
            float moduleNameY = GuiScreen.y + 49.555F + currentDownY;
            renderer2D.text(FontRegistry.INTER_MEDIUM, moduleNameX, moduleNameY + 6.6F, 14.0F, module.name, ColorUtil.overCol(mainColor40, textColor, animPCx));
            float bindAnim = GuiScreen.getModuleBindAnimation(module).get();
            if (module.binding || module.bind != -1 || bindAnim > 0.0F) {
               float bindHeight = 10.0F;
               String keyText = module.binding ? "..." : (module.bind != -1 ? Keyboard.keyName(module.bind) : "");
               float keyTextWidth = keyText.isEmpty() ? 0.0F : renderer2D.measureText(FontRegistry.INTER_MEDIUM, keyText, 12.0F).width;
               float minButtonWidth = 6.0F;
               float buttonWidth = Math.max(minButtonWidth, keyTextWidth + 6.0F);
               float moduleNameWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, module.name, 14.0F).width;
               float bindX = moduleNameX + moduleNameWidth + 4.0F;
               float bindY = moduleNameY - 0.35F;
               renderer2D.rectOutline(bindX, bindY, buttonWidth, bindHeight, 3.0F, ColorUtil.multAlpha(outlineColor, bindAnim), 0.1F);
               renderer2D.rect(bindX, bindY, buttonWidth, bindHeight, 3.0F, ColorUtil.multAlpha(mainColor6, bindAnim));
               if (!keyText.isEmpty()) {
                  renderer2D.text(
                     FontRegistry.INTER_MEDIUM,
                     bindX + buttonWidth / 2.0F - keyTextWidth / 2.0F - 0.2F,
                     bindY + 2.0F + 5.25F,
                     12.0F,
                     keyText,
                     ColorUtil.multAlpha(module.binding ? mainColor : mainColor40, bindAnim)
                  );
               }
            }

            renderer2D.rectOutline(GuiScreen.x + 348.415F - 1.5F, GuiScreen.y + 52.505F + currentDownY - 1.5F + yShar, 6.0F, 6.0F, 3.0F, outlineColor, 0.08F);
            renderer2D.rect(GuiScreen.x + 348.415F - 1.5F, GuiScreen.y + 52.505F + currentDownY - 1.5F + yShar, 6.0F, 6.0F, 3.0F, mainColor6);
            renderer2D.rect(
               GuiScreen.x + 349.27F - 0.75F,
               GuiScreen.y + 53.365F + currentDownY - 0.78F + yShar,
               3.0F,
               3.0F,
               1.5F,
               ColorUtil.overCol(mainColor40, mainColor, animPCx)
            );
            if (!vanillaStyle) {
               renderer2D.shadow(
                  GuiScreen.x + 349.27F + 0.7F,
                  GuiScreen.y + 53.365F + currentDownY + yShar,
                  0.1F,
                  0.1F,
                  1.5F,
                  2.575F,
                  0.1F,
                  ColorUtil.overCol(0, mainColorGlow35.getRGB(), animPCx)
               );
            }
            if (!moduleSettings.isEmpty()) {
               renderer2D.text(
                  vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS,
                  GuiScreen.x + 337.975F,
                  GuiScreen.y + 52.81F + currentDownY - 1.5F + yZnar + 6.5F + 6.0F - 6.0F * settingsAnimx,
                  11.0F,
                  vanillaStyle ? "+" : "S",
                  ColorUtil.overCol(0, mainColor, settingsAnimx)
               );
               renderer2D.text(
                  vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS,
                  GuiScreen.x + 337.975F,
                  GuiScreen.y + 52.81F + currentDownY - 1.5F + yZnar + 6.5F + 6.0F * settingsAnimx,
                  11.0F,
                  vanillaStyle ? "-" : "R",
                  ColorUtil.overCol(mainColor40, 0, settingsAnimx)
               );
            }

            if (settingsAnimx > 0.0F || settingsAlphaAnimx > 0.0F) {
               float settingY = GuiScreen.y + 64.69F + currentDownY + 4.0F;
               float settingX = GuiScreen.x + 238.35F + 9.0F;
               float settingWidth = 105.47F;
               float totalSettingsHeight = 0.0F;

               for (Setting setting : moduleSettings) {
                  totalSettingsHeight += GuiRenderSetting.renderSetting(
                        renderer2D,
                        setting,
                        settingX,
                        settingY + totalSettingsHeight,
                        settingWidth,
                        mouseX,
                        mouseY,
                        ColorUtil.multAlpha(outlineColor, settingsAlphaAnimx),
                        ColorUtil.multAlpha(mainColor, settingsAlphaAnimx),
                        ColorUtil.multAlpha(mainColor6, settingsAlphaAnimx),
                        ColorUtil.multAlpha(mainColor40, settingsAlphaAnimx),
                        ColorUtil.multAlpha(textColor, settingsAlphaAnimx),
                        mainAlpha * settingsAlphaAnimx
                     )
                     * settingsAlphaAnimx;
               }

               downYSetting2 += settingsHeightx;
            }
         } else {
            float animPCxx = module.animation.get();
            float currentDownYx = downY + downYSetting1;
            float settingsAnimxx = GuiScreen.getModuleSettingsAnimation(module).get();
            float settingsAlphaAnimxx = GuiScreen.getModuleSettingsAlphaAnimation(module).get();
            float settingsHeightxx = settingsHeightCache.getOrDefault(module, 12.0F);
            List<Setting> moduleSettings = settingsCache.get(module);

            if (!(settingsAnimxx > 0.0F) && !(settingsAlphaAnimxx > 0.0F)) {
               renderer2D.rectOutline(GuiScreen.x + 111.885F, GuiScreen.y + 43.365F + currentDownYx, 121.47F, 21.325F, 6.5F, outlineColor, 0.1F);
               renderer2D.rect(GuiScreen.x + 111.885F, GuiScreen.y + 43.365F + currentDownYx, 121.47F, 21.325F, 6.5F, backGroundThreeColor);
            } else {
               renderer2D.rectOutline(
                  GuiScreen.x + 111.885F, GuiScreen.y + 43.365F + currentDownYx, 121.47F, 21.325F + settingsHeightxx, 6.5F, outlineColor, 0.1F
               );
               renderer2D.rect(GuiScreen.x + 111.885F, GuiScreen.y + 43.365F + currentDownYx, 121.47F, 21.325F + settingsHeightxx, 6.5F, backGroundThreeColor);
               if (settingsAlphaAnimxx > 0.01F) {
                  renderer2D.rect(
                     GuiScreen.x + 111.885F, GuiScreen.y + 64.69F + currentDownYx, 121.47F, 1.0F, ColorUtil.multAlpha(outlineColor, settingsAlphaAnimxx)
                  );
               }
            }

            float moduleNameXx = GuiScreen.x + 121.425F;
            float moduleNameYx = GuiScreen.y + 49.555F + currentDownYx;
            renderer2D.text(
               FontRegistry.INTER_MEDIUM, moduleNameXx, moduleNameYx + 6.6F, 14.0F, module.name, ColorUtil.overCol(mainColor40, textColor, animPCxx)
            );
            float bindAnimx = GuiScreen.getModuleBindAnimation(module).get();
            if (module.binding || module.bind != -1 || bindAnimx > 0.0F) {
               float bindHeight = 10.0F;
               String keyText = module.binding ? "..." : (module.bind != -1 ? Keyboard.keyName(module.bind) : "");
               float keyTextWidth = keyText.isEmpty() ? 0.0F : renderer2D.measureText(FontRegistry.INTER_MEDIUM, keyText, 12.0F).width;
               float minButtonWidth = 6.0F;
               float buttonWidth = Math.max(minButtonWidth, keyTextWidth + 6.0F);
               float moduleNameWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, module.name, 14.0F).width;
               float bindX = moduleNameXx + moduleNameWidth + 4.0F;
               float bindY = moduleNameYx - 0.35F;
               renderer2D.rectOutline(bindX, bindY, buttonWidth, bindHeight, 3.0F, ColorUtil.multAlpha(outlineColor, bindAnimx), 0.1F);
               renderer2D.rect(bindX, bindY, buttonWidth, bindHeight, 3.0F, ColorUtil.multAlpha(mainColor6, bindAnimx));
               if (!keyText.isEmpty()) {
                  renderer2D.text(
                     FontRegistry.INTER_MEDIUM,
                     bindX + buttonWidth / 2.0F - keyTextWidth / 2.0F - 0.2F,
                     bindY + 2.0F + 5.25F,
                     12.0F,
                     keyText,
                     ColorUtil.multAlpha(module.binding ? mainColor : mainColor40, bindAnimx)
                  );
               }
            }

            renderer2D.rectOutline(GuiScreen.x + 221.875F - 1.5F, GuiScreen.y + 52.505F + currentDownYx - 1.5F + yShar, 6.0F, 6.0F, 3.0F, outlineColor, 0.08F);
            renderer2D.rect(GuiScreen.x + 221.875F - 1.5F, GuiScreen.y + 52.505F + currentDownYx - 1.5F + yShar, 6.0F, 6.0F, 3.0F, mainColor6);
            renderer2D.rect(
               GuiScreen.x + 222.735F - 0.75F,
               GuiScreen.y + 53.365F + currentDownYx - 0.78F + yShar,
               3.0F,
               3.0F,
               1.5F,
               ColorUtil.overCol(mainColor40, mainColor, animPCxx)
            );
            if (!vanillaStyle) {
               renderer2D.shadow(
                  GuiScreen.x + 222.735F + 0.7F,
                  GuiScreen.y + 53.365F + currentDownYx + yShar,
                  0.1F,
                  0.1F,
                  1.5F,
                  2.575F,
                  0.1F,
                  ColorUtil.overCol(0, mainColorGlow35.getRGB(), animPCxx)
               );
            }
            if (!moduleSettings.isEmpty()) {
               renderer2D.text(
                  vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS,
                  GuiScreen.x + 211.48F,
                  GuiScreen.y + 52.81F + currentDownYx - 1.5F + yZnar + 6.5F + 6.0F - 6.0F * settingsAnimxx,
                  11.0F,
                  vanillaStyle ? "+" : "S",
                  ColorUtil.overCol(0, mainColor, settingsAnimxx)
               );
               renderer2D.text(
                  vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS,
                  GuiScreen.x + 211.48F,
                  GuiScreen.y + 52.81F + currentDownYx - 1.5F + yZnar + 6.5F + 6.0F * settingsAnimxx,
                  11.0F,
                  vanillaStyle ? "-" : "R",
                  ColorUtil.overCol(mainColor40, 0, settingsAnimxx)
               );
            }

            if (settingsAnimxx > 0.0F || settingsAlphaAnimxx > 0.0F) {
               float settingY = GuiScreen.y + 64.69F + currentDownYx + 4.0F;
               float settingX = GuiScreen.x + 111.885F + 9.0F;
               float settingWidth = 105.47F;
               float totalSettingsHeight = 0.0F;

               for (Setting setting : moduleSettings) {
                  totalSettingsHeight += GuiRenderSetting.renderSetting(
                        renderer2D,
                        setting,
                        settingX,
                        settingY + totalSettingsHeight,
                        settingWidth,
                        mouseX,
                        mouseY,
                        ColorUtil.multAlpha(outlineColor, settingsAlphaAnimxx),
                        ColorUtil.multAlpha(mainColor, settingsAlphaAnimxx),
                        ColorUtil.multAlpha(mainColor6, settingsAlphaAnimxx),
                        ColorUtil.multAlpha(mainColor40, settingsAlphaAnimxx),
                        ColorUtil.multAlpha(textColor, settingsAlphaAnimxx),
                        mainAlpha * settingsAlphaAnimxx
                     )
                     * settingsAlphaAnimxx;
               }

               downYSetting1 += settingsHeightxx;
            }

            downY += 30.325F;
         }

         index++;
      }

      renderer2D.popClipRect();
      GuiScreen.getScrollUtil().render(renderer2D, GuiScreen.x + 104.735F + 261.5F - 5.0F + 1.0F, GuiScreen.y + 34.025F + 5.0F, 2.0F, 194.5F, mainAlpha);
      if (GuiScreen.activeColorPicker != null && GuiScreen.activeColorPicker instanceof HueSetting) {
         GuiRenderColorPicker.renderColorPickerWindow(
            renderer2D,
            GuiScreen.activeColorPicker,
            mouseX,
            mouseY,
            ColorUtil.multAlpha(outlineColor, GuiScreen.animation15.getOutput()),
            ColorUtil.multAlpha(backGroundOneColor, GuiScreen.animation15.getOutput()),
            ColorUtil.multAlpha(mainColor40, GuiScreen.animation15.getOutput()),
            mainAlpha * GuiScreen.animation15.getOutput()
         );
      }
   }

   public static boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {
      return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
   }
}
