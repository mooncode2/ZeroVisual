package ru.zero.module.api.setting.impl;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import ru.zero.module.api.setting.Setting;

@Environment(EnvType.CLIENT)
public class BindSettings extends Setting {
   public int key;
   public String description;
   public boolean active;

   public BindSettings(String name, int key) {
      this.name = name;
      this.key = key;
      this.description = this.description;
   }

   public int get() {
      return this.key;
   }

   public void set(int key) {
      this.key = key;
   }

   public BindSettings hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }

   public boolean isKeyDown(int keyCode) {
      long handle = MinecraftClient.getInstance().getWindow().getHandle();
      return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), keyCode);
   }
}
