package ru.zero.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.EXTEfx;
import ru.zero.module.impl.visuals.RTXSounds;
import ru.zero.sound.filters.FilterLowPass;
import ru.zero.sound.filters.FilterReverb;

@Environment(EnvType.CLIENT)
public class SoundMixerBase {
   private final FilterReverb reverbFilter = new FilterReverb();
   private final FilterLowPass lowPassFilter = new FilterLowPass();
   private float echoPercent = 0.0F;
   private float reflectPercent = 0.0F;
   private float lowPassGain = 1.0F;
   private float lowPassGainHF = 1.0F;
   private int reverbEffectSlot = -1;
   private int reverbEffect = -1;
   private int lowPassFilter_AL = -1;
   private boolean initialized = false;
   private boolean efxSupported = false;

   public static SoundMixerBase loadMixer() {
      return new SoundMixerBase();
   }

   public void setEchoEffect(float echo, float reflect) {
      this.echoPercent = echo;
      this.reflectPercent = reflect;
   }

   public void setLowPass(float gain, float gainHF) {
      this.lowPassGain = gain;
      this.lowPassGainHF = gainHF;
   }

   public float getEchoPercent() {
      return this.echoPercent;
   }

   public float getReflectPercent() {
      return this.reflectPercent;
   }

   public float getLowPassGain() {
      return this.lowPassGain;
   }

   public float getLowPassGainHF() {
      return this.lowPassGainHF;
   }

   public void initializeEffects() {
      try {
         ALCapabilities capabilities = AL.getCapabilities();
         if (capabilities == null || !capabilities.ALC_EXT_EFX) {
            System.out.println("[RTX Sounds] EFX extension not supported, audio effects disabled");
            this.efxSupported = false;
            return;
         }

         this.efxSupported = true;
         System.out.println("[RTX Sounds] EFX extension supported, initializing audio effects...");
         this.reverbEffect = EXTEfx.alGenEffects();
         if (AL10.alGetError() != 0) {
            System.err.println("[RTX Sounds] Failed to create reverb effect");
            this.efxSupported = false;
            return;
         }

         EXTEfx.alEffecti(this.reverbEffect, 32769, 1);
         this.reverbEffectSlot = EXTEfx.alGenAuxiliaryEffectSlots();
         if (AL10.alGetError() != 0) {
            System.err.println("[RTX Sounds] Failed to create effect slot");
            this.efxSupported = false;
            return;
         }

         this.lowPassFilter_AL = EXTEfx.alGenFilters();
         if (AL10.alGetError() != 0) {
            System.err.println("[RTX Sounds] Failed to create lowpass filter");
            this.efxSupported = false;
            return;
         }

         EXTEfx.alFilteri(this.lowPassFilter_AL, 32769, 1);
         System.out.println("[RTX Sounds] Audio effects initialized successfully");
      } catch (Exception var2) {
         System.err.println("[RTX Sounds] Failed to initialize OpenAL effects: " + var2.getMessage());
         this.efxSupported = false;
      }
   }

   public void injectFiltersToChannel(SoundInstance sound, int sourceId) {
      if (sourceId > 0) {
         if (!this.initialized) {
            this.initializeEffects();
            this.initialized = true;
         }

         if (this.efxSupported) {
            try {
               if (this.reverbEffect != -1 && this.reverbEffectSlot != -1) {
                  this.applyReverbEffect();
                  EXTEfx.alAuxiliaryEffectSloti(this.reverbEffectSlot, 1, this.reverbEffect);
                  if (AL10.alGetError() == 0) {
                     AL11.alSource3i(sourceId, 131078, this.reverbEffectSlot, 0, 0);
                  }
               }

               if (this.lowPassFilter_AL != -1) {
                  this.applyLowPassFilter();
                  AL10.alSourcei(sourceId, 131077, this.lowPassFilter_AL);
               }

               if (RTXSounds.stereo.get()) {
                  this.applyBetterStereo(sound, sourceId);
               }

               if (RTXSounds.tone.get()) {
                  this.applyToneCompensation(sound, sourceId);
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   private void applyReverbEffect() {
      if (this.reverbEffect != -1) {
         try {
            EXTEfx.alEffectf(this.reverbEffect, 1, this.reverbFilter.density);
            EXTEfx.alEffectf(this.reverbEffect, 2, this.reverbFilter.diffusion);
            EXTEfx.alEffectf(this.reverbEffect, 3, this.reverbFilter.gain);
            EXTEfx.alEffectf(this.reverbEffect, 4, this.reverbFilter.gainHF);
            EXTEfx.alEffectf(this.reverbEffect, 5, this.reverbFilter.decayTime);
            EXTEfx.alEffectf(this.reverbEffect, 6, this.reverbFilter.decayHFRatio);
            EXTEfx.alEffectf(this.reverbEffect, 7, this.reverbFilter.reflectionsGain);
            EXTEfx.alEffectf(this.reverbEffect, 8, this.reverbFilter.reflectionsDelay);
            EXTEfx.alEffectf(this.reverbEffect, 9, this.reverbFilter.lateReverbGain);
            EXTEfx.alEffectf(this.reverbEffect, 10, this.reverbFilter.lateReverbDelay);
            EXTEfx.alEffectf(this.reverbEffect, 11, this.reverbFilter.airAbsorptionGainHF);
            EXTEfx.alEffectf(this.reverbEffect, 12, this.reverbFilter.roomRolloffFactor);
         } catch (Exception var2) {
            System.err.println("[RTX Sounds] Failed to apply reverb parameters: " + var2.getMessage());
         }
      }
   }

   private void applyLowPassFilter() {
      if (this.lowPassFilter_AL != -1) {
         try {
            EXTEfx.alFilterf(this.lowPassFilter_AL, 1, this.lowPassFilter.gain);
            EXTEfx.alFilterf(this.lowPassFilter_AL, 2, this.lowPassFilter.gainHF);
         } catch (Exception var2) {
            System.err.println("[RTX Sounds] Failed to apply lowpass parameters: " + var2.getMessage());
         }
      }
   }

   private void applyBetterStereo(SoundInstance sound, int sourceId) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && !sound.isRelative()) {
         Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
         Vec3d soundPos = new Vec3d(sound.getX(), sound.getY(), sound.getZ());
         Vec2f angles = SoundHelper.calculate(playerPos, soundPos);
         float playerYaw = mc.player.getYaw();
         float angleDiff = SoundHelper.getAngleDifference(angles.x, playerYaw);
         float stereoModifier = 1.0F - angleDiff / 180.0F * 0.3F;
         stereoModifier = Math.max(0.7F, Math.min(1.0F, stereoModifier));
         float currentVolume = sound.getVolume();
         AL10.alSourcef(sourceId, 4106, currentVolume * stereoModifier);
      }
   }

   private void applyToneCompensation(SoundInstance sound, int sourceId) {
      float basePitch = sound.getPitch();
      float toneModifier = 1.0F + this.echoPercent * 0.05F;
      toneModifier = Math.max(0.95F, Math.min(1.05F, toneModifier));
      AL10.alSourcef(sourceId, 4099, basePitch * toneModifier);
   }

   public void cleanupEffects() {
      try {
         if (this.reverbEffect != -1) {
            EXTEfx.alDeleteEffects(this.reverbEffect);
            this.reverbEffect = -1;
         }

         if (this.reverbEffectSlot != -1) {
            EXTEfx.alDeleteAuxiliaryEffectSlots(this.reverbEffectSlot);
            this.reverbEffectSlot = -1;
         }

         if (this.lowPassFilter_AL != -1) {
            EXTEfx.alDeleteFilters(this.lowPassFilter_AL);
            this.lowPassFilter_AL = -1;
         }
      } catch (Exception var2) {
         System.err.println("[RTX Sounds] Failed to cleanup OpenAL effects: " + var2.getMessage());
      }
   }

   public FilterLowPass getLowPassFilter() {
      return this.lowPassFilter;
   }

   public FilterReverb getReverbFilter() {
      return this.reverbFilter;
   }
}
