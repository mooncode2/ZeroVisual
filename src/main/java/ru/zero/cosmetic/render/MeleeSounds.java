package ru.zero.cosmetic.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

/**
 * Plays cosmetic melee clip sounds (EvoVis {@code aia}) through the vanilla sound manager, so they
 * respect the player's volume settings and are positioned at the player.
 */
@Environment(EnvType.CLIENT)
public final class MeleeSounds {
   private MeleeSounds() {
   }

   public static SoundInstance play(String name) {
      if (name == null || name.isEmpty()) {
         return null;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.player == null || client.getSoundManager() == null) {
         return null;
      }

      Identifier id = Identifier.of("zero", name);
      PositionedSoundInstance instance = new PositionedSoundInstance(
            id,
            SoundCategory.PLAYERS,
            1.0F,
            1.0F,
            SoundInstance.createRandom(),
            false,
            0,
            SoundInstance.AttenuationType.NONE,
            client.player.getX(),
            client.player.getY(),
            client.player.getZ(),
            true);
      client.getSoundManager().play(instance);
      return instance;
   }

   public static void stop(SoundInstance instance) {
      if (instance == null) {
         return;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.getSoundManager() != null) {
         client.getSoundManager().stop(instance);
      }
   }

   public static boolean isActive(SoundInstance instance) {
      if (instance == null) {
         return false;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      return client != null && client.getSoundManager() != null && client.getSoundManager().isPlaying(instance);
   }
}
