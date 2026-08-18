package ru.zero.module.impl.visuals.HUD;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import ru.zero.module.impl.visuals.Hud;
import ru.zero.module.impl.visuals.HUD.HudEditor;
import ru.zero.ui.draggable.DraggableManager;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.animation.AnimationMath;
import ru.zero.util.render.math.animation.anim.util.Animation2;
import ru.zero.util.render.math.animation.anim.util.Easings;
import ru.zero.util.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
public class PotionsHUD {
   public static MinecraftClient mc = MinecraftClient.getInstance();
   private static final Map<RegistryEntry<StatusEffect>, Integer> maxDurations = new HashMap<>();
   private static final Map<RegistryEntry<StatusEffect>, Float> animatedWidths = new HashMap<>();
   private static final Map<RegistryEntry<StatusEffect>, Float> animatedLineX = new HashMap<>();
   private static final Map<RegistryEntry<StatusEffect>, Float> animatedY = new HashMap<>();
   private static final Map<RegistryEntry<StatusEffect>, Animation2> animatedAlphas = new HashMap<>();
   private static final Map<RegistryEntry<StatusEffect>, StatusEffectInstance> cachedEffects = new HashMap<>();
   private static final Map<RegistryEntry<StatusEffect>, String> cachedEffectText = new HashMap<>();
   private static final Map<RegistryEntry<StatusEffect>, Integer> cachedEffectAmplifier = new HashMap<>();
   private static final int HARMFUL_TEXT_COLOR = new Color(16734547).getRGB();

   private static float getAdjustedAnimationSpeed() {
      float hudScale = HudEditor.getScale("potions");
      return 0.1F * Math.min(1.0F, 1.0F / hudScale);
   }

   private static String getEffectText(RegistryEntry<StatusEffect> effectType, StatusEffectInstance effect) {
      int amplifier = effect.getAmplifier();
      Integer cachedAmp = cachedEffectAmplifier.get(effectType);
      if (cachedAmp == null || cachedAmp != amplifier || !cachedEffectText.containsKey(effectType)) {
         String effectText = effect.getTranslationKey().replace("effect.minecraft.", "");
         cachedEffectText.put(effectType, effectText.substring(0, 1).toUpperCase()
               + effectText.substring(1).replace("_", " ")
               + " "
               + String.valueOf(amplifier + 1).replace("1", ""));
         cachedEffectAmplifier.put(effectType, amplifier);
      }
      return cachedEffectText.get(effectType);
   }

   public static void potions(Renderer2D r2, DrawContext drawContext) {
      if (mc.player != null) {
         float hudScale = HudEditor.getScale("potions");
         Set<RegistryEntry<StatusEffect>> activeEffects = mc.player
               .getStatusEffects()
               .stream()
               .<RegistryEntry<StatusEffect>>map(StatusEffectInstance::getEffectType)
               .collect(Collectors.toSet());

         Set<RegistryEntry<StatusEffect>> activeFiniteTypes = new HashSet<>();
         for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
            cachedEffects.put(effect.getEffectType(), effect);
            RegistryEntry<StatusEffect> effectType = effect.getEffectType();
            if (effect.getDuration() < StatusEffectInstance.INFINITE) {
               activeFiniteTypes.add(effectType);
            }
            if (!animatedAlphas.containsKey(effectType)) {
               Animation2 newAnim = new Animation2();
               newAnim.set(0.0);
               animatedAlphas.put(effectType, newAnim);
            }
         }

         animatedAlphas.forEach((effectTypex, anim) -> {
            double targetValue = activeEffects.contains(effectTypex) ? 1.0 : 0.0;
            anim.run(targetValue, 0.6, Easings.QUART_OUT, true);
            anim.update();
         });
         Set<RegistryEntry<StatusEffect>> effectsToRender = new HashSet<>(activeEffects);
         animatedAlphas.forEach((effectTypex, anim) -> {
            if (anim.get() > 0.01F) {
               effectsToRender.add(effectTypex);
            }
         });
         List<RegistryEntry<StatusEffect>> sortedEffects = new ArrayList<>(effectsToRender);
         sortedEffects.sort((a, b) -> {
            boolean aActive = activeEffects.contains(a);
            boolean bActive = activeEffects.contains(b);
            if (aActive != bActive) {
               return aActive ? -1 : 1;
            } else {
               return 0;
            }
         });
         float preferredX = 20.0F;
         float preferredY = 474.0F;
         float measureOffset = 0.0F;
         float measureMaxWidth = 0.0F;

         for (RegistryEntry<StatusEffect> effectType : sortedEffects) {
            StatusEffectInstance effectMeasure = cachedEffects.get(effectType);
            if (effectMeasure != null) {
               Animation2 alphaAnimMeasure = animatedAlphas.get(effectType);
               if (alphaAnimMeasure != null) {
                  float currentAlpha = alphaAnimMeasure.get();
                  if (currentAlpha > 0.01F) {
                     String text = getEffectText(effectType, effectMeasure);
                     float textWidth = r2.measureText(FontRegistry.INTER_MEDIUM, text, 20.0F).width;
                     measureMaxWidth = Math.max(measureMaxWidth, 72.0F + textWidth);
                     measureOffset += 34.0F * currentAlpha;
                  }
               }
            }
         }

         float boundsWidth = Math.max(measureMaxWidth, 120.0F);
         float boundsHeight = Math.max(measureOffset, 30.0F);
         DraggableManager.DragSession dragSession = DraggableManager.getInstance()
            .beginDrag("potions", preferredX, preferredY, boundsWidth, boundsHeight);
         float x = dragSession.positionX();
         float y = dragSession.positionY();
         float offset = 0.0F;
         float offsetTexture = 0.0F;
         float maxRenderedWidth = 0.0F;
         int effectIndex = 0;

         for (RegistryEntry<StatusEffect> effectType : sortedEffects) {
            StatusEffectInstance effectx = cachedEffects.get(effectType);
            if (effectx != null) {
               int currentDuration = activeEffects.contains(effectType) ? effectx.getDuration() : 0;
               Animation2 alphaAnim = animatedAlphas.get(effectType);
               if (alphaAnim != null) {
                  float currentAlpha = alphaAnim.get();
                  float x3 = -80.0F + 80.0F * alphaAnim.get();
                  float targetY = y + offset;
                  float currentAnimatedY = animatedY.getOrDefault(effectType, targetY);
                  currentAnimatedY = AnimationMath.animation(currentAnimatedY, targetY, getAdjustedAnimationSpeed());
                  animatedY.put(effectType, currentAnimatedY);
                  float y3 = currentAnimatedY - targetY;
                  if (currentAlpha <= 0.01F) {
                     offsetTexture += 18.0F * currentAlpha;
                     offset += 34.0F * currentAlpha;
                     effectIndex++;
                   } else {
                      r2.pushAlpha(currentAlpha);
                      boolean isInfinite = effectx.getDuration() >= StatusEffectInstance.INFINITE;
                      if (!isInfinite) {
                         if (!maxDurations.containsKey(effectType) || currentDuration > maxDurations.get(effectType)) {
                            maxDurations.put(effectType, currentDuration);
                         }
                      }

                      String text = getEffectText(effectType, effectx);
                      float textWidth = r2.measureText(FontRegistry.INTER_MEDIUM, text, 20.0F).width;
                      float mainRectWidth = 72.0F + textWidth;
                      maxRenderedWidth = Math.max(maxRenderedWidth, mainRectWidth);
                      Hud.drawClientRect(r2, x + x3, currentAnimatedY, mainRectWidth, 30.0F, 10.0F, 1.0F, 1.0F);
                      int maxDuration = isInfinite ? 0 : maxDurations.getOrDefault(effectType, 0);
                      float progress = maxDuration > 0 ? (float) currentDuration / maxDuration : 0.0F;
                      float targetWidth = mainRectWidth * progress;
                     float currentAnimatedWidth = animatedWidths.getOrDefault(effectType, targetWidth);
                     currentAnimatedWidth = AnimationMath.animation(currentAnimatedWidth, targetWidth, getAdjustedAnimationSpeed());
                     animatedWidths.put(effectType, currentAnimatedWidth);
                     int color = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 0);
                     int color2 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 28);
                     if (currentAnimatedWidth > 1.0F) {
                        float gradientWidth = currentAnimatedWidth - 1.0F;
                        float targetLineX = x + gradientWidth + x3;
                        float currentLineX = animatedLineX.getOrDefault(effectType, targetLineX);
                        currentLineX = AnimationMath.animation(currentLineX, targetLineX, getAdjustedAnimationSpeed());
                        animatedLineX.put(effectType, currentLineX);
                        r2.horizontalGradient(x, currentAnimatedY, gradientWidth, 30.0F, 3.0F, 0.0F, 0.0F, 3.0F, color,
                              color2);
                        r2.rect(currentLineX, currentAnimatedY, 1.0F, 30.0F, Renderer2D.ColorUtil.getMainColor(1, 1));
                     }

                     Identifier effectTexture = getEffectTexture(effectx.getEffectType());
                     float iconAreaWidth = 37.5F;
                     float rowHeight = 30.0F;
                     // Принудительный правильный размер иконки, подстраивающийся под высоту строки
                     float iconSize = rowHeight * 0.6F;
                     float textFontSize = 20.0F;
                     float elementHeight = 30.0F;
                     int textCodepoint = text.codePointAt(0);
                     float textCenterY = currentAnimatedY + elementHeight * 0.5F;
                     float baselineOffset = FontRegistry.centeredBaselineOffset(FontRegistry.INTER_MEDIUM, textCodepoint, textFontSize * 0.5F);
                     float textBaselineY = textCenterY + baselineOffset;
                     float iconX = x + x3 + (iconAreaWidth - iconSize) * 0.5F;
                     float iconY = currentAnimatedY + (rowHeight - iconSize) * 0.5F;
                     float guiScale = mc.getWindow() != null ? (float) mc.getWindow().getScaleFactor() : 1.0F;
                     float scaleOriginX = HudEditor.getOriginX("potions");
                     float scaleOriginY = HudEditor.getOriginY("potions");
                     if (scaleOriginX == 0.0F && scaleOriginY == 0.0F) {
                        scaleOriginX = x;
                        scaleOriginY = y;
                     }
                     float scaledOriginX = scaleOriginX / guiScale;
                     float scaledOriginY = scaleOriginY / guiScale;
                     drawContext.getMatrices().pushMatrix();
                     drawContext.getMatrices().translate(scaledOriginX, scaledOriginY);
                     drawContext.getMatrices().scale(hudScale, hudScale);
                     drawContext.getMatrices().translate(-scaledOriginX, -scaledOriginY);
                     drawContext.drawGuiTexture(
                           RenderPipelines.GUI_TEXTURED,
                           effectTexture,
                           Math.round(iconX / guiScale),
                           Math.round(iconY / guiScale),
                           Math.round(iconSize / guiScale),
                           Math.round(iconSize / guiScale));
                     drawContext.getMatrices().popMatrix();
                     r2.rect(
                           x + 43.0F - 5.5F + x3,
                           currentAnimatedY + 10.0F,
                           2.0F,
                           9.0F,
                           4.0F,
                           Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 51));
                     boolean isBeneficial = effectx.getEffectType().value().isBeneficial();
                     int textColor = isBeneficial ? Renderer2D.ColorUtil.getTextColor(1, 1)
                           : HARMFUL_TEXT_COLOR;
                     float textX = x + x3 + iconAreaWidth + 10.0F;
                     r2.text(FontRegistry.INTER_MEDIUM, textX, textBaselineY, textFontSize, text,
                           textColor);
                     r2.popAlpha();
                     offsetTexture += 18.0F * currentAlpha;
                     offset += 34.0F * currentAlpha;
                     effectIndex++;
                  }
               }
            }
         }

         maxDurations.keySet().removeIf(key -> !activeFiniteTypes.contains(key));
         animatedWidths.keySet().removeIf(key -> !activeEffects.contains(key));
         animatedLineX.keySet().removeIf(key -> !activeEffects.contains(key));
         animatedY.keySet().removeIf(key -> !activeEffects.contains(key));
         cachedEffects.keySet().removeIf(key -> !activeEffects.contains(key)
               && (animatedAlphas.get(key) == null || animatedAlphas.get(key).get() <= 0.01F));
         animatedAlphas.keySet().removeIf(key -> {
            Animation2 anim = animatedAlphas.get(key);
            return anim == null || anim.get() <= 0.01F && !activeEffects.contains(key);
         });
         boundsWidth = Math.max(maxRenderedWidth, boundsWidth);
         boundsHeight = Math.max(offset, boundsHeight);
         HudEditor.registerRect(x, y, boundsWidth, boundsHeight);
         DraggableManager.getInstance().endDrag(dragSession);
      }
   }

   private static Identifier getEffectTexture(RegistryEntry<StatusEffect> effect) {
      return effect.getKey().<Identifier>map(RegistryKey::getValue).map(id -> id.withPrefixedPath("mob_effect/"))
            .orElseGet(MissingSprite::getMissingSpriteId);
   }
}
