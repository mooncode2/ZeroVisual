package ru.zero.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the full list of available cosmetics, ported from EvoVis' registries. */
public final class CosmeticRegistry {
   private static final String GEO = "assets/zero/cosmetic/";
   private static final String TEX = "assets/zero/textures/cosmetic/";

   private static final List<Cosmetic> ALL = new ArrayList<>();
   private static final Map<CosmeticSlot, List<Cosmetic>> BY_SLOT = new EnumMap<>(CosmeticSlot.class);
   private static boolean initialized;

   private CosmeticRegistry() {
   }

   public static synchronized void init() {
      if (initialized) {
         return;
      }
      initialized = true;
      for (CosmeticSlot slot : CosmeticSlot.values()) {
         BY_SLOT.put(slot, new ArrayList<>());
      }

      buildWings();
      buildBackpacks();
      buildSkins();
      buildMelee();
      buildNametags();
      buildGraffiti();
      buildCapes();
   }

   public static List<Cosmetic> all() {
      init();
      return Collections.unmodifiableList(ALL);
   }

   public static List<Cosmetic> bySlot(CosmeticSlot slot) {
      init();
      return Collections.unmodifiableList(BY_SLOT.get(slot));
   }

   public static Cosmetic byKey(CosmeticSlot slot, String key) {
      if (key == null) {
         return null;
      }
      for (Cosmetic c : bySlot(slot)) {
         if (c.key.equals(key)) {
            return c;
         }
      }
      return null;
   }

   public static String geometryPath(String relative) {
      return GEO + relative;
   }

   public static String texturePath(String relative) {
      return TEX + relative;
   }

   static InputStream openResource(String path) {
      return CosmeticRegistry.class.getClassLoader().getResourceAsStream(path);
   }

   private static Cosmetic add(Cosmetic c) {
      ALL.add(c);
      BY_SLOT.get(c.slot).add(c);
      return c;
   }

   private static Cosmetic model(String slot, String key, String name, String geometry, String texture, String animation) {
      CosmeticSlot s = CosmeticSlot.valueOf(slot);
      Cosmetic c = new Cosmetic(key, name, s, Cosmetic.Kind.MODEL);
      c.geometry = GEO + geometry + ".geo.json";
      c.texture = TEX + texture + ".png";
      if (animation != null) {
         c.animation = GEO + animation + (animation.endsWith(".json") ? "" : ".animation.json");
      }
      return add(c);
   }

   private static void buildWings() {
      // key, display name, geometry, scale, offsetY, offsetZ
      Object[][] wings = {
         {"baby_dragon_white", "Baby Dragon (White)", "dragon", 1.0F, -1.2F, -0.14F},
         {"angel_white", "Angel (White)", "angel", 1.0F, -0.65F, -0.14F},
         {"demon_red", "Demon (Red)", "demon", 1.0F, -1.05F, -0.14F},
         {"demon_black", "Demon (Black)", "demon", 1.0F, -1.05F, -0.14F},
         {"pulse", "Pulse", "pulse", 0.85F, -0.7F, -0.15F},
         {"dark", "Dark", "dark", 1.0F, -1.0F, -0.14F},
         {"ghoul", "Ghoul", "ghoul", 1.0F, -1.1F, -0.14F},
         {"rocker", "Rocker", "rocker", 1.0F, -0.95F, -0.14F},
         {"trident", "Trident", "trident", 1.0F, -0.85F, -0.16F},
         {"easter", "Easter", "easter", 0.9F, -0.8F, -0.15F},
         {"blue_dragon", "Blue Dragon", "blue_dragon", 1.0F, -1.05F, -0.15F},
         {"spider", "Spider", "spider", 0.95F, -0.9F, -0.15F},
         {"vulcano", "Vulcano", "vulcano", 1.0F, -0.9F, -0.14F},
         {"wota", "Wota", "wota", 1.0F, -0.9F, -0.14F}
      };
      for (Object[] w : wings) {
         String key = (String) w[0];
         String geometry = "wings/" + (String) w[2];
         String anim = resourceExists(GEO + geometry + ".animation.json") ? geometry : null;
         Cosmetic c = model("WINGS", key, (String) w[1], geometry, "wings/" + key, anim);
         c.scale = (Float) w[3];
         c.offsetY = (Float) w[4];
         c.offsetZ = (Float) w[5];
      }
   }

   private static void buildBackpacks() {
      String[] keys = {"adidas", "gucci", "hermes", "labubu", "lui_vuitton", "mage_staff", "nike", "snoopy"};
      String[] names = {"Adidas", "Gucci", "Hermes", "Labubu", "Louis Vuitton", "Mage Staff", "Nike", "Snoopy"};
      for (int i = 0; i < keys.length; i++) {
         String geometry = "backpacks/" + keys[i];
         String anim = resourceExists(GEO + geometry + ".animation.json") ? geometry : null;
         Cosmetic c = model("BACKPACKS", keys[i], names[i], geometry, geometry, anim);
         c.offsetY = -0.15F;
         if (keys[i].equals("mage_staff")) {
            c.rotationY = 180.0F;
         }
      }
   }

   private static void buildSkins() {
      Object[][] skins = {
         {"akita_neru", "Akita Neru"}, {"aria", "Aria"}, {"baron", "Baron"},
         {"beardie", "Beardie"}, {"cotton", "Cotton"}, {"elise", "Elise"},
         {"goldenglow", "Goldenglow"}, {"goose", "Goose"}, {"kaltsit", "Kal'tsit"},
         {"mikie", "Mikie"}, {"miku", "Miku"}, {"mothi", "Mothi"},
         {"mothli", "Mothli"}, {"repo", "Repo"}, {"turbo_granny", "Turbo Granny"},
         {"wheelchair", "Wheelchair"}, {"android", "Android"}, {"peter", "Peter"}
      };
      for (Object[] s : skins) {
         String key = (String) s[0];
         String anim = resourceExists(GEO + "skins/" + key + ".anim.json") ? "skins/" + key + ".anim.json" : null;
         model("PLAYERSKINS", key, (String) s[1], "skins/" + key, "skins/" + key, anim);
      }
   }

   private static void buildMelee() {
      InputStream in = openResource(GEO + "melee/index.json");
      if (in == null) {
         return;
      }
      try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
         JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
         JsonArray melee = root.getAsJsonArray("melee");
         if (melee == null) {
            return;
         }
         for (JsonElement el : melee) {
            JsonObject o = el.getAsJsonObject();
            String key = o.get("key").getAsString();
            String name = o.has("name") ? o.get("name").getAsString() : key;
            // Recolours share a base model: "model" points at the entry holding the geo/animation.
            String modelKey = o.has("model") ? o.get("model").getAsString() : key;
            Cosmetic c = new Cosmetic(key, name, CosmeticSlot.MELEE, Cosmetic.Kind.MELEE);
            c.geometry = GEO + "melee/" + modelKey + ".geo.json";
            c.texture = TEX + "melee/" + key + ".png";
            c.animation = GEO + "melee/" + modelKey + ".anim.json";
            if (resourceExists(TEX + "melee/slot/" + key + ".png")) {
               c.icon = TEX + "melee/slot/" + key + ".png";
            }
            if (o.has("transforms") && o.get("transforms").isJsonObject()) {
               JsonObject transforms = o.getAsJsonObject("transforms");
               c.thirdPersonRightRotation = readTransformVector(transforms, "thirdperson_righthand", "rotation", 0.0F);
               c.thirdPersonRightTranslation = readTransformVector(transforms, "thirdperson_righthand", "translation", 0.0F);
               c.thirdPersonRightScale = readTransformVector(transforms, "thirdperson_righthand", "scale", 1.0F);
               c.thirdPersonLeftRotation = readTransformVector(transforms, "thirdperson_lefthand", "rotation", 0.0F);
               c.thirdPersonLeftTranslation = readTransformVector(transforms, "thirdperson_lefthand", "translation", 0.0F);
               c.thirdPersonLeftScale = readTransformVector(transforms, "thirdperson_lefthand", "scale", 1.0F);
             }
             readVariants(o, c);
             add(c);
         }
      } catch (Exception ignored) {
      }
   }

   private static void readVariants(JsonObject entry, Cosmetic cosmetic) {
      if (!entry.has("variants") || !entry.get("variants").isJsonArray()) {
         return;
      }

      for (JsonElement element : entry.getAsJsonArray("variants")) {
         if (!element.isJsonObject()) {
            continue;
         }

         JsonObject o = element.getAsJsonObject();
         String suffix = o.has("suffix") ? o.get("suffix").getAsString() : "";
         java.util.List<String> hidden = new java.util.ArrayList<>();
         if (o.has("hidden") && o.get("hidden").isJsonArray()) {
            for (JsonElement bone : o.getAsJsonArray("hidden")) {
               if (bone.isJsonPrimitive()) {
                  hidden.add(bone.getAsString());
               }
            }
         }

         cosmetic.variants.add(new Cosmetic.Variant(suffix, hidden.toArray(new String[0])));
      }
   }

   private static float[] readTransformVector(JsonObject transforms, String transform, String field, float fallback) {
      float[] value = { fallback, fallback, fallback };
      if (transforms == null || !transforms.has(transform) || !transforms.get(transform).isJsonObject()) {
         return value;
      }

      JsonObject group = transforms.getAsJsonObject(transform);
      if (!group.has(field) || !group.get(field).isJsonArray()) {
         return value;
      }

      JsonArray array = group.getAsJsonArray(field);
      for (int i = 0; i < 3 && i < array.size(); i++) {
         value[i] = array.get(i).getAsFloat();
      }

      return value;
   }

   private static void buildNametags() {
      nametag("rainbow", "Rainbow", 0.8F, 1.0F, true, null);
      nametag("sunset", "Sunset", 0.5F, 0.55F, false, new int[]{0xFFD3A6, 0xFF7A45, 0xFF3C3C});
      nametag("ocean", "Ocean", 0.5F, 0.6F, false, new int[]{0x6EE9FF, 0x2F8FCF, 0x2A3F80});
      nametag("toxic", "Toxic", 0.5F, 0.65F, false, new int[]{0xE8066B, 0x4CE84A, 0x0FA32B});
      nametag("ember", "Ember", 0.5F, 0.75F, false, new int[]{0xFFD8A6, 0xFF6E5A, 0xE01E1F});
      nametag("nebula", "Nebula", 0.5F, 0.5F, false, new int[]{0x7A5BBF, 0xB454FF, 0xFF5A50});
      nametag("frost", "Frost", 0.5F, 0.42F, false, new int[]{0xFFFFFF, 0xA8EAFF, 0x5AABBF});
      nametag("sakura", "Sakura", 0.5F, 0.5F, false, new int[]{0xFFC7AC, 0xFF8FA5, 0xE04A6B});
      nametag("midnight", "Midnight", 0.5F, 0.4F, false, new int[]{0x9A9AFF, 0x5A5A93, 0x2A1A4B});
      nametag("gold", "Gold", 0.5F, 0.9F, false, new int[]{0xFFE455, 0xFFC44D, 0xFFEEDA, 0xE0A83F});
   }

   private static void nametag(String key, String name, float span, float flow, boolean hueCycle, int[] stops) {
      Cosmetic c = new Cosmetic(key, name, CosmeticSlot.NAMETAGS, Cosmetic.Kind.NAMETAG);
      c.span = span;
      c.flow = flow;
      c.hueCycle = hueCycle;
      c.stops = stops;
      add(c);
   }

   private static void buildGraffiti() {
      flat("GRAFFITI", "evo", "Evo", "graffiti/evo");
      flat("GRAFFITI", "noai", "No Thanks I Use AI", "graffiti/noai");
   }

   private static void buildCapes() {
      Cosmetic c = new Cosmetic("dancing_cat", "Dancing Cat", CosmeticSlot.CAPES, Cosmetic.Kind.CAPE);
      c.texture = TEX + "capes/dancing_cat.png";
      add(c);
   }

   private static void flat(String slot, String key, String name, String texRelative) {
      Cosmetic c = new Cosmetic(key, name, CosmeticSlot.valueOf(slot), Cosmetic.Kind.FLAT);
      c.texture = TEX + texRelative + ".png";
      add(c);
   }

   private static boolean resourceExists(String path) {
      return CosmeticRegistry.class.getClassLoader().getResource(path) != null;
   }

   /** Ordered list of slots shown as tabs. */
   public static List<CosmeticSlot> slots() {
      List<CosmeticSlot> list = new ArrayList<>();
      for (CosmeticSlot slot : CosmeticSlot.values()) {
         list.add(slot);
      }
      return list;
   }

   public static Map<String, Cosmetic> index(CosmeticSlot slot) {
      Map<String, Cosmetic> map = new LinkedHashMap<>();
      for (Cosmetic c : bySlot(slot)) {
         map.put(c.key, c);
      }
      return map;
   }
}
