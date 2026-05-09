package ru.zero.module.api;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import ru.zero.Zero;
import ru.zero.event.EventManager;
import ru.zero.module.api.setting.Config;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BindSettings;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.module.api.setting.impl.ListSetting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.module.api.setting.impl.MultiBooleanSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.module.api.setting.impl.StringSetting;
import ru.zero.module.impl.visuals.Hud;
import ru.zero.util.render.animation.util.Animation;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.animation.Translate;
import ru.zero.util.render.math.animation.anim.util.Animation2;
import ru.zero.util.render.math.animation.anim.util.Easings;
import ru.zero.util.render.math.animation.impl.EaseInOutQuad;
import ru.zero.util.render.utils.SoundUtil;

@Environment(EnvType.CLIENT)
public class Module extends Config {
   public IModule module = this.getClass().getAnnotation(IModule.class);
   public static MinecraftClient mc = MinecraftClient.getInstance();
   public String name;
   public int bind;
   public boolean enable;
   public boolean open = false;
   public Category category;
   public String displayName;
   public String description;
   public boolean binding;
   public boolean isRender = true;
   public Translate a = new Translate(0.0F, 0.0F);
   public Animation animation = new Animation();
   public ru.zero.util.render.math.animation.Animation animation1 = new EaseInOutQuad(300, 1.0);
   public ru.zero.util.render.math.animation.Animation animation2 = new EaseInOutQuad(300, 1.0);
   public final Animation2 mAnim = new Animation2();
   public ru.zero.util.render.math.animation.Animation animation3 = new EaseInOutQuad(300, 1.0);
   public ru.zero.util.render.math.animation.Animation animation4 = new EaseInOutQuad(300, 1.0);

   public Module() {
      this.name = this.module.name();
      this.category = this.module.category();
      if (this.module.bind() == 0) {
         this.bind = -1;
      } else {
         this.bind = this.module.bind();
      }

      this.enable = false;
      this.description = this.module.description();
      this.displayName = this.name;
   }

   public void onEnable() {
      System.out.println("[Module] Enabling: " + this.name);

      try {
         EventManager.register(this);
      } catch (Exception var2) {
         System.err.println("[Module] Failed to enable " + this.name + ": " + var2.getMessage());
         var2.printStackTrace();
         this.enable = false;
         return;
      }

      if (mc.player != null) {
         Zero.get.manager.get(Hud.class).showNotification(this.name + " включен", Hud.NotificationType.SUCCESS, 2400L);
         SoundUtil.playSound_wav("on", 0.35F);
         mc.player.sendMessage(Text.of(this.module.name() + " enable"), false);
      }

      this.mAnim.run(1.0, 0.24F, Easings.QUART_OUT);
   }

   public void onDisable() {
      EventManager.unregister(this);
      if (mc.player != null) {
         Zero.get.manager.get(Hud.class).showNotification(this.name + " выключен", Hud.NotificationType.WARNING, 2400L);
         SoundUtil.playSound_wav("off", 0.35F);
         mc.player.sendMessage(Text.of(this.module.name() + " disable"), false);
      }

      this.mAnim.run(0.0, 0.24F, Easings.QUART_OUT);
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public void toggle() {
      this.enable = !this.enable;
      if (this.enable) {
         this.onEnable();
      } else {
         this.onDisable();
      }

      if (Zero.get.configManager != null) {
         Zero.get.configManager.autoSave();
      }
   }

   public JsonObject save() {
      JsonObject object = new JsonObject();
      if (this.enable) {
         object.addProperty("enable", this.enable);
      }

      if (this.bind > 0) {
         object.addProperty("keyIndex", this.bind);
      }

      JsonObject propertiesObject = new JsonObject();

      for (Setting set : this.getSettings()) {
         if (set instanceof BooleanSetting) {
            propertiesObject.addProperty(set.name, ((BooleanSetting)set).get());
         } else if (set instanceof ModeSetting) {
            propertiesObject.addProperty(set.name, ((ModeSetting)set).currentMode);
         } else if (set instanceof SliderSetting) {
            propertiesObject.addProperty(set.name, ((SliderSetting)set).current);
         } else if (set instanceof BindSettings) {
            propertiesObject.addProperty(set.name, ((BindSettings)set).key);
         } else if (set instanceof StringSetting) {
            propertiesObject.addProperty(set.name, ((StringSetting)set).input);
         } else if (set instanceof HueSetting) {
            propertiesObject.addProperty(set.name, ((HueSetting)set).current);
         } else if (set instanceof MultiBooleanSetting) {
            JsonObject multiBoolObject = new JsonObject();

            for (BooleanSetting boolSetting : ((MultiBooleanSetting)set).settings) {
               multiBoolObject.addProperty(boolSetting.name, boolSetting.get());
            }

            propertiesObject.add(set.name, multiBoolObject);
         }
      }

      object.add("Settings", propertiesObject);
      return object;
   }

   public void load(JsonObject object) {
      if (object != null) {
         if (object.has("enable")) {
            this.setState(object.get("enable").getAsBoolean());
         }

         if (object.has("keyIndex")) {
            this.bind = object.get("keyIndex").getAsInt();
         }

         for (Setting set : this.getSettings()) {
            JsonObject propertiesObject = object.getAsJsonObject("Settings");
            if (set != null && propertiesObject != null && propertiesObject.has(set.name)) {
               if (set instanceof BooleanSetting) {
                  ((BooleanSetting)set).set(propertiesObject.get(set.name).getAsBoolean());
               } else if (set instanceof ModeSetting) {
                  ((ModeSetting)set).currentMode = propertiesObject.get(set.name).getAsString();
               } else if (set instanceof SliderSetting) {
                  ((SliderSetting)set).current = propertiesObject.get(set.name).getAsFloat();
               } else if (set instanceof BindSettings) {
                  ((BindSettings)set).key = propertiesObject.get(set.name).getAsInt();
               } else if (set instanceof StringSetting) {
                  ((StringSetting)set).input = propertiesObject.get(set.name).getAsString();
               } else if (set instanceof HueSetting) {
                  ((HueSetting)set).current = propertiesObject.get(set.name).getAsFloat();
               } else if (set instanceof MultiBooleanSetting) {
                  if (propertiesObject.get(set.name).isJsonObject()) {
                     JsonObject multiBoolObject = propertiesObject.getAsJsonObject(set.name);

                     for (BooleanSetting boolSetting : ((MultiBooleanSetting)set).settings) {
                        if (multiBoolObject.has(boolSetting.name)) {
                           boolSetting.set(multiBoolObject.get(boolSetting.name).getAsBoolean());
                        }
                     }
                  }
               } else if (set instanceof ListSetting) {
                  String[] split = propertiesObject.get(set.name).getAsString().split(",");
                  ((ListSetting)set).selected = new ArrayList<>();

                  for (String s : split) {
                     if (((ListSetting)set).list.contains(s)) {
                        ((ListSetting)set).selected.add(s);
                     }
                  }
               }
            }
         }
      }
   }

   public int getBind() {
      return this.bind;
   }

   public void setState(boolean enable) {
      this.enable = enable;
      if (enable) {
         this.onEnable();
      } else {
         this.onDisable();
      }
   }

   public void setEnable(boolean enable) {
      this.enable = !enable;
      if (enable) {
         this.onEnable();
      } else {
         this.onDisable();
      }
   }
}
