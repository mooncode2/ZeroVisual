package ru.zero.module.api.setting.impl;

import java.awt.Color;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.setting.Setting;
import ru.zero.util.render.math.animation.Animation;
import ru.zero.util.render.math.animation.impl.EaseInOutQuad;

@Environment(EnvType.CLIENT)
public class HueSetting extends Setting {
   public float current;
   public float minimum;
   public float maximum;
   public float increment;
   public float sliderWidth;
   public boolean sliding;
   public String description;
   public Animation animation = new EaseInOutQuad(300, 1.0);
   public float saturation = 1.0F;
   public float brightness = 1.0F;
   public float alpha = 1.0F;
   public boolean alphaEnabled = true;

   public HueSetting(String name, float current) {
      this.name = name;
      this.minimum = 0.0F;
      this.current = current;
      this.maximum = 106.0F;
      this.increment = 1.0F;
      this.saturation = 1.0F;
      this.brightness = 1.0F;
   }

   public HueSetting(String name, float current, float saturation, float brightness) {
      this.name = name;
      this.minimum = 0.0F;
      this.current = current;
      this.maximum = 106.0F;
      this.increment = 1.0F;
      this.saturation = saturation;
      this.brightness = brightness;
   }

   public HueSetting hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }

   public HueSetting withAlpha(boolean enabled) {
      this.alphaEnabled = enabled;
      return this;
   }

   public Color getColor() {
      float hue = this.current / this.maximum;
      Color rgb = Color.getHSBColor(hue, this.saturation, this.brightness);
      if (!this.alphaEnabled) {
         return rgb;
      }

      int a = Math.round(this.alpha * 255.0F);
      return new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), a);
   }

   public void setColor(Color color) {
      float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
      this.current = hsb[0] * this.maximum;
      this.saturation = hsb[1];
      this.brightness = hsb[2];
      if (this.alphaEnabled) {
         this.alpha = color.getAlpha() / 255.0F;
      }
   }

   public float getHue() {
      return this.current / this.maximum;
   }

   public int getRGB() {
      return this.getColor().getRGB();
   }

   public int getRGBA(int overrideAlpha) {
      Color color = this.getColor();
      int a = this.alphaEnabled ? color.getAlpha() : overrideAlpha;
      return a << 24 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
   }

   public String toHex() {
      Color color = this.getColor();
      if (this.alphaEnabled) {
         return String.format("#%02X%02X%02X%02X", color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
      }

      return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
   }

   public void fromHex(String hex) {
      if (hex == null || hex.isBlank()) {
         return;
      }

      String value = hex.trim();
      if (value.startsWith("#")) {
         value = value.substring(1);
      }

      try {
         if (value.length() == 8) {
            int a = Integer.parseInt(value.substring(0, 2), 16);
            int r = Integer.parseInt(value.substring(2, 4), 16);
            int g = Integer.parseInt(value.substring(4, 6), 16);
            int b = Integer.parseInt(value.substring(6, 8), 16);
            this.setColor(new Color(r, g, b, a));
         } else if (value.length() == 6) {
            int r = Integer.parseInt(value.substring(0, 2), 16);
            int g = Integer.parseInt(value.substring(2, 4), 16);
            int b = Integer.parseInt(value.substring(4, 6), 16);
            this.setColor(new Color(r, g, b));
         }
      } catch (NumberFormatException ignored) {
      }
   }
}
