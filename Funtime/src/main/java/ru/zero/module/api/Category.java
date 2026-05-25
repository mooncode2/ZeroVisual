package ru.zero.module.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.util.render.animation.util.Animation;

@Environment(EnvType.CLIENT)
public enum Category {
   Combat("Combat", "a"),
   Movement("Movement", "c"),
   Visuals("Visuals", "e"),
   Player("Player", "g"),
   Utils("Utils", "j"),
   Prime("Prime", "k"),
   Misc("Misc", "h");

   private final String name;
   private final String icon;
   public Animation anim33 = new Animation();
   public Animation anim44 = new Animation();

   private Category(String name, String icon) {
      this.name = name;
      this.icon = icon;
   }

   public String getIcon() {
      return this.icon;
   }

   public String getName() {
      return this.name;
   }
}
