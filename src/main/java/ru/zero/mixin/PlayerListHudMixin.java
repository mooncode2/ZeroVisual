package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.module.impl.visuals.Animations;

@Environment(EnvType.CLIENT)
@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
   @Inject(method = "render", at = @At("HEAD"))
   private void zero$beginTabAnimation(
      DrawContext context,
      int scaledWindowWidth,
      Scoreboard scoreboard,
      @Nullable ScoreboardObjective objective,
      CallbackInfo ci
   ) {
      if (!Animations.isTabAnimationEnabled()) {
         return;
      }

      float progress = Animations.getTabProgress();
      if (progress >= 0.999F) {
         return;
      }

      float slide = (1.0F - progress) * 18.0F;
      context.getMatrices().pushMatrix();
      context.getMatrices().translate(0.0F, -slide);
   }

   @Inject(method = "render", at = @At("RETURN"))
   private void zero$endTabAnimation(
      DrawContext context,
      int scaledWindowWidth,
      Scoreboard scoreboard,
      @Nullable ScoreboardObjective objective,
      CallbackInfo ci
   ) {
      if (!Animations.isTabAnimationEnabled()) {
         return;
      }

      if (Animations.getTabProgress() >= 0.999F) {
         return;
      }

      context.getMatrices().popMatrix();
   }
}
