package ru.zero.cfg;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.io.FilenameUtils;
import ru.zero.Zero;
import ru.zero.module.api.Module;

@Environment(EnvType.CLIENT)
public final class ConfigManager extends Manager<Config> {
   public static final String AUTO_SAVE_CONFIG_NAME = "latest";
   public static final File configDirectory = getConfigDirectory();
   public static final String[] AUTO_SAVE_ALIASES = new String[] { "latest", "last", "lastest" };
   private static final long AUTO_SAVE_DEBOUNCE_MS = 500L;
   private static final ArrayList<Config> loadedConfigs = new ArrayList<>();
   private static final ScheduledExecutorService AUTO_SAVE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "zero-config-autosave");
      thread.setDaemon(true);
      return thread;
   });
   private volatile ScheduledFuture<?> pendingAutoSave;

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
               Module.beginConfigLoad();
               try (FileReader reader = new FileReader(config.getFile())) {
                  JsonParser parser = new JsonParser();
                  JsonObject object = (JsonObject)parser.parse(reader);
                  config.load(object);
                  return true;
               } finally {
                  Module.endConfigLoad();
               }
            } catch (IOException var9) {
               System.err.println("[Config] Failed to load '" + configName + "': " + var9.getMessage());
               return false;
            } catch (RuntimeException e) {
               System.err.println("[Config] Corrupt config '" + configName + "': " + e.getMessage());
               e.printStackTrace();
               return false;
            }
         }
      }
   }

   public boolean saveConfig(String configName) {
      return this.saveConfig(configName, true);
   }

   private boolean saveConfig(String configName, boolean refreshIndex) {
      if (configName == null) {
         return false;
      } else {
         Config config;
         if ((config = this.findConfig(configName)) == null) {
            Config newConfig = config = new Config(configName);
            this.getContents().add(newConfig);
            loadedConfigs.add(newConfig);
         }

         try {
            JsonObject data = config.save();
            this.sanitizeNumbers(data);
            String contentPrettyPrint = new GsonBuilder().setPrettyPrinting().create().toJson(data);
            try (FileWriter writer = new FileWriter(config.getFile())) {
               writer.write(contentPrettyPrint);
               if (refreshIndex) {
                  this.load();
               }
            }
            return true;
         } catch (IOException var9) {
            System.err.println("[Config] Save failed for '" + configName + "': " + var9.getMessage());
            return false;
         } catch (RuntimeException e) {
            System.err.println("[Config] Save failed for '" + configName + "': " + e.getMessage());
            e.printStackTrace();
            return false;
         }
      }
   }

   private void sanitizeNumbers(JsonObject root) {
      if (root == null) {
         return;
      }

      java.util.List<String> toReplace = null;
      java.util.List<JsonPrimitive> replacements = null;

      for (java.util.Map.Entry<String, JsonElement> entry : root.entrySet()) {
         JsonElement element = entry.getValue();
         if (element.isJsonObject()) {
            this.sanitizeNumbers(element.getAsJsonObject());
         } else if (element.isJsonArray()) {
            com.google.gson.JsonArray arr = element.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
               JsonElement child = arr.get(i);
               if (child.isJsonObject()) {
                  this.sanitizeNumbers(child.getAsJsonObject());
               } else if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isNumber() && !isFinite(child.getAsFloat())) {
                  arr.set(i, new JsonPrimitive(0.0F));
               }
            }
         } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() && !isFinite(element.getAsFloat())) {
            if (toReplace == null) {
               toReplace = new java.util.ArrayList<>();
               replacements = new java.util.ArrayList<>();
            }
            toReplace.add(entry.getKey());
            replacements.add(new JsonPrimitive(0.0F));
         }
      }

      if (toReplace != null) {
         for (int i = 0; i < toReplace.size(); i++) {
            root.add(toReplace.get(i), replacements.get(i));
         }
      }
   }

   private static boolean isFinite(float value) {
      return !Float.isNaN(value) && !Float.isInfinite(value);
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
      if (Zero.get == null || Zero.get.configManager != this) {
         return;
      }

      ScheduledFuture<?> previous = this.pendingAutoSave;
      if (previous != null) {
         previous.cancel(false);
      }

      this.pendingAutoSave = AUTO_SAVE_EXECUTOR.schedule(this::performAutoSave, AUTO_SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
   }

   private void performAutoSave() {
      try {
         if (!this.saveConfig(AUTO_SAVE_CONFIG_NAME, false)) {
            return;
         }

         Config primary = this.findConfig(AUTO_SAVE_CONFIG_NAME);
         if (primary == null) {
            return;
         }

         this.copyAliases(primary.getFile());
      } catch (Throwable t) {
         System.err.println("[Config] Auto-save failed: " + t.getMessage());
         t.printStackTrace();
      }
   }

   /**
    * Синхронно сохраняет автоконфиг на вызывающем потоке (используется при выходе из игры,
    * чтобы последние изменения не потерялись из-за debounce-задержки фонового потока).
    */
   public void flushAutoSave() {
      ScheduledFuture<?> pending = this.pendingAutoSave;
      if (pending != null) {
         pending.cancel(false);
         this.pendingAutoSave = null;
      }

      try {
         if (!this.saveConfig(AUTO_SAVE_CONFIG_NAME, false)) {
            return;
         }

         Config primary = this.findConfig(AUTO_SAVE_CONFIG_NAME);
         if (primary == null) {
            return;
         }

         this.copyAliases(primary.getFile());
      } catch (Throwable t) {
         System.err.println("[Config] Flush auto-save failed: " + t.getMessage());
         t.printStackTrace();
      }
   }

   private void copyAliases(File source) {
      for (String alias : AUTO_SAVE_ALIASES) {
         if (alias.equalsIgnoreCase(AUTO_SAVE_CONFIG_NAME)) {
            continue;
         }

         try {
            File target = new File(configDirectory, alias + ".json");
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (this.findConfig(alias) == null) {
               Config aliasConfig = new Config(alias);
               this.getContents().add(aliasConfig);
               loadedConfigs.add(aliasConfig);
            }
         } catch (IOException ignored) {
         }
      }
   }

   public static void shutdown() {
      AUTO_SAVE_EXECUTOR.shutdown();
      try {
         if (!AUTO_SAVE_EXECUTOR.awaitTermination(2L, TimeUnit.SECONDS)) {
            AUTO_SAVE_EXECUTOR.shutdownNow();
         }
      } catch (InterruptedException e) {
         AUTO_SAVE_EXECUTOR.shutdownNow();
         Thread.currentThread().interrupt();
      }
   }
}
