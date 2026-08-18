package ru.zero.util.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;

@Environment(EnvType.CLIENT)
public final class Lang {
   public static final String RU = "RU";
   public static final String EN = "EN";
   public static final String PL = "PL";

   private static final Gson GSON = new Gson();
   private static volatile String current = RU;
   private static Map<String, String> ruMap = Collections.emptyMap();
   private static Map<String, String> enMap = Collections.emptyMap();
   private static Map<String, String> plMap = Collections.emptyMap();
   private static boolean loaded = false;

   private Lang() {
   }

   public static String current() {
      return current;
   }

   public static boolean isEn() {
      return EN.equals(current);
   }

   public static void ensureLoaded() {
      if (!loaded) {
         load();
      }
   }

   public static void load() {
      ruMap = loadResource("/assets/zero/lang/ru_ru.json");
      enMap = loadResource("/assets/zero/lang/en_us.json");
      plMap = loadResource("/assets/zero/lang/pl_pl.json");
      String persisted = readPersisted();
      if (persisted != null) {
         current = persisted;
      }
      loaded = true;
   }

   public static void setLanguage(String lang) {
      String normalized = normalize(lang);
      if (normalized == null) {
         return;
      }
      current = normalized;
      writePersisted(current);
   }

   public static String t(String text) {
      if (text == null || text.isEmpty()) {
         return text;
      }
      ensureLoaded();
      if (RU.equals(current)) {
         String mapped = ruMap.get(text);
         return mapped != null ? mapped : text;
      }
      if (PL.equals(current)) {
         String mapped = plMap.get(text);
         return mapped != null ? mapped : text;
      }
      String mapped = enMap.get(text);
      return mapped != null ? mapped : text;
   }

   public static String tf(String text, Object... args) {
      String base = t(text);
      if (args == null || args.length == 0) {
         return base;
      }
      try {
         return String.format(base, args);
      } catch (Throwable ignored) {
         return base;
      }
   }

   public static String normalize(String lang) {
      if (lang == null) {
         return null;
      }
      String upper = lang.trim().toUpperCase(Locale.ROOT);
      if (RU.equals(upper) || EN.equals(upper)) {
         return upper;
      }
      if (upper.startsWith("RU")) {
         return RU;
      } else if (upper.startsWith("EN")) {
         return EN;
      }
      return null;
   }

   private static Map<String, String> loadResource(String path) {
      try (InputStream in = Lang.class.getResourceAsStream(path)) {
         if (in == null) {
            return Collections.emptyMap();
         }
         try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, String> map = new HashMap<>();
            for (String key : obj.keySet()) {
               map.put(key, obj.get(key).getAsString());
            }
            return map;
         }
      } catch (Throwable ignored) {
      }
      return Collections.emptyMap();
   }

   private static File configFile() {
      try {
         File dir = new File(Zero.get.root, "configs");
         if (!dir.exists()) {
            dir.mkdirs();
         }
         return new File(dir, "lang.cfg");
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static String readPersisted() {
      File f = configFile();
      if (f == null || !f.exists()) {
         return null;
      }
      try (InputStreamReader reader = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
         JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
         String lang = obj.has("language") ? obj.get("language").getAsString() : null;
         return normalize(lang);
      } catch (Throwable ignored) {
      }
      return null;
   }

   private static void writePersisted(String lang) {
      File f = configFile();
      if (f == null) {
         return;
      }
      try (FileWriter writer = new FileWriter(f, StandardCharsets.UTF_8)) {
         JsonObject obj = new JsonObject();
         obj.addProperty("language", lang);
         GSON.toJson(obj, writer);
      } catch (IOException ignored) {
      }
   }
}
