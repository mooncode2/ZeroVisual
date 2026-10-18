package ru.zero.cosmetic;

/** Cosmetics categories, mirroring EvoVis' CosmeticShowcase.Slot. */
public enum CosmeticSlot {
   CAPES("Capes"),
   HATS("Hats"),
   BACKPACKS("Backpacks"),
   WINGS("Wings"),
   NAMETAGS("Nametags"),
   MELEE("Melee"),
   GRAFFITI("Graffiti"),
   PLAYERSKINS("My Skins");

   private final String displayName;

   CosmeticSlot(String displayName) {
      this.displayName = displayName;
   }

   public String getDisplayName() {
      return this.displayName;
   }
}
