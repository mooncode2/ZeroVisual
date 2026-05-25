package ru.zero.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import ru.zero.Zero;
import ru.zero.module.impl.visuals.RTXSounds;
import ru.zero.sound.filters.FilterLowPass;
import ru.zero.sound.filters.FilterReverb;

@Environment(EnvType.CLIENT)
public class SoundMixFilter {
   private final SoundMixerBase mixer;
   private final SoundSurroundTool surround;
   private boolean state = false;

   private SoundMixFilter() {
      this.mixer = SoundMixerBase.loadMixer();
      this.surround = SoundSurroundTool.build();
   }

   public static SoundMixFilter makeDistorterMixer() {
      return new SoundMixFilter();
   }

   public void updateMixer() {
      float[] args = new float[]{0.0F, 0.0F, 1.0F, 1.0F};
      this.setState(Zero.get.manager.getModule(RTXSounds.class).enable);
      if (this.state) {
         MinecraftClient mc = MinecraftClient.getInstance();
         this.surround.setRtxDebug(false);
         this.surround.setPlayer(mc.player);
         this.surround.setTooPerfomance(RTXSounds.performancePriority.is("РџСЂРѕРёР·РІРѕРґРёС‚РµР»СЊРЅРѕСЃС‚СЊ"));
         args = this.surround.getGainArgsFromWorld();
      } else if (!this.surround.getListOfTestVecs().isEmpty()) {
         int sz = this.surround.getListOfTestVecs().size();

         for (int i = 0; i < sz / 2.0F; i++) {
            if (!this.surround.getListOfTestVecs().isEmpty()) {
               this.surround.getListOfTestVecs().remove(0);
            }
         }
      } else {
         this.surround.setRtxDebug(false);
      }

      float performanceMod = this.surround.isTooPerfomance() ? 0.75F : 1.0F;
      this.mixer.setEchoEffect(args[0] * performanceMod, args[1] * (this.surround.isTooPerfomance() ? 0.4F : 0.8F));
      this.mixer.setLowPass(args[2], args[3]);
      this.updateFiltersData();
   }

   private void updateFiltersData() {
      SoundMixerBase mixer = this.getMixer();
      FilterReverb reverbFilter = mixer.getReverbFilter();
      FilterLowPass lowPassFilter = mixer.getLowPassFilter();
      float echoDelay = mixer.getEchoPercent();
      float echoRev = echoDelay + mixer.getReflectPercent() * 8.000011F;
      reverbFilter.decayTime = echoDelay;
      reverbFilter.reflectionsGain = echoRev * (0.05F + 0.05F * echoDelay);
      reverbFilter.reflectionsDelay = 0.125F * echoDelay;
      reverbFilter.lateReverbGain = echoRev * (1.26F + 0.2F * echoDelay);
      reverbFilter.lateReverbDelay = 0.01F * echoDelay;
      reverbFilter.checkParameters();
      float lLevelGain = mixer.getLowPassGain();
      float lLevelGainHF = mixer.getLowPassGainHF();
      lowPassFilter.gain = this.lerp(lowPassFilter.gain, lLevelGain, lowPassFilter.gainHF > lLevelGainHF ? 0.05F : 0.1F);
      lowPassFilter.gainHF = this.lerp(lowPassFilter.gainHF, lLevelGainHF, lowPassFilter.gainHF > lLevelGainHF ? 0.3F : 0.12F);
      lowPassFilter.checkParameters();
   }

   private float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   public boolean getHasMixerLoaded() {
      return this.mixer != null;
   }

   public void init() {
   }

   public void unload() {
      if (this.mixer != null) {
         this.mixer.cleanupEffects();
      }
   }

   public SoundMixerBase getMixer() {
      return this.mixer;
   }

   public SoundSurroundTool getSurround() {
      return this.surround;
   }

   public boolean isState() {
      return this.state;
   }

   public void setState(boolean state) {
      this.state = state;
   }
}
