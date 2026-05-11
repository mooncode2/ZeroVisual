package ru.zero.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import ru.zero.Zero;
import ru.zero.module.api.Category;
import ru.zero.module.api.Theme;

@Environment(EnvType.CLIENT)
public class GuiManager {
   public static MinecraftClient mc = MinecraftClient.getInstance();
   private File file;
   private Theme currentTheme = Theme.THEME1;
   private Category currentCategory = Category.Visuals;
   private boolean guiBlurEnabled = false;
   private boolean guiVanillaStyleEnabled = false;
   private boolean guiVulcanModeEnabled = false;

   public void init() {
      this.file = new File(new File(Zero.get.root, "configs"), "gui.cfg");

      try {
         if (!this.file.getParentFile().exists()) {
            this.file.getParentFile().mkdirs();
         }

         if (!this.file.exists()) {
            this.file.createNewFile();
            this.saveSettings();
         } else {
            this.readSettings();
         }
      } catch (Exception var2) {
         var2.printStackTrace();
      }
   }

   public void setGuiTheme(Theme theme) {
      this.currentTheme = theme;
      this.saveSettings();
   }

   public void setGuiCategory(Category category) {
      this.currentCategory = category;
      this.saveSettings();
   }

   public void setGuiBlurEnabled(boolean enabled) {
      this.guiBlurEnabled = enabled;
      this.saveSettings();
   }

   public void setGuiVanillaStyleEnabled(boolean enabled) {
      this.guiVanillaStyleEnabled = enabled;
      this.saveSettings();
   }

   public void setGuiVulcanModeEnabled(boolean enabled) {
      this.guiVulcanModeEnabled = enabled;
      this.saveSettings();
   }

   public Theme getCurrentTheme() {
      return this.currentTheme;
   }

   public Category getCurrentCategory() {
      return this.currentCategory;
   }

   public boolean isGuiBlurEnabled() {
      return this.guiBlurEnabled;
   }

   public boolean isGuiVanillaStyleEnabled() {
      return this.guiVanillaStyleEnabled;
   }

   public boolean isGuiVulcanModeEnabled() {
      return this.guiVulcanModeEnabled;
   }

   private void saveSettings() {
      try (FileWriter writer = new FileWriter(this.file)) {
         Properties props = new Properties();
         props.setProperty("theme", this.currentTheme.name());
         props.setProperty("category", this.currentCategory.name());
         props.setProperty("guiBlur", String.valueOf(this.guiBlurEnabled));
         props.setProperty("guiVanillaStyle", String.valueOf(this.guiVanillaStyleEnabled));
         props.setProperty("guiVulcanMode", String.valueOf(this.guiVulcanModeEnabled));
         props.store(writer, "GUI Settings");
      } catch (IOException var6) {
         var6.printStackTrace();
      }
   }

   private void readSettings() {
      try (FileReader reader = new FileReader(this.file)) {
         Properties props = new Properties();
         props.load(reader);
         this.currentTheme = Theme.valueOf(props.getProperty("theme", Theme.THEME1.name()));
         this.currentCategory = Category.valueOf(props.getProperty("category", Category.Visuals.name()));
         this.guiBlurEnabled = Boolean.parseBoolean(props.getProperty("guiBlur", "false"));
         this.guiVanillaStyleEnabled = Boolean.parseBoolean(props.getProperty("guiVanillaStyle", "false"));
         this.guiVulcanModeEnabled = Boolean.parseBoolean(props.getProperty("guiVulcanMode", "false"));
      } catch (IllegalArgumentException | IOException var6) {
         var6.printStackTrace();
      }
   }
}
