package ru.zero.cosmetic.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bedrock animation ({@code *.anim.json}), sampled with linear interpolation.
 */
public final class ModelAnimation {
   public final float length;
   public final boolean loop;
   private final Map<String, BoneAnimation> bones = new HashMap<>();

   /** Sound events fired by the clip, sorted by time (EvoVis {@code sound_effects}). */
   public float[] soundTimes = new float[0];
   public String[] soundNames = new String[0];

   private ModelAnimation(float length, boolean loop) {
      this.length = length;
      this.loop = loop;
   }

   public int soundCount() {
      return this.soundTimes.length;
   }

   public float soundTime(int index) {
      return this.soundTimes[index];
   }

   public String soundName(int index) {
      return this.soundNames[index];
   }

   public boolean hasBone(String name) {
      return this.bones.containsKey(name);
   }

   public java.util.Set<String> bones() {
      return this.bones.keySet();
   }

   public float time(float seconds) {
      if (this.length > 0.0F && this.loop) {
         float t = seconds % this.length;
         return t < 0.0F ? t + this.length : t;
      }

      return seconds;
   }

   public void applyRotation(String bone, float t, float[] out) {
      BoneAnimation animation = this.bones.get(bone);
      if (animation == null) {
         return;
      }

      sample(animation.rotation, t, out, 0.0F);
   }

   public void applyPosition(String bone, float t, float[] out) {
      BoneAnimation animation = this.bones.get(bone);
      if (animation == null) {
         return;
      }

      sample(animation.position, t, out, 0.0F);
   }

   public void applyScale(String bone, float t, float[] out) {
      BoneAnimation animation = this.bones.get(bone);
      if (animation == null) {
         return;
      }

      sample(animation.scale, t, out, 1.0F);
   }

   private static void sample(TreeMap<Float, float[]> track, float t, float[] out, float fallback) {
      if (track == null || track.isEmpty()) {
         return;
      }

      Map.Entry<Float, float[]> floor = track.floorEntry(t);
      Map.Entry<Float, float[]> ceil = track.ceilingEntry(t);
      if (floor == null) {
         copy(ceil.getValue(), out, fallback);
         return;
      }

      if (ceil == null || floor.getKey().equals(ceil.getKey())) {
         copy(floor.getValue(), out, fallback);
         return;
      }

      float span = ceil.getKey() - floor.getKey();
      float alpha = span <= 0.0F ? 0.0F : (t - floor.getKey()) / span;
      float[] a = floor.getValue();
      float[] b = ceil.getValue();
      for (int i = 0; i < 3; i++) {
         out[i] = a[i] + (b[i] - a[i]) * alpha;
      }
   }

   private static void copy(float[] source, float[] out, float fallback) {
      for (int i = 0; i < 3; i++) {
         out[i] = source != null && i < source.length ? source[i] : fallback;
      }
   }

   public static ModelAnimation parse(InputStream in, String animationName) {
      JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
      // Skin format: { "clips": { name: {...} } }, otherwise an "animations" map, otherwise the root itself.
      JsonObject animations;
      if (root.has("clips") && root.get("clips").isJsonObject()) {
         animations = root.getAsJsonObject("clips");
      } else if (root.has("animations") && root.get("animations").isJsonObject()) {
         animations = root.getAsJsonObject("animations");
      } else {
         animations = root;
      }

      JsonObject animation = findAnimation(animations, animationName);
      if (animation == null) {
         return null;
      }

      boolean hasDeclaredLength = animation.has("animation_length") || animation.has("length");
      float declaredLength = animation.has("animation_length")
            ? animation.get("animation_length").getAsFloat()
            : (animation.has("length") ? animation.get("length").getAsFloat() : 0.0F);

      boolean loop = true;
      if (animation.has("loop")) {
         JsonElement loopElement = animation.get("loop");
         if (loopElement.isJsonPrimitive() && loopElement.getAsJsonPrimitive().isBoolean()) {
            loop = loopElement.getAsBoolean();
         } else if (loopElement.isJsonPrimitive()) {
            loop = !"once".equalsIgnoreCase(loopElement.getAsString());
         }
      }

      Map<String, BoneAnimation> parsedBones = new HashMap<>();
      float longest = 0.0F;
      JsonObject bones = animation.getAsJsonObject("bones");
      if (bones != null) {
         for (Map.Entry<String, JsonElement> entry : bones.entrySet()) {
            BoneAnimation bone = new BoneAnimation();
            JsonObject boneObject = entry.getValue().getAsJsonObject();
            bone.rotation = readTrack(boneObject.get("rotation"), 0.0F);
            bone.position = readTrack(boneObject.get("position"), 0.0F);
            bone.scale = readTrack(boneObject.get("scale"), 1.0F);
            parsedBones.put(entry.getKey(), bone);
            longest = Math.max(longest,
                  Math.max(lastTime(bone.rotation), Math.max(lastTime(bone.position), lastTime(bone.scale))));
         }
      }

      // EvoVis uses the declared length when present, otherwise the last keyframe time.
      float length = hasDeclaredLength ? declaredLength : longest;
      ModelAnimation result = new ModelAnimation(Math.max(length, 0.0F), loop);
      result.bones.putAll(parsedBones);
      readSounds(animation, result);
      return result;
   }

   private static float lastTime(TreeMap<Float, float[]> track) {
      return track == null || track.isEmpty() ? 0.0F : track.lastKey();
   }

   /** Parses {@code sound_effects} into time-sorted arrays so the player can fire them while playing. */
   private static void readSounds(JsonObject animation, ModelAnimation result) {
      JsonElement sounds = animation.get("sound_effects");
      if (sounds != null && sounds.isJsonNull()) {
         sounds = null;
      }
      if (sounds == null) {
         return;
      }

      TreeMap<Float, String> collected = new TreeMap<>();
      if (sounds.isJsonObject()) {
         for (Map.Entry<String, JsonElement> entry : sounds.getAsJsonObject().entrySet()) {
            float time;
            try {
               time = Float.parseFloat(entry.getKey().trim());
            } catch (NumberFormatException ignored) {
               continue;
            }

            String name = effectName(entry.getValue());
            if (name != null) {
               collected.put(time, name);
            }
         }
      } else if (sounds.isJsonArray()) {
         for (JsonElement element : sounds.getAsJsonArray()) {
            if (!element.isJsonObject()) {
               continue;
            }

            JsonObject object = element.getAsJsonObject();
            float time = object.has("time") ? object.get("time").getAsFloat() : 0.0F;
            String name = effectName(object);
            if (name != null) {
               collected.put(time, name);
            }
         }
      }

      if (collected.isEmpty()) {
         return;
      }

      result.soundTimes = new float[collected.size()];
      result.soundNames = new String[collected.size()];
      int index = 0;
      for (Map.Entry<Float, String> entry : collected.entrySet()) {
         result.soundTimes[index] = entry.getKey();
         result.soundNames[index] = entry.getValue();
         index++;
      }
   }

   private static String effectName(JsonElement element) {
      if (element == null) {
         return null;
      }

      if (element.isJsonPrimitive()) {
         return element.getAsString();
      }

      if (element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         if (object.has("effect") && object.get("effect").isJsonPrimitive()) {
            return object.get("effect").getAsString();
         }

         if (object.has("sound") && object.get("sound").isJsonPrimitive()) {
            return object.get("sound").getAsString();
         }
      }

      return null;
   }

   private static JsonObject findAnimation(JsonObject animations, String name) {
      if (animations == null) {
         return null;
      }

      if (name != null) {
         if (animations.has(name) && animations.get(name).isJsonObject()) {
            return animations.getAsJsonObject(name);
         }

         for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            if (entry.getKey().endsWith("." + name) && entry.getValue().isJsonObject()) {
               return entry.getValue().getAsJsonObject();
            }
         }

         // A specific clip was requested: never fall back to an unrelated clip.
         return null;
      }

      for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
         JsonElement value = entry.getValue();
         if (value.isJsonObject() && value.getAsJsonObject().has("bones")) {
            return value.getAsJsonObject();
         }
      }

      for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
         if (entry.getValue().isJsonObject()) {
            return entry.getValue().getAsJsonObject();
         }
      }

      return null;
   }

   /**
    * Parses a single channel (rotation/position/scale). Mirrors EvoVis {@code ahi.parseChannel}:
    * a constant value (scalar or {@code [x,y,z]} vector) becomes a single keyframe at time 0,
    * an array of arrays {@code [ [time, x, y, z], ... ]} is read as frames, and an object is read
    * as time-keyed frames.
    *
    * @param fallback value used for missing components ({@code 0} for rotation/position, {@code 1} for scale)
    */
   private static TreeMap<Float, float[]> readTrack(JsonElement element, float fallback) {
      if (element == null || element.isJsonNull()) {
         return null;
      }

      TreeMap<Float, float[]> track = new TreeMap<>();
      if (element.isJsonPrimitive()) {
         float value = element.getAsFloat();
         track.put(0.0F, new float[] { value, value, value });
      } else if (element.isJsonArray()) {
         JsonArray array = element.getAsJsonArray();
         boolean frames = !array.isEmpty() && array.get(0).isJsonArray();
         if (frames) {
            // Explicit frame list: [ [time, x, y, z], ... ]
            for (JsonElement frameElement : array) {
               if (!frameElement.isJsonArray()) {
                  continue;
               }

               JsonArray frame = frameElement.getAsJsonArray();
               if (frame.size() < 4) {
                  continue;
               }

               float time = frame.get(0).getAsFloat();
               float[] value = { frame.get(1).getAsFloat(), frame.get(2).getAsFloat(), frame.get(3).getAsFloat() };
               track.put(time, value);
            }
         } else {
            // Constant vector, e.g. "rotation": [x, y, z]
            track.put(0.0F, readArray(array, fallback));
         }
      } else if (element.isJsonObject()) {
         for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            float time;
            try {
               time = Float.parseFloat(entry.getKey());
            } catch (NumberFormatException ignored) {
               continue;
            }

            float[] value = readKeyframe(entry.getValue(), fallback);
            if (value != null) {
               track.put(time, value);
            }
         }
      }

      return track.isEmpty() ? null : track;
   }

   private static float[] readKeyframe(JsonElement element, float fallback) {
      if (element == null || element.isJsonNull()) {
         return null;
      }

      if (element.isJsonPrimitive()) {
         float value = element.getAsFloat();
         return new float[] { value, value, value };
      }

      if (element.isJsonArray()) {
         return readArray(element.getAsJsonArray(), fallback);
      }

      if (element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         if (object.has("post")) {
            return readKeyframe(object.get("post"), fallback);
         }

         if (object.has("pre")) {
            return readKeyframe(object.get("pre"), fallback);
         }
      }

      return null;
   }

   private static float[] readArray(JsonArray array, float fallback) {
      float[] value = { fallback, fallback, fallback };
      if (array == null) {
         return value;
      }

      for (int i = 0; i < 3 && i < array.size(); i++) {
         JsonElement component = array.get(i);
         if (component.isJsonPrimitive() && component.getAsJsonPrimitive().isNumber()) {
            value[i] = component.getAsFloat();
         }
      }

      return value;
   }

   private static final class BoneAnimation {
      TreeMap<Float, float[]> rotation;
      TreeMap<Float, float[]> position;
      TreeMap<Float, float[]> scale;
   }
}
