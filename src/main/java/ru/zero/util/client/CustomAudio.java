package ru.zero.util.client;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundInstance.AttenuationType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import ru.zero.util.render.utils.SoundUtil;

@Environment(EnvType.CLIENT)
public final class CustomAudio {
   private CustomAudio() {
   }

   public static void playOgg(String name, float volume, float pitch) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.getSoundManager() == null) {
         return;
      }

      client.getSoundManager().play(new PositionedSoundInstance(
            Identifier.of("zero", name),
            SoundCategory.MASTER,
            clampVolume(volume),
            pitch,
            SoundInstance.createRandom(),
            false,
            0,
            AttenuationType.NONE,
            0.0,
            0.0,
            0.0,
            true
      ));
   }

   public static void playWav(String fileName, float volume) {
      SoundUtil.playSound_wav(fileName, clampVolume(volume));
   }

   public static void playRandomTf2Crit(float volume) {
      int pick = ThreadLocalRandom.current().nextInt(3);
      playOgg("crit" + (pick + 1), volume, 1.0F);
   }

   private static float clampVolume(float volume) {
      return Math.max(0.0F, Math.min(1.0F, volume));
   }
}
