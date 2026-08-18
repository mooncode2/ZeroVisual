package ru.zero.ui.gui.color;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;

@Environment(EnvType.CLIENT)
public final class ColorPresetStorage {
   public static final int MAX_PRESETS = 10;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final List<ColorPreset> PRESETS = new ArrayList<>();

   static {
      PRESETS.add(new ColorPreset(new Color(0, 122, 255, 255)));
      PRESETS.add(new ColorPreset(new Color(52, 199, 89, 255)));
      PRESETS.add(new ColorPreset(new Color(255, 204, 0, 255)));
      PRESETS.add(new ColorPreset(new Color(255, 59, 48, 255)));
      PRESETS.add(new ColorPreset(new Color(151, 71, 255, 255)));
   }

   private ColorPresetStorage() {
   }

   public static List<ColorPreset> presets() {
      return Collections.unmodifiableList(PRESETS);
   }

   public static void addPreset(Color color) {
      if (PRESETS.size() >= MAX_PRESETS) {
         return;
      }

      PRESETS.add(new ColorPreset(color));
      save();
   }

   public static void removePreset(int index) {
      if (index < 0 || index >= PRESETS.size()) {
         return;
      }

      PRESETS.remove(index);
      save();
   }

   public static void load() {
      File file = getFile();
      if (!file.exists()) {
         return;
      }

      try (FileReader reader = new FileReader(file)) {
         JsonElement root = JsonParser.parseReader(reader);
         if (!root.isJsonArray()) {
            return;
         }

         PRESETS.clear();
         for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
               continue;
            }

            JsonObject object = element.getAsJsonObject();
            int r = object.has("r") ? object.get("r").getAsInt() : 255;
            int g = object.has("g") ? object.get("g").getAsInt() : 255;
            int b = object.has("b") ? object.get("b").getAsInt() : 255;
            int a = object.has("a") ? object.get("a").getAsInt() : 255;
            PRESETS.add(new ColorPreset(new Color(r, g, b, a)));
         }

         if (PRESETS.isEmpty()) {
            resetDefaults();
         }
      } catch (IOException ignored) {
      }
   }

   public static void save() {
      File file = getFile();
      file.getParentFile().mkdirs();
      JsonArray array = new JsonArray();

      for (ColorPreset preset : PRESETS) {
         JsonObject object = new JsonObject();
         object.addProperty("r", preset.color().getRed());
         object.addProperty("g", preset.color().getGreen());
         object.addProperty("b", preset.color().getBlue());
         object.addProperty("a", preset.color().getAlpha());
         array.add(object);
      }

      try (FileWriter writer = new FileWriter(file)) {
         GSON.toJson(array, writer);
      } catch (IOException ignored) {
      }
   }

   private static void resetDefaults() {
      PRESETS.clear();
      PRESETS.add(new ColorPreset(new Color(0, 122, 255, 255)));
      PRESETS.add(new ColorPreset(new Color(52, 199, 89, 255)));
      PRESETS.add(new ColorPreset(new Color(255, 204, 0, 255)));
      PRESETS.add(new ColorPreset(new Color(255, 59, 48, 255)));
      PRESETS.add(new ColorPreset(new Color(151, 71, 255, 255)));
   }

   private static File getFile() {
      File root = Zero.get != null && Zero.get.root != null
            ? Zero.get.root
            : new File(new File(System.getProperty("user.home"), ".zerodlc"), "ZeroDLC");
      return new File(root, "color_presets.json");
   }

   @Environment(EnvType.CLIENT)
   public record ColorPreset(Color color) {
   }
}
