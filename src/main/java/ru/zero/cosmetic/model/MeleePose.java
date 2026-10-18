package ru.zero.cosmetic.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A name-keyed pose (rotation degrees, position in 1/16 block units, scale) used by the melee
 * animation compositor. Absence of a bone means the neutral value (0 for rotation/position,
 * 1 for scale), mirroring EvoVis' {@code fillNeutral} float-array layout.
 */
public final class MeleePose {
   public final Map<String, float[]> rotation = new HashMap<>();
   public final Map<String, float[]> position = new HashMap<>();
   public final Map<String, float[]> scale = new HashMap<>();

   public void clear() {
      this.rotation.clear();
      this.position.clear();
      this.scale.clear();
   }

   /** Samples every bone of {@code animation} into this pose, overwriting channels it defines. */
   public void applyAnimation(ModelAnimation animation, float time) {
      if (animation == null) {
         return;
      }

      for (String bone : animation.bones()) {
         float[] r = this.rotation.computeIfAbsent(bone, key -> new float[3]);
         float[] p = this.position.computeIfAbsent(bone, key -> new float[3]);
         float[] s = this.scale.computeIfAbsent(bone, key -> new float[] { 1.0F, 1.0F, 1.0F });
         animation.applyRotation(bone, time, r);
         animation.applyPosition(bone, time, p);
         animation.applyScale(bone, time, s);
      }
   }

   public MeleePose copy() {
      MeleePose copy = new MeleePose();
      copyInto(this.rotation, copy.rotation, 0.0F);
      copyInto(this.position, copy.position, 0.0F);
      copyInto(this.scale, copy.scale, 1.0F);
      return copy;
   }

   private static void copyInto(Map<String, float[]> source, Map<String, float[]> target, float neutral) {
      for (Map.Entry<String, float[]> entry : source.entrySet()) {
         float[] value = entry.getValue();
         target.put(entry.getKey(), new float[] { value[0], value[1], value[2] });
      }
   }

   /**
    * Blends from {@code fade} toward this pose element-wise: {@code this = fade + (this - fade) * alpha}.
    * Bones only present in {@code fade} decay toward neutral.
    */
   public void blend(MeleePose fade, float alpha) {
      blendChannel(this.rotation, fade.rotation, alpha, 0.0F);
      blendChannel(this.position, fade.position, alpha, 0.0F);
      blendChannel(this.scale, fade.scale, alpha, 1.0F);
   }

   private static void blendChannel(Map<String, float[]> current, Map<String, float[]> fade, float alpha, float neutral) {
      Set<String> keys = new HashSet<>(current.keySet());
      keys.addAll(fade.keySet());
      for (String bone : keys) {
         float[] c = current.get(bone);
         float[] f = fade.get(bone);
         float[] out = new float[3];
         for (int i = 0; i < 3; i++) {
            float from = f != null ? f[i] : neutral;
            float to = c != null ? c[i] : neutral;
            out[i] = from + (to - from) * alpha;
         }

         current.put(bone, out);
      }
   }

   /** Rotation/position are added, scale multiplied (EvoVis {@code compose}). */
   public static MeleePose combine(MeleePose base, MeleePose additive) {
      MeleePose out = base.copy();
      addChannel(out.rotation, additive.rotation);
      addChannel(out.position, additive.position);
      multiplyChannel(out.scale, additive.scale);
      return out;
   }

   private static void addChannel(Map<String, float[]> target, Map<String, float[]> source) {
      for (Map.Entry<String, float[]> entry : source.entrySet()) {
         float[] value = entry.getValue();
         float[] current = target.get(entry.getKey());
         if (current == null) {
            current = new float[3];
            target.put(entry.getKey(), current);
         }

         current[0] += value[0];
         current[1] += value[1];
         current[2] += value[2];
      }
   }

   private static void multiplyChannel(Map<String, float[]> target, Map<String, float[]> source) {
      for (Map.Entry<String, float[]> entry : source.entrySet()) {
         float[] value = entry.getValue();
         float[] current = target.get(entry.getKey());
         if (current == null) {
            current = new float[] { 1.0F, 1.0F, 1.0F };
            target.put(entry.getKey(), current);
         }

         current[0] *= value[0];
         current[1] *= value[1];
         current[2] *= value[2];
      }
   }

   public float[] rotationOf(String bone) {
      return this.rotation.get(bone);
   }

   public float[] positionOf(String bone) {
      return this.position.get(bone);
   }
}
