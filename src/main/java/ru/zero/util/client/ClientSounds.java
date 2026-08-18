package ru.zero.util.client;

import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundInstance.AttenuationType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import ru.zero.ui.gui.GuiScreen;

@Environment(EnvType.CLIENT)
public final class ClientSounds {
   private static final WeakHashMap<String, PositionedSoundInstance> SOUND_CACHE = new WeakHashMap<>(4);

   private ClientSounds() {
   }

   public static void playClickGuiOpen() {
      play("clickgui_open", 0.85F, 1.0F);
   }

   public static void playClickGuiClose() {
      play("clickgui_open", 0.85F, 0.92F);
   }

   public static void playModuleToggle(boolean enabled) {
      play("toggle", 0.75F, enabled ? 1.08F : 0.92F);
   }

   private static void play(String name, float volume, float pitch) {
      if (!GuiScreen.clientSoundSetting.get()) {
         return;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.getSoundManager() == null) {
         return;
      }

      PositionedSoundInstance instance = SOUND_CACHE.computeIfAbsent(name, k -> createSoundInstance(k, volume, pitch));
      client.getSoundManager().play(instance);
   }

   private static PositionedSoundInstance createSoundInstance(String name, float volume, float pitch) {
      return new PositionedSoundInstance(
            Identifier.of("zero", name),
            SoundCategory.MASTER,
            volume,
            pitch,
            SoundInstance.createRandom(),
            false,
            0,
            AttenuationType.NONE,
            0.0,
            0.0,
            0.0,
            true
      );
   }
}