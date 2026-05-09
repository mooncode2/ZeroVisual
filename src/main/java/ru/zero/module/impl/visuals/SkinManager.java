package ru.zero.module.impl.visuals;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventUpdate;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.MultiBooleanSetting;

@IModule(name = "Skin Manager", description = "Manages custom player skins", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class SkinManager extends Module {
   private static String customSkinPath = null;
   private static Identifier customSkinIdentifier = null;
   private static boolean isEnabled = false;
   private static boolean lastSetSkinValue = false;
   public static MultiBooleanSetting setSkin = new MultiBooleanSetting("Set skin",
         new BooleanSetting("Set skin", false));

   public SkinManager() {
      this.addSettings(new Setting[] { setSkin });
      loadSavedSkin();
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      boolean currentSetSkinValue = setSkin.get("Set skin");
      if (currentSetSkinValue && !lastSetSkinValue) {
         new Thread(() -> {
            openSkinFileDialog();
            setSkin.settings.get(0).set(false);
         }).start();
      }

      lastSetSkinValue = currentSetSkinValue;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      isEnabled = true;
      File skinFile = new File(getSkinDirectory(), "custom_skin.png");
      boolean hasSkin = skinFile.exists() && customSkinPath != null && !customSkinPath.isEmpty();
      if (!hasSkin) {
         new Thread(() -> openSkinFileDialog()).start();
      }
   }

   @Override
   public void onDisable() {
      super.onDisable();
      isEnabled = false;
      customSkinIdentifier = null;
   }

   private static File getSkinDirectory() {
      return new File(Zero.get.root, "skins");
   }

   private static void loadSavedSkin() {
      File skinFile = new File(getSkinDirectory(), "custom_skin.png");
      if (skinFile.exists()) {
         setCustomSkinPath(skinFile.getAbsolutePath());
      }
   }

   public static void setCustomSkinPath(String path) {
      customSkinPath = path;
      customSkinIdentifier = null;
   }

   private static Identifier loadCustomSkin() {
      if (customSkinPath != null && !customSkinPath.isEmpty()) {
         File skinFile = new File(customSkinPath);
         if (!skinFile.exists()) {
            return null;
         } else {
            try {
               NativeImage image = NativeImage.read(new FileInputStream(skinFile));
               Identifier identifier = Identifier.of("zero", "custom_skin_" + System.currentTimeMillis());
               NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> identifier.toString(), image);
               MinecraftClient.getInstance().getTextureManager().registerTexture(identifier, texture);
               customSkinIdentifier = identifier;
               return identifier;
            } catch (IOException var4) {
               var4.printStackTrace();
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static Object updatedPlayerSkin(Object originalSkin, PlayerEntity entity) {
      // TODO: Update for 1.21.11 - SkinTextures/PlayerSkin API changed
      return originalSkin;
   }

   private static void openSkinFileDialog() {
      try {
         File selectedFile = null;
         String os = System.getProperty("os.name").toLowerCase();
         if (os.contains("win")) {
            selectedFile = openWindowsFileDialog();
         } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            selectedFile = openLinuxFileDialog();
         } else if (os.contains("mac")) {
            try {
               ProcessBuilder pb = new ProcessBuilder("osascript", "-e",
                     "choose file of type {\"public.png\"} with prompt \"Select Skin File\"");
               Process process = pb.start();
               BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
               String path = reader.readLine();
               if (path != null && !path.isEmpty()) {
                  path = path.replaceFirst("^alias .*:", "").trim();
                  selectedFile = new File(path);
               }
            } catch (Exception var6) {
               var6.printStackTrace();
            }
         } else {
            selectedFile = openWindowsFileDialog();
         }

         if (selectedFile != null && selectedFile.exists() && selectedFile.getName().toLowerCase().endsWith(".png")) {
            saveSkinFile(selectedFile);
         }
      } catch (Exception var7) {
         var7.printStackTrace();
      }
   }

   private static File openWindowsFileDialog() {
      try {
         String powerShellScript = "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; $dialog = New-Object System.Windows.Forms.OpenFileDialog; $dialog.Filter = 'PNG Images (*.png)|*.png|All Files (*.*)|*.*'; $dialog.Title = 'Select Skin File'; $result = $dialog.ShowDialog(); if ($result -eq 'OK') { Write-Output $dialog.FileName }";
         ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", powerShellScript);
         pb.redirectErrorStream(true);
         Process process = pb.start();
         BufferedReader reader = new BufferedReader(
               new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
         String path = null;

         String line;
         while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() > 0
                  && !line.contains("System.Windows.Forms")
                  && !line.startsWith("PS ")
                  && !line.contains("Microsoft")
                  && (line.contains(":\\") || line.startsWith("/"))) {
               path = line;
               break;
            }
         }

         int exitCode = process.waitFor();
         reader.close();
         if (path != null && !path.isEmpty() && exitCode == 0) {
            return new File(path);
         }
      } catch (Exception var7) {
         var7.printStackTrace();
      }

      return null;
   }

   private static File openLinuxFileDialog() {
      try {
         ProcessBuilder pb = new ProcessBuilder("zenity", "--file-selection", "--title=Select Skin File",
               "--file-filter=*.png");
         Process process = pb.start();
         int exitCode = process.waitFor();
         if (exitCode == 0) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String path = reader.readLine();
            if (path != null && !path.isEmpty()) {
               return new File(path.trim());
            }
         }

         try {
            ProcessBuilder kdialogPb = new ProcessBuilder("kdialog", "--getopenfilename", "", "*.png");
            Process kdialogProcess = kdialogPb.start();
            int kdialogExitCode = kdialogProcess.waitFor();
            if (kdialogExitCode == 0) {
               BufferedReader kdialogReader = new BufferedReader(
                     new InputStreamReader(kdialogProcess.getInputStream()));
               String kdialogPath = kdialogReader.readLine();
               if (kdialogPath != null && !kdialogPath.isEmpty()) {
                  return new File(kdialogPath.trim());
               }
            }
         } catch (Exception var8) {
         }

         return null;
      } catch (Exception var9) {
         var9.printStackTrace();
         return null;
      }
   }

   private static void saveSkinFile(File sourceFile) {
      try {
         File skinDir = getSkinDirectory();
         if (!skinDir.exists()) {
            skinDir.mkdirs();
         }

         File destFile = new File(skinDir, "custom_skin.png");
         Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
         setCustomSkinPath(destFile.getAbsolutePath());
         MinecraftClient client = MinecraftClient.getInstance();
         if (client.player != null) {
            client.player.sendMessage(Text.literal("Skin saved successfully!"), false);
         }
      } catch (Exception var4) {
         var4.printStackTrace();
         MinecraftClient client = MinecraftClient.getInstance();
         if (client.player != null) {
            client.player.sendMessage(Text.literal("Error saving skin: " + var4.getMessage()), false);
         }
      }
   }
}
