package ru.zero.cfg;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.io.FilenameUtils;
import ru.zero.Zero;

@Environment(EnvType.CLIENT)
public final class ConfigManager extends Manager<Config> {
   public static final String AUTO_SAVE_CONFIG_NAME = "latest";
   public static final File configDirectory = getConfigDirectory();
   public static final String[] AUTO_SAVE_ALIASES = new String[] { "latest", "last", "lastest" };
   private static final ArrayList<Config> loadedConfigs = new ArrayList<>();

   private static File getConfigDirectory() {
      File root;
      if (Zero.get != null && Zero.get.root != null) {
         root = Zero.get.root;
      } else {
         File preRoot = new File(System.getProperty("user.home"), ".zerodlc");
         root = new File(preRoot, "ZeroDLC");
      }

      return new File(new File(root, "configs"), "cfg");
   }

   public ConfigManager() {
      loadedConfigs.clear();
      this.setContents(loadConfigs());
      configDirectory.mkdirs();
   }

   private static ArrayList<Config> loadConfigs() {
      loadedConfigs.clear();
      File[] files = configDirectory.listFiles();
      if (files != null) {
         for (File file : files) {
            if (FilenameUtils.getExtension(file.getName()).equals("json")) {
               String cfgName = FilenameUtils.removeExtension(file.getName());
               boolean exists = loadedConfigs.stream().anyMatch(c -> c.getName().equalsIgnoreCase(cfgName));
               if (!exists) {
                  loadedConfigs.add(new Config(cfgName));
               }
            }
         }
      }

      return loadedConfigs;
   }

   public static ArrayList<Config> getLoadedConfigs() {
      return loadedConfigs;
   }

   public void load() {
      if (!configDirectory.exists()) {
         configDirectory.mkdirs();
      }

      if (configDirectory != null) {
         loadedConfigs.clear();
         File[] files = configDirectory.listFiles(fx -> !fx.isDirectory() && FilenameUtils.getExtension(fx.getName()).equals("json"));

         if (files != null) {
            for (File f : files) {
               String cfgName = FilenameUtils.removeExtension(f.getName());
               boolean exists = loadedConfigs.stream().anyMatch(c -> c.getName().equalsIgnoreCase(cfgName));
               if (!exists) {
                  loadedConfigs.add(new Config(cfgName));
               }
            }
         }

         this.setContents(new ArrayList<>(loadedConfigs));
      }
   }

   public boolean loadConfig(String configName) {
      if (configName == null) {
         return false;
      } else {
         Config config = this.findConfig(configName);
         if (config == null) {
            return false;
         } else {
            try {
               boolean var6;
               try (FileReader reader = new FileReader(config.getFile())) {
                  JsonParser parser = new JsonParser();
                  JsonObject object = (JsonObject)parser.parse(reader);
                  config.load(object);
                  var6 = true;
               }

               return var6;
            } catch (IOException var9) {
               return false;
            }
         }
      }
   }

   public boolean saveConfig(String configName) {
      if (configName == null) {
         return false;
      } else {
         Config config;
         if ((config = this.findConfig(configName)) == null) {
            Config newConfig = config = new Config(configName);
            this.getContents().add(newConfig);
            loadedConfigs.add(newConfig);
         }

         String contentPrettyPrint = new GsonBuilder().setPrettyPrinting().create().toJson(config.save());

         try {
            boolean var5;
            try (FileWriter writer = new FileWriter(config.getFile())) {
               writer.write(contentPrettyPrint);
               this.load();
               var5 = true;
            }

            return var5;
         } catch (IOException var9) {
            return false;
         }
      }
   }

   public Config findConfig(String configName) {
      if (configName == null) {
         return null;
      } else {
         for (Config config : this.getContents()) {
            if (config.getName().equalsIgnoreCase(configName)) {
               return config;
            }
         }

         if (new File(configDirectory, configName + ".json").exists()) {
            Config config = new Config(configName);
            this.getContents().add(config);
            if (loadedConfigs.stream().noneMatch(c -> c.getName().equalsIgnoreCase(configName))) {
               loadedConfigs.add(config);
            }
            return config;
         }

         return null;
      }
   }

   public boolean deleteConfig(String configName) {
      if (configName == null) {
         return false;
      } else {
         Config config;
         if ((config = this.findConfig(configName)) == null) {
            return false;
         } else {
            File f = config.getFile();
            this.getContents().remove(config);
            loadedConfigs.removeIf(c -> c.getName().equalsIgnoreCase(configName));
            boolean deleted = f.exists() && f.delete();
            this.load();
            return deleted;
         }
      }
   }

   public void autoSave() {
      if (Zero.get.configManager != null) {
         // Пишем основной autosave и legacy-алиасы для совместимости.
         this.saveConfig(AUTO_SAVE_CONFIG_NAME);
         this.saveConfig("last");
         this.saveConfig("lastest");
      }
   }
}
