package ru.zero.mixin;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.SoundSystem.PlayResult;
import net.minecraft.client.sound.Channel.SourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zero.Zero;
import ru.zero.module.impl.visuals.RTXSounds;
import ru.zero.util.client.HitSoundHandler;

@Environment(EnvType.CLIENT)
@Mixin({SoundSystem.class})
public abstract class SoundSystemMixin {
   @Shadow
   private Map<SoundInstance, SourceManager> sources;

   @Inject(
      method = {"play"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSoundPlayHead(SoundInstance sound, CallbackInfoReturnable<PlayResult> cir) {
      if (HitSoundHandler.tryIntercept(sound)) {
         cir.setReturnValue(PlayResult.NOT_STARTED);
      }
   }

   @Inject(
      method = {"play"},
      at = {@At("RETURN")}
   )
   private void onSoundPlay(SoundInstance sound, CallbackInfoReturnable<PlayResult> cir) {
      if (Zero.get.manager.getModule(RTXSounds.class).enable) {
         if (Zero.rtx == null) {
            return;
         }

         try {
            SourceManager sourceManager = this.sources.get(sound);
            if (sourceManager != null) {
               sourceManager.run(source -> {
                  try {
                     int sourceId = ((SourceAccessor)source).rtx$getSourceId();
                     if (sourceId > 0) {
                        Zero.rtx.getMixer().injectFiltersToChannel(sound, sourceId);
                     }
                  } catch (Exception var3x) {
                  }
               });
            }
         } catch (Exception var4) {
         }
      }
   }
}
