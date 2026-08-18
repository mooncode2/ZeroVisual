package ru.zero.util.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import ru.zero.Zero;
import ru.zero.module.impl.utils.HitSound;

@Environment(EnvType.CLIENT)
public final class HitSoundHandler {
   private static final SoundEvent[] ATTACK_SOUNDS = new SoundEvent[] {
         SoundEvents.ENTITY_PLAYER_ATTACK_WEAK,
         SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
         SoundEvents.ENTITY_PLAYER_ATTACK_CRIT,
         SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,
         SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE,
         SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP
   };

   private HitSoundHandler() {
   }

   public static boolean tryIntercept(SoundInstance sound) {
      if (Zero.get == null || Zero.get.manager == null) {
         return false;
      }

      HitSound module = Zero.get.manager.get(HitSound.class);
      if (module == null || !module.enable || sound == null) {
         return false;
      }

      Identifier soundId = sound.getId();
      if (soundId == null) {
         return false;
      }

      boolean isAttackSound = false;
      boolean isCritSound = false;

      for (SoundEvent attackSound : ATTACK_SOUNDS) {
         if (attackSound == null || attackSound.id() == null) {
            continue;
         }

         if (attackSound.id().equals(soundId)) {
            isAttackSound = true;
            if (attackSound == SoundEvents.ENTITY_PLAYER_ATTACK_CRIT) {
               isCritSound = true;
            }
            break;
         }
      }

      if (!isAttackSound) {
         return false;
      }

      if (module.critOnly.get()) {
         if (!isCritSound) {
            return true;
         }
         module.playSelectedSound(true);
         return true;
      }

      module.playSelectedSound(isCritSound);
      return true;
   }
}
