package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zero.Zero;
import ru.zero.event.EventManager;
import ru.zero.event.player.EventJump;
import ru.zero.module.impl.visuals.SwingAnimation;

/**
 * Клиентские хуки живой сущности: прыжок (событие), длительность замаха (модуль Swing Animation).
 * Настройки задержки прыжка из модуля NoDelay удалены вместе с зависимостью от player-модулей.
 */
@Environment(EnvType.CLIENT)
@Mixin({ LivingEntity.class })
public abstract class LivingEntityMixin {
   @Unique
   private final MinecraftClient client = MinecraftClient.getInstance();

   @Shadow
   public abstract boolean hasStatusEffect(RegistryEntry<StatusEffect> var1);

   @Shadow
   @Nullable
   public abstract StatusEffectInstance getStatusEffect(RegistryEntry<StatusEffect> var1);

   @Inject(method = { "jump" }, at = { @At("HEAD") })
   public void jumpYo(CallbackInfo ci) {
      LivingEntity self = (LivingEntity) (Object) this;
      if (self instanceof ClientPlayerEntity) {
         EventManager.call(new EventJump());
      }
   }

   @Inject(method = { "getHandSwingDuration" }, at = { @At("HEAD") }, cancellable = true)
   private void swingProgressHook(CallbackInfoReturnable<Integer> cir) {
      LivingEntity self = (LivingEntity) (Object) this;
      if (self instanceof ClientPlayerEntity) {
         if (Zero.isModInitialized()) {
            if (this.client != null && this.client.player != null) {
               if (self == this.client.player) {
                  if (Zero.get != null && Zero.get.manager != null) {
                     SwingAnimation swingModule = (SwingAnimation) Zero.get.manager.getModule(SwingAnimation.class);
                     if (swingModule != null && swingModule.enable) {
                        if (!SwingAnimation.swingMode.is("Off")) {
                           if (SwingAnimation.auraCheck()) {
                              float speed = SwingAnimation.animSpeed.get();
                              if (!(speed <= 0.0F)) {
                                 int baseDuration = 6;
                                 if (StatusEffectUtil.hasHaste(self)) {
                                    baseDuration = Math.max(1,
                                          baseDuration - (1 + StatusEffectUtil.getHasteAmplifier(self)));
                                 } else if (this.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
                                    StatusEffectInstance fatigueEffect = this
                                          .getStatusEffect(StatusEffects.MINING_FATIGUE);
                                    if (fatigueEffect != null) {
                                       baseDuration += (1 + fatigueEffect.getAmplifier()) * 2;
                                    }
                                 }

                                 int duration = Math.max(1, (int) (baseDuration / speed));
                                 cir.setReturnValue(duration);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

}
