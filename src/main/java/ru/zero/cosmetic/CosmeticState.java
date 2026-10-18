package ru.zero.cosmetic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/** Stores which cosmetic is equipped in each slot. Persisted to configs/. */
public final class CosmeticState {
   private static final Map<CosmeticSlot, String> EQUIPPED = new EnumMap<>(CosmeticSlot.class);
   private static boolean enabled = true;
   private static boolean loaded;

   private CosmeticState() {
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("zero-cosmetics.json");
   }

   public static synchronized void load() {
      if (loaded) {
         return;
      }
      loaded = true;
      CosmeticRegistry.init();
      Path path = file();
      if (!Files.exists(path)) {
         return;
      }
      try {
         String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
         JsonObject root = JsonParser.parseString(text).getAsJsonObject();
         if (root.has("enabled")) {
            enabled = root.get("enabled").getAsBoolean();
         }
         if (root.has("equipped")) {
            JsonObject eq = root.getAsJsonObject("equipped");
            for (CosmeticSlot slot : CosmeticSlot.values()) {
               if (eq.has(slot.name())) {
                  EQUIPPED.put(slot, eq.get(slot.name()).getAsString());
               }
            }
         }
      } catch (Exception ignored) {
      }
   }

   public static synchronized void save() {
      JsonObject root = new JsonObject();
      root.addProperty("enabled", enabled);
      JsonObject eq = new JsonObject();
      for (Map.Entry<CosmeticSlot, String> e : EQUIPPED.entrySet()) {
         eq.addProperty(e.getKey().name(), e.getValue());
      }
      root.add("equipped", eq);
      try {
         Path path = file();
         Files.createDirectories(path.getParent());
         Files.write(path, root.toString().getBytes(StandardCharsets.UTF_8));
      } catch (IOException ignored) {
      }
   }

   public static boolean isEnabled() {
      load();
      return enabled;
   }

   public static void setEnabled(boolean value) {
      load();
      enabled = value;
      save();
   }

   public static Cosmetic equipped(CosmeticSlot slot) {
      load();
      return CosmeticRegistry.byKey(slot, EQUIPPED.get(slot));
   }

   public static String equippedKey(CosmeticSlot slot) {
      load();
      return EQUIPPED.get(slot);
   }

   public static boolean isEquipped(Cosmetic c) {
      if (c == null) {
         return false;
      }
      return c.key.equals(equippedKey(c.slot));
   }

   public static void toggleEquipped(Cosmetic c) {
      if (c == null) {
         return;
      }
      load();
      if (c.key.equals(EQUIPPED.get(c.slot))) {
         EQUIPPED.remove(c.slot);
      } else {
         EQUIPPED.put(c.slot, c.key);
      }
      save();
   }

   public static void equip(Cosmetic c) {
      if (c == null) {
         return;
      }
      load();
      EQUIPPED.put(c.slot, c.key);
      save();
   }

   public static void clear(CosmeticSlot slot) {
      load();
      EQUIPPED.remove(slot);
      save();
   }
}
