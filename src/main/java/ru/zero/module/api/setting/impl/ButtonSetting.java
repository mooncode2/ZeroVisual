package ru.zero.module.api.setting.impl;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.setting.Setting;

@Environment(EnvType.CLIENT)
public class ButtonSetting extends Setting {
   public int mode;
   public String description;
   public Runnable action;

   public ButtonSetting(String name, int mode) {
      this.name = name;
      this.mode = mode;
   }

   public ButtonSetting onClick(Runnable action) {
      this.action = action;
      return this;
   }

   public int get() {
      return this.mode;
   }

   public void set(int mode) {
      this.mode = mode;
   }

   public ButtonSetting hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }
}
