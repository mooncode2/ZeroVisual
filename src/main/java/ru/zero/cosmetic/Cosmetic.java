package ru.zero.cosmetic;

/** A single cosmetic entry (model, flat icon, nametag preset or online skin). */
public final class Cosmetic {
   public enum Kind {
      MODEL,
      MELEE,
      FLAT,
      NAMETAG,
      CAPE,
      PLAYERSKIN
   }

   public final String key;
   public final String name;
   public final CosmeticSlot slot;
   public final Kind kind;

   /** Classpath resource paths (no leading slash). */
   public String geometry;
   public String texture;
   public String icon;
   public String animation;

   public float scale = 1.0F;
   public float offsetX;
   public float offsetY;
   public float offsetZ;
   public float rotationX;
   public float rotationY;
   public float rotationZ;

   /** Melee model variants: an optional clip-name suffix plus bones hidden for that variant. */
   public static final class Variant {
      public final String suffix;
      public final String[] hidden;

      public Variant(String suffix, String[] hidden) {
         this.suffix = suffix;
         this.hidden = hidden;
      }
   }

   public java.util.List<Variant> variants = new java.util.ArrayList<>();

   /** Melee display transforms (degrees / pixels / scale), ported from the skin's index.json. */
   public float[] thirdPersonRightRotation = { 0.0F, 0.0F, 0.0F };
   public float[] thirdPersonRightTranslation = { 0.0F, 0.0F, 0.0F };
   public float[] thirdPersonRightScale = { 1.0F, 1.0F, 1.0F };
   public float[] thirdPersonLeftRotation = { 0.0F, 0.0F, 0.0F };
   public float[] thirdPersonLeftTranslation = { 0.0F, 0.0F, 0.0F };
   public float[] thirdPersonLeftScale = { 1.0F, 1.0F, 1.0F };

   /** Nametag styling. */
   public int[] stops;
   public float span = 0.5F;
   public float flow;
   public boolean hueCycle;

   public Cosmetic(String key, String name, CosmeticSlot slot, Kind kind) {
      this.key = key;
      this.name = name;
      this.slot = slot;
      this.kind = kind;
   }
}
