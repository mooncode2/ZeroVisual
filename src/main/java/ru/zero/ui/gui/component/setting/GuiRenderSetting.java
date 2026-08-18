package ru.zero.ui.gui.component.setting;

import java.awt.Color;
import java.util.HashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BindSettings;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.module.api.setting.impl.MultiBooleanSetting;
import ru.zero.module.api.setting.impl.NoneSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.module.api.setting.impl.StringSetting;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.util.client.Lang;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.animation.util.Animation;
import ru.zero.util.render.animation.util.Easings;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.MathHelper;
import ru.zero.util.render.math.animation.AnimationMath;
import ru.zero.util.render.math.animation.anim.util.Animation2;
import ru.zero.util.render.text.FontRegistry;
import ru.zero.util.render.utils.KeyUtil;

@Environment(EnvType.CLIENT)
public class GuiRenderSetting {
   public static Animation multiAnim = new Animation();
   public static HashMap<String, Float> animation = new HashMap<>();
   public static HashMap<String, Float> animation2 = new HashMap<>();
   public static HashMap<String, Float> multiBooleanAnimation = new HashMap<>();

   public static float getSettingHeight(Renderer2D renderer2D, Setting setting) {
      if (setting instanceof NoneSetting) {
         return ((NoneSetting)setting).get();
      } else if (setting instanceof BooleanSetting) {
         return 10.0F;
      } else if (setting instanceof SliderSetting) {
         return 19.0F;
      } else if (setting instanceof ModeSetting modeSetting) {
         float width = 105.47F;
         float modeSpacing = 2.0F;
         float modeHeight = 10.075F;
         float padding = 3.0F;
         float verticalSpacing = -2.0F;
         float calcX = padding;
         float calcY = 0.0F;

         for (String mode : modeSetting.modes) {
            float modeWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, Lang.t(mode), 12.0F).width + padding * 2.0F;
            if (calcX + modeWidth > width && calcX > padding) {
               calcX = padding;
               calcY += modeHeight + verticalSpacing;
            }

            calcX += modeWidth + modeSpacing;
         }

         return calcY + modeHeight + 12.0F;
      } else if (setting instanceof BindSettings) {
         return 13.0F;
      } else if (setting instanceof StringSetting) {
         return 15.0F;
      } else if (setting instanceof HueSetting) {
         return 15.0F;
      } else if (setting instanceof MultiBooleanSetting multiBooleanSetting) {
         float height = 0.0F;
         float startY = 10.0F;
         float currentX = 0.0F;
         float currentY = startY;
         float spacing = 3.0F;
         float boolHeight = 10.0F;
         float padding = 4.0F;
         float width = 105.47F;

         for (BooleanSetting boolSetting : multiBooleanSetting.settings) {
            float nameWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, Lang.t(boolSetting.name), 12.0F).width;
            float boolWidth = nameWidth + padding * 2.0F;
            if (currentX + boolWidth > 0.0F + width) {
               currentX = 0.0F;
               currentY += boolHeight + spacing;
            }

            currentX += boolWidth + spacing;
         }

         return currentY - 0.0F + boolHeight;
      } else {
         return 15.0F;
      }
   }

   public static float renderSetting(
      Renderer2D renderer2D,
      Setting setting,
      float x,
      float y,
      float width,
      int mouseX,
      int mouseY,
      int outlineColor,
      int mainColor,
      int mainColor6,
      int mainColor40,
      int textColor,
      float mainAlpha
   ) {
      float height = 0.0F;
      if (setting instanceof NoneSetting) {
         height = ((NoneSetting)setting).get();
      } else if (setting instanceof BooleanSetting boolSetting) {
         boolean value = boolSetting.get();
         float checkBoxSize = 8.0F;
         float checkBoxX = x + width - checkBoxSize - 3.0F;
         float checkBoxY = y + 2.0F;
         boolSetting.anim.update();
         boolSetting.anim.run(value ? 1.0 : 0.0, 0.15F, Easings.SINE_OUT);
         renderer2D.rectOutline(checkBoxX, checkBoxY, checkBoxSize, checkBoxSize, 3.0F, outlineColor, 0.1F);
         renderer2D.rect(checkBoxX, checkBoxY, checkBoxSize, checkBoxSize, 3.0F, mainColor6);
         renderer2D.rect(checkBoxX + 2.3F, checkBoxY + 2.2F, 3.42F, 3.425F, 3.0F, ColorUtil.overCol(0, mainColor, boolSetting.anim.get()));
         drawFitText(renderer2D, FontRegistry.INTER_MEDIUM, Lang.t(setting.name), x, y + 3.0F + 5.0F, 13.0F, width - 14.0F, mainColor40);
         height = 10.0F;
      } else if (setting instanceof SliderSetting sliderSetting) {
         float sliderHeight = 4.0F;
         float sliderY = y + 10.0F;
         float sliderWidth = width - 2.5F;
         Animation2 sliderAnim = GuiScreen.getSliderAnimation(sliderSetting);
         float targetProgress = (sliderSetting.current - sliderSetting.minimum) / (sliderSetting.maximum - sliderSetting.minimum);
         double toValue = sliderAnim.getToValue();
         sliderAnim.update();
         sliderAnim.run(targetProgress, 0.24F, ru.zero.util.render.math.animation.anim.util.Easings.QUART_OUT);
         float progress = (float)sliderAnim.getValue();
         float progressWidth = sliderWidth * progress;
         renderer2D.rectOutline(x, sliderY + 2.0F, sliderWidth, sliderHeight, 2.0F, outlineColor, 0.3F);
         renderer2D.rect(x, sliderY + 2.0F, sliderWidth, sliderHeight, 2.0F, mainColor6);
         renderer2D.rect(x + 1.0F, sliderY + 2.5F, progressWidth - 2.0F, sliderHeight - 1.0F, 2.0F, mainColor);
         renderer2D.rect(x + 1.0F + progressWidth - 5.0F + (progressWidth == 0.0F ? 5 : 2), sliderY + 2.2F, 5.0F, 3.88F, 2.0F, textColor);
         String valueText = sliderSetting.percent
            ? String.format("%.1f%%", sliderSetting.current)
            : String.format("%.1f / %.1f", sliderSetting.current, sliderSetting.maximum);
         drawFitText(renderer2D, FontRegistry.INTER_MEDIUM, Lang.t(setting.name), x, y + 1.0F + 7.0F, 13.0F, width - 50.0F, mainColor40);
         renderer2D.text(
            FontRegistry.INTER_MEDIUM,
            x + sliderWidth - renderer2D.measureText(FontRegistry.INTER_MEDIUM, valueText, 13.0F).width - 2.0F,
            y + 7.0F,
            13.0F,
            valueText,
            mainColor
         );
         height = 19.0F;
      } else if (setting instanceof ModeSetting modeSetting) {
         for (String s : modeSetting.modes) {
            animation.putIfAbsent(s, 0.0F);
         }

         drawFitText(renderer2D, FontRegistry.INTER_MEDIUM, Lang.t(setting.name), x, y + 7.0F, 13.0F, width, mainColor40);
         float modeSpacing = 2.0F;
         float modeHeight = 10.075F;
         float padding = 3.0F;
         float verticalSpacing = -2.0F;
         float calcX = padding;
         float calcY = 0.0F;

         for (String mode : modeSetting.modes) {
            float modeWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, Lang.t(mode), 12.0F).width + padding * 2.0F;
            if (calcX + modeWidth > width && calcX > padding) {
               calcX = padding;
               calcY += modeHeight + verticalSpacing;
            }

            calcX += modeWidth + modeSpacing;
         }

         float modeContainerY = y + 10.0F;
         float modeContainerHeight = calcY + modeHeight;
         renderer2D.rectOutline(x, modeContainerY, width, modeContainerHeight, 3.0F, outlineColor, 0.1F);
         renderer2D.rect(x, modeContainerY, width, modeContainerHeight, 3.0F, mainColor6);
         float currentX = padding;
         float currentY = 1.5F;

         for (String mode : modeSetting.modes) {
            boolean isSelected = mode.equals(modeSetting.currentMode);
            float modeWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, Lang.t(mode), 12.0F).width + padding * 2.0F;
            if (currentX + modeWidth > width && currentX > padding) {
               currentX = padding;
               currentY += modeHeight + verticalSpacing;
            }

            float anim = animation.get(mode);
            float target = isSelected ? 1.0F : 0.0F;
            anim = AnimationMath.fast(anim, target, 10.0F);
            animation.put(mode, anim);
            int modeColor = ColorUtil.overCol(mainColor40, mainColor, anim);
            renderer2D.text(FontRegistry.INTER_MEDIUM, x + currentX, modeContainerY + currentY + 5.5F, 12.0F, Lang.t(mode), modeColor);
            currentX += modeWidth + modeSpacing;
         }

         height = modeContainerHeight + 12.0F;
      } else if (setting instanceof BindSettings bindSetting) {
         float bindHeight = 10.075F;
         String displayName = setting.name != null && !setting.name.isEmpty() ? Lang.t(setting.name) : "KEY";
         String keyText = bindSetting.active ? "..." : KeyUtil.getKey(bindSetting.key);
         float keyTextWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, keyText, 12.0F).width;
         float minButtonWidth = 16.055F;
         float buttonWidth = Math.max(minButtonWidth, keyTextWidth + 8.0F);
         float bindButtonX = x + width - buttonWidth - 2.0F;
         if (bindButtonX < x) {
            bindButtonX = x;
            buttonWidth = width - 2.0F;
         }

         drawFitText(renderer2D, FontRegistry.INTER_MEDIUM, displayName, x, y + 1.0F + 6.8F, 13.0F, Math.max(20.0F, bindButtonX - x - 4.0F), mainColor40);
         float backgroundX = bindButtonX - 6.0F;
         float backgroundWidth = buttonWidth + 2.0F;
         if (backgroundX < x) {
            backgroundWidth = backgroundX + backgroundWidth - x;
            backgroundX = x;
         }

         renderer2D.rectOutline(backgroundX, y, backgroundWidth, bindHeight, 3.0F, outlineColor, 0.1F);
         renderer2D.rect(backgroundX, y, backgroundWidth, bindHeight, 3.0F, mainColor6);
         renderer2D.text(
            FontRegistry.INTER_MEDIUM,
            backgroundX + backgroundWidth / 2.0F - keyTextWidth / 2.0F,
            y + 1.5F + 5.7F,
            12.0F,
            keyText,
            bindSetting.active ? mainColor : mainColor40
         );
         height = 13.0F;
      } else if (setting instanceof StringSetting stringSetting) {
         float textFieldHeight = 10.075F;
         float textFieldWidth = 63.56F;
         float textFieldX = x + 42.0F;
         float textX = textFieldX + 5.0F;
         float textY = y + 1.5F;
         drawFitText(renderer2D, FontRegistry.INTER_MEDIUM, Lang.t(setting.name), x, y + 1.0F + 6.5F, 13.0F, 38.0F, mainColor40);
         renderer2D.rectOutline(textFieldX, y, textFieldWidth, textFieldHeight, 3.0F, outlineColor, 0.1F);
         renderer2D.rect(textFieldX, y, textFieldWidth, textFieldHeight, 3.0F, mainColor6);
         String inputText = stringSetting.input;
         boolean isEmpty = inputText.isEmpty();
         float cursorX = textX;
         if (isEmpty) {
            renderer2D.text(FontRegistry.INTER_MEDIUM, textX - 2.0F, textY - 0.5F + 6.1F, 12.0F, "Enter text", mainColor40);
         } else {
            float currentX = textX;
            float maxX = textFieldX + textFieldWidth - 5.0F;
            float gradientStartX = textX;
            float gradientEndX = textFieldX + textFieldWidth - 5.0F;

            for (int i = 0; i < inputText.length(); i++) {
               char c = inputText.charAt(i);
               String charStr = String.valueOf(c);
               float charWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, charStr, 12.0F).width;
               if (currentX + charWidth > maxX) {
                  cursorX = currentX;
                  break;
               }

               textColor = mainColor40;
               if (i >= 16) {
                  float fadeStartX = gradientStartX + renderer2D.measureText(FontRegistry.INTER_MEDIUM, inputText.substring(0, 16), 12.0F).width;
                  float gradientWidth = Math.min(30.0F, gradientEndX - fadeStartX);
                  if (gradientWidth > 0.0F) {
                     float fadeProgress = (currentX - fadeStartX) / gradientWidth;
                     fadeProgress = MathHelper.clamp(fadeProgress, 0.0F, 1.0F);
                     int alpha = mainColor40 >> 24 & 0xFF;
                     alpha = (int)(alpha * (1.0F - fadeProgress));
                     textColor = Renderer2D.ColorUtil.replAlpha(mainColor40, alpha);
                  } else {
                     textColor = Renderer2D.ColorUtil.replAlpha(mainColor40, 0);
                  }
               }

               renderer2D.text(FontRegistry.INTER_MEDIUM, currentX - 2.0F, textY - 0.5F + 6.1F, 12.0F, charStr, textColor);
               currentX += charWidth;
               cursorX = currentX;
            }
         }

         boolean isActive = GuiScreen.activeStringSetting == stringSetting && stringSetting.active;
         if (isActive) {
            long currentTime = System.currentTimeMillis();
            boolean showCursor = currentTime / 500L % 2L == 0L;
            if (showCursor) {
               renderer2D.rect(cursorX - 3.0F, textY - 0.5F, 1.0F, 8.0F, 0.5F, mainColor);
            }
         }

         height = 15.0F;
      } else if (setting instanceof HueSetting hueSetting) {
         float colorHeight = 12.0F;
         float colorWidth = 40.0F;
         float colorX = x + width - colorWidth - 2.0F;
         drawFitText(renderer2D, FontRegistry.INTER_MEDIUM, Lang.t(setting.name), x, y + 1.0F + 7.0F, 13.0F, width - 50.0F, mainColor40);
         Color hueColor = hueSetting.getColor();
         renderer2D.rectOutline(colorX - 10.0F, y, 46.48F, 10.075F, 3.0F, outlineColor, 0.1F);
         renderer2D.rect(colorX - 10.0F, y, 46.48F, 10.075F, 3.0F, mainColor6);
         renderer2D.rect(
            colorX + 32.0F - 10.0F,
            y + 0.8F,
            13.285F,
            8.315F,
            0.0F,
            3.0F,
            3.0F,
            0.0F,
            Renderer2D.ColorUtil.replAlpha(hueColor.getRGB(), (int)(255.0F * mainAlpha))
         );
         String hexText = String.format("#%02X%02X%02X", hueColor.getRed(), hueColor.getGreen(), hueColor.getBlue());
         renderer2D.text(
            FontRegistry.INTER_MEDIUM,
            colorX + colorWidth / 2.0F - renderer2D.measureText(FontRegistry.INTER_MEDIUM, hexText, 12.0F).width / 2.0F - 14.0F,
            y + 1.5F + 5.7F,
            12.0F,
            hexText,
            mainColor40
         );
         height = 15.0F;
      } else if (setting instanceof MultiBooleanSetting multiBooleanSetting) {
         drawFitText(renderer2D, FontRegistry.INTER_MEDIUM, Lang.t(setting.name), x, y + 7.0F, 13.0F, width, mainColor40);
         float startY = y + 10.0F;
         float currentX = x;
         float currentY = startY;
         float spacing = 3.0F;
         float boolHeight = 10.0F;
         float padding = 4.0F;

         for (BooleanSetting boolSetting : multiBooleanSetting.settings) {
            float nameWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, Lang.t(boolSetting.name), 12.0F).width;
            float boolWidth = nameWidth + padding * 2.0F;
            if (currentX + boolWidth > x + width) {
               currentX = x;
               currentY += boolHeight + spacing;
            }

            renderer2D.rectOutline(currentX, currentY, boolWidth, boolHeight, 3.0F, outlineColor, 0.1F);
            renderer2D.rect(currentX, currentY, boolWidth, boolHeight, 3.0F, mainColor6);
            String animKey = setting.name + "_" + boolSetting.name;
            multiBooleanAnimation.putIfAbsent(animKey, boolSetting.get() ? 1.0F : 0.0F);
            float anim = multiBooleanAnimation.get(animKey);
            float target = boolSetting.get() ? 1.0F : 0.0F;
            anim = AnimationMath.fast(anim, target, 10.0F);
            multiBooleanAnimation.put(animKey, anim);
            textColor = ColorUtil.overCol(mainColor40, mainColor, anim);
            renderer2D.text(FontRegistry.INTER_MEDIUM, currentX + padding, currentY + 3.0F - 1.0F + 5.0F, 12.0F, Lang.t(boolSetting.name), textColor);
            currentX += boolWidth + spacing;
         }

         height = currentY - y + boolHeight;
      }

      return height + 1.0F;
   }
   private static void drawFitText(Renderer2D r2, ru.zero.util.render.text.FontObject font, String text, float x, float y, float baseSize, float maxWidth, int color) {
      if (text == null || text.isEmpty() || maxWidth <= 0.0F) {
         r2.text(font, x, y, baseSize, text == null ? "" : text, color);
         return;
      }
      float w = r2.measureText(font, text, baseSize).width;
      if (w <= maxWidth) {
         r2.text(font, x, y, baseSize, text, color);
         return;
      }
      float size = baseSize * (maxWidth / w);
      if (size < 8.0F) {
         size = 8.0F;
      }
      r2.text(font, x, y, size, text, color);
   }
}