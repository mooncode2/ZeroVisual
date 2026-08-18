package ru.zero.ui.gui.color;

import java.awt.Color;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.component.render.GuiRenderMain;
import ru.zero.util.client.Lang;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.animation.Direction;
import ru.zero.util.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
public final class GuiAdvancedColorPicker {
   public static final float WIDTH = 136.0F;
   public static final float HEIGHT_WITH_ALPHA = 137.0F;
   public static final float HEIGHT_NO_ALPHA = 137.0F;

   private static final float PALETTE_X = 8.0F;
   private static final float PALETTE_Y = 26.0F;
   private static final float PALETTE_W = 94.0F;
   private static final float PALETTE_H = 56.0F;
   private static final float HUE_X = 106.0F;
   private static final float HUE_Y = 26.0F;
   private static final float HUE_W = 8.0F;
   private static final float HUE_H = 56.0F;
   private static final float ALPHA_Y = 88.0F;
   private static final float ALPHA_W = 94.0F;
   private static final float ALPHA_H = 8.0F;
   private static final float PREVIEW_X = 8.0F;
   private static final float PREVIEW_Y = 100.0F;
   private static final float PREVIEW_W = 16.0F;
   private static final float PREVIEW_H = 12.0F;
   private static final float HEX_X = 30.0F;
   private static final float HEX_Y = 103.0F;
   private static final float PRESET_START_Y = 116.0F;
   private static final float SWATCH = 12.0F;
   private static final float SWATCH_PITCH = 15.0F;
   private static final int SLOTS_PER_ROW = 8;
   private static final float BOTTOM_PAD = 6.0F;
   private static final float BUTTON_SIZE = 14.0F;

   private GuiAdvancedColorPicker() {
   }

   public static float height(HueSetting setting) {
      int slots = presetSlots();
      int rows = Math.max(1, (slots + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW);
      return PRESET_START_Y + rows * SWATCH_PITCH + BOTTOM_PAD;
   }

   public static float pickerDrawX(float x) {
      float anim = GuiScreen.animation15.getOutput();
      return x + (30.0F - 30.0F * anim);
   }

   public static void render(
         Renderer2D renderer,
         HueSetting setting,
         float x,
         float y,
         int mouseX,
         int mouseY,
         int outlineColor,
         int bgColor,
         int textColor,
         float alpha
   ) {
      float h = height(setting);
      float drawX = pickerDrawX(x);
      if (!GuiScreen.clientBlurSetting.get()) {
         renderer.prepareBlurRegion(drawX, y, WIDTH, h, 23.0F);
         renderer.blurRegion(drawX, y, WIDTH, h, 5.5F, alpha);
      }

      renderer.rectOutline(drawX, y, WIDTH, h, 6.0F, outlineColor, 0.12F);
      renderer.rect(drawX, y, WIDTH, h, 6.0F, bgColor);

      renderer.text(FontRegistry.INTER_MEDIUM, drawX + 9.0F, y + 10.0F, 12.0F, Lang.t(setting.name), textColor);
      renderCloseButton(renderer, drawX, y, outlineColor, bgColor, textColor);
      renderEyeDropperButton(renderer, setting, drawX, y, outlineColor, bgColor, textColor);

      float hue = setting.getHue();
      renderPalette(renderer, drawX + PALETTE_X, y + PALETTE_Y, PALETTE_W, PALETTE_H, hue, outlineColor);
      float cursorX = drawX + PALETTE_X + setting.saturation * PALETTE_W;
      float cursorY = y + PALETTE_Y + (1.0F - setting.brightness) * PALETTE_H;
      renderer.shadow(cursorX, cursorY, 1.0F, 1.0F, 5.0F, 5.0F, 0.0F, 0x6D000000);
      renderer.circle(cursorX, cursorY, 5.0F, 0.0F, 1.0F, new Color(0, 0, 0, 160).getRGB());
      renderer.circle(cursorX, cursorY, 3.6F, 0.0F, 1.0F, Color.WHITE.getRGB());

      renderHueBar(renderer, drawX + HUE_X, y + HUE_Y, HUE_W, HUE_H, outlineColor);
      float huePos = y + HUE_Y + hue * HUE_H;
      renderer.rect(drawX + HUE_X - 1.5F, huePos - 0.5F, HUE_W + 3.0F, 3.0F, 2.0F, new Color(0, 0, 0, 170).getRGB());
      renderer.rect(drawX + HUE_X - 0.75F, huePos, HUE_W + 1.5F, 2.0F, 2.0F, Color.WHITE.getRGB());

      if (setting.alphaEnabled) {
         float alphaY = y + ALPHA_Y;
         renderer.pushRoundedClipRect(drawX + PALETTE_X, alphaY, ALPHA_W, ALPHA_H, 4.0F, 4.0F, 4.0F, 4.0F);
         try {
            renderCheckerboard(renderer, drawX + PALETTE_X, alphaY, ALPHA_W, ALPHA_H);
         } finally {
            renderer.popClipRect();
         }
         Color rgb = Color.getHSBColor(hue, setting.saturation, setting.brightness);
         int opaque = new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255).getRGB();
         int transparent = new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 0).getRGB();
         renderer.horizontalGradient(drawX + PALETTE_X, alphaY, ALPHA_W, ALPHA_H, transparent, opaque);
         float alphaPos = drawX + PALETTE_X + setting.alpha * ALPHA_W;
         renderer.rect(alphaPos - 1.5F, alphaY - 1.0F, 3.0F, ALPHA_H + 2.0F, 1.5F, Color.WHITE.getRGB());
         renderer.text(FontRegistry.INTER_MEDIUM, drawX + HUE_X + 2.0F, alphaY + 8.0F, 10.0F, Math.round(setting.alpha * 100.0F) + "%", textColor);
      }

      Color preview = setting.getColor();
      renderer.rectOutline(drawX + PREVIEW_X, y + PREVIEW_Y, PREVIEW_W, PREVIEW_H, 4.0F, outlineColor, 0.12F);
      renderer.rect(drawX + PREVIEW_X, y + PREVIEW_Y, PREVIEW_W, PREVIEW_H, 4.0F, preview.getRGB());
      renderer.text(FontRegistry.INTER_MEDIUM, drawX + HEX_X, y + HEX_Y, 10.0F, setting.toHex(), ColorUtil.multAlpha(textColor, 0.85F));

      renderPresets(renderer, drawX, y, outlineColor, bgColor, textColor);

      if (GuiScreen.pickingScreenColor) {
         Color sampled = ScreenColorSampler.sample(mouseX, mouseY);
         float tipX = mouseX + 12.0F;
         float tipY = mouseY + 12.0F;
         renderer.rect(tipX, tipY, 96.0F, 30.0F, 6.0F, new Color(0, 0, 0, 190).getRGB());
         renderer.rectOutline(tipX, tipY, 96.0F, 30.0F, 6.0F, outlineColor, 0.2F);
         renderer.rect(tipX + 6.0F, tipY + 6.0F, 12.0F, 12.0F, 3.0F, sampled.getRGB());
         renderer.text(
               FontRegistry.INTER_MEDIUM,
               tipX + 24.0F,
               tipY + 7.0F,
               10.0F,
               String.format("#%02X%02X%02X", sampled.getRed(), sampled.getGreen(), sampled.getBlue()),
               Color.WHITE.getRGB()
         );
         renderer.text(FontRegistry.INTER_MEDIUM, tipX + 24.0F, tipY + 20.0F, 9.0F, Lang.t("Клик — взять цвет"), Color.WHITE.getRGB());
      }
   }

   public static boolean handleClick(HueSetting setting, float x, float y, int mouseX, int mouseY, int button) {
      if (button != 0 && button != 1) {
         return false;
      }

      float h = height(setting);
      if (!GuiRenderMain.isHovered(mouseX, mouseY, x, y, WIDTH, h)) {
         return false;
      }

      if (GuiRenderMain.isHovered(mouseX, mouseY, x + WIDTH - 36.0F, y + 4.0F, BUTTON_SIZE, BUTTON_SIZE)) {
         GuiScreen.pickingScreenColor = !GuiScreen.pickingScreenColor;
         return true;
      }

      if (GuiRenderMain.isHovered(mouseX, mouseY, x + WIDTH - 18.0F, y + 4.0F, BUTTON_SIZE, BUTTON_SIZE)) {
         close();
         return true;
      }

      if (GuiRenderMain.isHovered(mouseX, mouseY, x + PALETTE_X, y + PALETTE_Y, PALETTE_W, PALETTE_H)) {
         GuiScreen.pickingSaturationBrightness = true;
         updateSaturationBrightness(setting, x, y, mouseX, mouseY);
         return true;
      }

      if (GuiRenderMain.isHovered(mouseX, mouseY, x + HUE_X, y + HUE_Y, HUE_W, HUE_H)) {
         GuiScreen.pickingHue = true;
         updateHue(setting, x, y, mouseY);
         return true;
      }

      if (setting.alphaEnabled && GuiRenderMain.isHovered(mouseX, mouseY, x + PALETTE_X, y + ALPHA_Y, ALPHA_W, ALPHA_H)) {
         GuiScreen.pickingAlpha = true;
         updateAlpha(setting, x, mouseX);
         return true;
      }

      List<ColorPresetStorage.ColorPreset> presets = ColorPresetStorage.presets();
      int slots = presetSlots();
      for (int slot = 0; slot < slots; slot++) {
         float[] pos = presetSlotPos(slot, x, y);
         if (GuiRenderMain.isHovered(mouseX, mouseY, pos[0], pos[1], SWATCH, SWATCH)) {
            if (slot < presets.size()) {
               if (button == 1) {
                  ColorPresetStorage.removePreset(slot);
               } else {
                  setting.setColor(presets.get(slot).color());
                  autoSave();
               }
            } else if (button == 0) {
               ColorPresetStorage.addPreset(setting.getColor());
            }
            return true;
         }
      }

      return true;
   }

   public static boolean handleDrag(HueSetting setting, float x, float y, int mouseX, int mouseY) {
      if (GuiScreen.pickingSaturationBrightness) {
         updateSaturationBrightness(setting, x, y, mouseX, mouseY);
         return true;
      }

      if (GuiScreen.pickingHue) {
         updateHue(setting, x, y, mouseY);
         return true;
      }

      if (GuiScreen.pickingAlpha) {
         updateAlpha(setting, x, mouseX);
         return true;
      }

      return false;
   }

   public static void close() {
      GuiScreen.animation15.setDirection(Direction.BACKWARDS);
      GuiScreen.activeColorPicker = null;
      GuiScreen.colorPickerX = 0.0F;
      GuiScreen.colorPickerY = 0.0F;
      GuiScreen.pickingScreenColor = false;
      GuiScreen.pickingSaturationBrightness = false;
      GuiScreen.pickingHue = false;
      GuiScreen.pickingAlpha = false;
   }

   private static int presetSlots() {
      List<ColorPresetStorage.ColorPreset> presets = ColorPresetStorage.presets();
      int slots = presets.size();
      if (slots < ColorPresetStorage.MAX_PRESETS) {
         slots++;
      }
      return slots;
   }

   private static float[] presetSlotPos(int slot, float x, float y) {
      int col = slot % SLOTS_PER_ROW;
      int row = slot / SLOTS_PER_ROW;
      return new float[] { x + 8.0F + col * SWATCH_PITCH, y + PRESET_START_Y + row * SWATCH_PITCH };
   }

   private static void renderCloseButton(Renderer2D renderer, float x, float y, int outlineColor, int bgColor, int textColor) {
      float bx = x + WIDTH - 18.0F;
      float by = y + 4.0F;
      renderer.rectOutline(bx, by, BUTTON_SIZE, BUTTON_SIZE, 4.0F, outlineColor, 0.1F);
      renderer.rect(bx, by, BUTTON_SIZE, BUTTON_SIZE, 4.0F, bgColor);
      renderer.text(FontRegistry.INTER_MEDIUM, bx + 4.0F, by + 9.0F, 12.0F, "X", textColor);
   }

   private static void renderEyeDropperButton(
         Renderer2D renderer,
         HueSetting setting,
         float x,
         float y,
         int outlineColor,
         int bgColor,
         int textColor
   ) {
      float bx = x + WIDTH - 36.0F;
      float by = y + 4.0F;
      boolean active = GuiScreen.pickingScreenColor;
      int border = active ? ColorUtil.replAlpha(textColor, 150) : outlineColor;
      renderer.rectOutline(bx, by, BUTTON_SIZE, BUTTON_SIZE, 4.0F, border, active ? 0.25F : 0.1F);
      renderer.rect(bx, by, BUTTON_SIZE, BUTTON_SIZE, 4.0F, active ? ColorUtil.multAlpha(textColor, 0.18F) : bgColor);
      float cx = bx + BUTTON_SIZE / 2.0F;
      float cy = by + BUTTON_SIZE / 2.0F;
      renderer.circle(cx, cy, 4.0F, 0.0F, 1.0F, new Color(0, 0, 0, 120).getRGB());
      renderer.circle(cx, cy, 3.0F, 0.0F, 1.0F, setting.getColor().getRGB());
   }

   private static void renderPresets(Renderer2D renderer, float drawX, float y, int outlineColor, int bgColor, int textColor) {
      List<ColorPresetStorage.ColorPreset> presets = ColorPresetStorage.presets();
      int slots = presetSlots();
      for (int slot = 0; slot < slots; slot++) {
         float[] pos = presetSlotPos(slot, drawX, y);
         if (slot < presets.size()) {
            renderer.rect(pos[0], pos[1], SWATCH, SWATCH, 4.0F, presets.get(slot).color().getRGB());
         } else {
            renderer.rectOutline(pos[0], pos[1], SWATCH, SWATCH, 4.0F, outlineColor, 0.1F);
            renderer.rect(pos[0], pos[1], SWATCH, SWATCH, 4.0F, bgColor);
            renderer.text(FontRegistry.INTER_MEDIUM, pos[0] + 2.5F, pos[1] + 8.5F, 11.0F, "+", textColor);
         }
      }
   }

   private static void renderCheckerboard(Renderer2D renderer, float x, float y, float w, float h) {
      float s = 5.0F;
      boolean toggleRow = false;
      for (float cy = y; cy < y + h; cy += s) {
         boolean toggle = toggleRow;
         float rowHeight = Math.min(s, y + h - cy);
         for (float cx = x; cx < x + w; cx += s) {
            float colWidth = Math.min(s, x + w - cx);
            int color = toggle ? new Color(255, 255, 255, 24).getRGB() : new Color(255, 255, 255, 8).getRGB();
            renderer.rect(cx, cy, colWidth, rowHeight, color);
            toggle = !toggle;
         }
         toggleRow = !toggleRow;
      }
   }

   private static void updateSaturationBrightness(HueSetting setting, float x, float y, int mouseX, int mouseY) {
      float sx = Math.max(0.0F, Math.min(mouseX - (x + PALETTE_X), PALETTE_W));
      float sy = Math.max(0.0F, Math.min(mouseY - (y + PALETTE_Y), PALETTE_H));
      setting.saturation = sx / PALETTE_W;
      setting.brightness = 1.0F - sy / PALETTE_H;
      autoSave();
   }

   private static void updateHue(HueSetting setting, float x, float y, int mouseY) {
      float huePos = Math.max(0.0F, Math.min(mouseY - (y + HUE_Y), HUE_H));
      setting.current = huePos / HUE_H * setting.maximum;
      autoSave();
   }

   private static void updateAlpha(HueSetting setting, float x, int mouseX) {
      float alphaPos = Math.max(0.0F, Math.min(mouseX - (x + PALETTE_X), ALPHA_W));
      setting.alpha = alphaPos / ALPHA_W;
      autoSave();
   }

   private static void autoSave() {
      if (Zero.get != null && Zero.get.configManager != null) {
         Zero.get.configManager.autoSave();
      }
   }

   private static void renderPalette(Renderer2D renderer, float x, float y, float width, float height, float hue, int outlineColor) {
      Color baseColor = Color.getHSBColor(hue, 1.0F, 1.0F);
      renderer.rectOutline(x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F, 4.0F, outlineColor, 0.1F);
      renderer.horizontalGradient(x, y, width, height, 4.0F, Color.WHITE.getRGB(), baseColor.getRGB());
      renderer.verticalGradient(x, y, width, height, 4.0F, new Color(0, 0, 0, 0).getRGB(), new Color(0, 0, 0, 255).getRGB());
   }

   private static void renderHueBar(Renderer2D renderer, float x, float y, float width, float height, int outlineColor) {
      renderer.rectOutline(x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F, 4.0F, outlineColor, 0.1F);
      int segments = 6;
      float segmentHeight = height / segments;
      Color[] colors = new Color[] {
            Color.getHSBColor(0.0F, 1.0F, 1.0F),
            Color.getHSBColor(0.16666667F, 1.0F, 1.0F),
            Color.getHSBColor(0.33333334F, 1.0F, 1.0F),
            Color.getHSBColor(0.5F, 1.0F, 1.0F),
            Color.getHSBColor(0.6666667F, 1.0F, 1.0F),
            Color.getHSBColor(0.8333333F, 1.0F, 1.0F),
            Color.getHSBColor(1.0F, 1.0F, 1.0F)
      };

      for (int i = 0; i < segments; i++) {
         float segmentY = y + i * segmentHeight;
         float round = i == 0 ? 4.0F : 0.0F;
         renderer.verticalGradient(x + 1.0F, segmentY, width - 2.0F, segmentHeight, round, colors[i].getRGB(), colors[i + 1].getRGB());
      }
   }
}
