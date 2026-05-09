package ru.zero.util.other;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.util.color.ColorUtil;

@Environment(EnvType.CLIENT)
public final class ColorFormatting {
   public static Pattern PATTERN = Pattern.compile("\\$\\{(rgba|rgb)\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3})(?:,(\\d{1,3}))?\\)}|\\$\\{reset}", 2);

   public static String getColor(int red, int green, int blue) {
      return String.format("${rgb(%s,%s,%s)}", red, green, blue);
   }

   public static String getColor(int red, int green, int blue, int alpha) {
      return String.format("${rgba(%s,%s,%s,%s)}", red, green, blue, alpha);
   }

   public static String getColor(int color) {
      return String.format("${rgba(%s,%s,%s,%s)}", ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color));
   }

   public static int overColor(int firstColor, int secondColor, float factorPercent) {
      return ColorUtil.overCol(firstColor, secondColor, factorPercent);
   }

   public static String reset() {
      return "${reset}";
   }

   public static String removeFormatting(String text) {
      return PATTERN.matcher(text).replaceAll("");
   }

   public static String typeRGB() {
      return "rgb";
   }

   public static String typeRGBA() {
      return "rgba";
   }

   public static String replaceColor(String text, int red, int green, int blue) {
      return PATTERN.matcher(text).replaceAll(Matcher.quoteReplacement(getColor(red, green, blue)));
   }

   public static String replaceColor(String text, int red, int green, int blue, int alpha) {
      return PATTERN.matcher(text).replaceAll(Matcher.quoteReplacement(getColor(red, green, blue, alpha)));
   }

   @Generated
   private ColorFormatting() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
