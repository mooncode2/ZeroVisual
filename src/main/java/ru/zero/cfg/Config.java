package ru.zero.cfg;

import com.google.gson.JsonObject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.api.Module;
import ru.zero.module.impl.visuals.HUD.HudEditor;
import ru.zero.ui.draggable.DraggableManager;
import ru.zero.util.render.math.animation.Animation;
import ru.zero.util.render.math.animation.impl.EaseInOutQuad;

@Environment(EnvType.CLIENT)
public final class Config implements ConfigUpdater {
   private final String name;
   private final File file;
   public Animation animation1 = new EaseInOutQuad(500, 1.0);
   public Animation animation2 = new EaseInOutQuad(300, 1.0);
   public Animation animation3 = new EaseInOutQuad(300, 1.0);
   public Animation animation4 = new EaseInOutQuad(300, 1.0);
   public Animation animation5 = new EaseInOutQuad(500, 1.0);

   public Config(String name) {
      this.name = name;
      this.file = new File(ConfigManager.configDirectory, name + ".json");
      if (!this.file.exists()) {
         try {
            this.file.createNewFile();
         } catch (Exception var3) {
         }
      }
   }

   public File getFile() {
      return this.file;
   }

   public String getName() {
      return this.name;
   }

   @Override
   public JsonObject save() {
      JsonObject jsonObject = new JsonObject();
      JsonObject modulesObject = new JsonObject();

      for (Module module : Zero.get.manager.module) {
         modulesObject.add(module.name, module.save());
      }

      jsonObject.add("Features", modulesObject);
      JsonObject draggablePositions = new JsonObject();
      Map<String, DraggableManager.NormalizedPosition> positions = DraggableManager.getInstance().snapshotNormalizedPositions();

      for (Entry<String, DraggableManager.NormalizedPosition> entry : positions.entrySet()) {
         JsonObject posObject = new JsonObject();
         posObject.addProperty("x", entry.getValue().x());
         posObject.addProperty("y", entry.getValue().y());
         draggablePositions.add(entry.getKey(), posObject);
      }

      jsonObject.add("DraggablePositions", draggablePositions);
      // Явное сохранение положения HUD-элементов (alias ключ для совместимости).
      jsonObject.add("HudElementPositions", draggablePositions.deepCopy());
      JsonObject hudScales = new JsonObject();
      HudEditor.snapshotScales().forEach((id, scale) -> hudScales.addProperty(id, scale));
      jsonObject.add("HudElementScales", hudScales);
      return jsonObject;
   }

   @Override
   public void load(JsonObject object) {
      System.out.println("[Config] Loading config: " + this.name);
      if (object.has("Features")) {
         JsonObject modulesObject = object.getAsJsonObject("Features");
         int enabledCount = 0;

         for (Module module : Zero.get.manager.module) {
            if (module.enable) {
               module.toggle();
            }

            if (modulesObject.has(module.name)) {
               module.load(modulesObject.getAsJsonObject(module.name));
               if (module.enable) {
                  enabledCount++;
                  System.out.println("[Config] Module enabled: " + module.name);
               }
            }
         }

         System.out.println("[Config] Total modules enabled: " + enabledCount);
      }

      JsonObject draggablePositions = null;
      if (object.has("DraggablePositions")) {
         draggablePositions = object.getAsJsonObject("DraggablePositions");
      } else if (object.has("HudElementPositions")) {
         draggablePositions = object.getAsJsonObject("HudElementPositions");
      }

      if (draggablePositions != null) {
         Map<String, DraggableManager.NormalizedPosition> positions = new HashMap<>();

         for (String key : draggablePositions.keySet()) {
            JsonObject posObject = draggablePositions.getAsJsonObject(key);
            if (posObject.has("x") && posObject.has("y")) {
               float x = posObject.get("x").getAsFloat();
               float y = posObject.get("y").getAsFloat();

               try {
                  positions.put(key, new DraggableManager.NormalizedPosition(x, y));
               } catch (Exception var10) {
                  System.out.println("[Config] Failed to load position for: " + key);
               }
            }
         }

         DraggableManager.getInstance().loadNormalizedPositions(positions);
         System.out.println("[Config] Loaded " + positions.size() + " draggable positions");
      }

      if (object.has("HudElementScales")) {
         JsonObject hudScales = object.getAsJsonObject("HudElementScales");
         Map<String, Float> scales = new HashMap<>();

         for (String key : hudScales.keySet()) {
            try {
               scales.put(key, hudScales.get(key).getAsFloat());
            } catch (Exception var8) {
            }
         }

         HudEditor.loadScales(scales);
      }
   }
}
