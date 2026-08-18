package ru.zero.util.render.text;

import net.minecraft.util.Formatting;
import ru.zero.util.render.core.Renderer2D;

public final class FormattedText {
   private FormattedText() {
   }

   public static float draw(Renderer2D r2, FontObject font, float x, float y, float size, String text, int defaultColor) {
      if (text == null || text.isEmpty()) {
         return 0.0F;
      }
      float penX = x;
      int color = defaultColor;
      int i = 0;
      int segStart = 0;
      int len = text.length();

      while (i < len) {
         char ch = text.charAt(i);
         if (ch == '\u00a7' && i + 1 < len) {
            if (i > segStart) {
               String seg = text.substring(segStart, i);
               r2.text(font, penX, y, size, seg, color);
               penX += r2.measureText(font, seg, size).width;
            }
            char code = Character.toLowerCase(text.charAt(i + 1));
            Integer mapped = colorForCode(code);
            if (mapped != null) {
               color = mapped;
            } else if (code == 'r') {
               color = defaultColor;
            }
            i += 2;
            segStart = i;
         } else {
            i++;
         }
      }

      if (segStart < len) {
         String seg = text.substring(segStart);
         r2.text(font, penX, y, size, seg, color);
         penX += r2.measureText(font, seg, size).width;
      }

      return penX - x;
   }

   public static float measure(Renderer2D r2, FontObject font, String text, float size) {
      if (text == null || text.isEmpty()) {
         return 0.0F;
      }
      float width = 0.0F;
      int i = 0;
      int segStart = 0;
      int len = text.length();

      while (i < len) {
         char ch = text.charAt(i);
         if (ch == '\u00a7' && i + 1 < len) {
            if (i > segStart) {
               width += r2.measureText(font, text.substring(segStart, i), size).width;
            }
            i += 2;
            segStart = i;
         } else {
            i++;
         }
      }

      if (segStart < len) {
         width += r2.measureText(font, text.substring(segStart), size).width;
      }

      return width;
   }

   private static Integer colorForCode(char code) {
      Formatting fmt = Formatting.byCode(code);
      if (fmt == null || !fmt.isColor()) {
         return null;
      }
      Integer rgbBox = fmt.getColorValue();
      if (rgbBox == null) {
         return null;
      }
      return (0xFF << 24) | rgbBox;
   }
}
