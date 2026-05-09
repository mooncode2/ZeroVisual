package ru.zero.ui.gui.widget.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.ui.Colors;
import ru.zero.util.render.animation.AnimationSystem;
import ru.zero.util.render.animation.Easings;
import ru.zero.util.render.animation.SpringConfig;
import ru.zero.util.render.animation.SpringFloatAnimator;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.text.FontRegistry;
import ru.zero.util.render.utils.Color;

@Environment(EnvType.CLIENT)
public final class ComboWidget implements SettingWidget {
   private static final float WIDGET_HEIGHT = 100.0F;
   private static final float LABEL_TEXT_SIZE = 18.0F;
   private static final float LABEL_LEFT_MARGIN = 18.0F;
   private static final float LABEL_TOP_MARGIN = 17.0F;
   private static final float FIELD_TOP_GAP = 8.0F;
   private static final float FIELD_WIDTH = 298.0F;
   private static final float FIELD_HEIGHT = 38.0F;
   private static final float FIELD_CORNER_RADIUS = 6.0F;
   private static final float FIELD_LEFT_MARGIN = 18.0F;
   private static final int FIELD_BACKGROUND_COLOR = -14408668;
   private static final float VALUE_TEXT_SIZE = 16.0F;
   private static final float VALUE_TEXT_LEFT_PADDING = 16.0F;
   private static final float VALUE_TEXT_BASELINE_OFFSET = 6.0F;
   private static final int VALUE_TEXT_DEFAULT_COLOR = -7829368;
   private static final int VALUE_TEXT_HOVER_COLOR = -1;
   private static final float ARROW_SECTION_WIDTH = 40.0F;
   private static final float ARROW_ICON_SIZE = 24.0F;
   private static final int ARROW_ICON_CODEPOINT = 58131;
   private static final SpringConfig HOVER_SPRING = SpringConfig.of(1.4F, 0.7F);
   private static final SpringConfig OPEN_SPRING = SpringConfig.of(2.1F, 0.55F);
   private static final float POPUP_ITEM_HEIGHT = 38.0F;
   private static final int POPUP_BACKGROUND_COLOR = -14408668;
   private static final int POPUP_HOVER_COLOR = -13750738;
   private static final float CHECK_ICON_SIZE = 18.0F;
   private static final float CHECK_ICON_BASELINE_OFFSET = 6.0F;
   private static final int CHECK_ICON_COLOR = -1;
   private static final float TEXT_ALIGNMENT_EPSILON = 1.0E-4F;
   private static final float OPEN_EPSILON = 0.001F;
   private final Module module;
   private final ModeSetting setting;
   private final PopupOpenContext popupContext;
   private final SettingValueAccessor<String> valueAccessor;
   private final String labelOverride;
   private final List<String> options;
   private final SpringFloatAnimator highlightAnimator;
   private final SpringFloatAnimator openAnimator;
   private final List<SpringFloatAnimator> optionHoverAnimators;
   private static float ARROW_BASELINE_OFFSET_CACHE = Float.NaN;
   private ComboWidget.Rect widgetBounds = ComboWidget.Rect.EMPTY;
   private ComboWidget.Rect fieldBounds = ComboWidget.Rect.EMPTY;
   private ComboWidget.Rect arrowBounds = ComboWidget.Rect.EMPTY;
   private ComboWidget.Rect popupBounds = ComboWidget.Rect.EMPTY;
   private final List<ComboWidget.Rect> optionBounds = new ArrayList<>();
   private float labelX = 0.0F;
   private float labelBaseline = 0.0F;
   private boolean hovered = false;
   private boolean open = false;
   private int hoveredOption = -1;

   public ComboWidget(Module module, PopupOpenContext popupContext, ModeSetting setting, SettingValueAccessor<String> valueAccessor) {
      this(module, popupContext, setting, valueAccessor, null);
   }

   public ComboWidget(Module module, PopupOpenContext popupContext, ModeSetting setting, SettingValueAccessor<String> valueAccessor, String labelOverride) {
      this.module = Objects.requireNonNull(module, "module");
      this.popupContext = Objects.requireNonNull(popupContext, "popupContext");
      this.setting = Objects.requireNonNull(setting, "setting");
      this.valueAccessor = Objects.requireNonNull(valueAccessor, "valueAccessor");
      this.labelOverride = normalizeLabel(labelOverride);
      this.options = new ArrayList<>(setting.modes != null ? setting.modes : List.of());
      this.highlightAnimator = new SpringFloatAnimator(AnimationSystem.getInstance(), HOVER_SPRING, 0.0F, 0.0F, 1.0F, 5.0E-4F, 5.0E-4F);
      this.highlightAnimator.setOutputTransform(Easings.SMOOTH_STEP);
      this.openAnimator = new SpringFloatAnimator(AnimationSystem.getInstance(), OPEN_SPRING, 0.0F, 0.0F, 1.0F, 5.0E-4F, 5.0E-4F);
      this.openAnimator.setOutputTransform(Easings.SMOOTH_STEP);
      this.optionHoverAnimators = new ArrayList<>();
      this.initializeOptionAnimators();
   }

   @Override
   public void updateState() {
      List<String> currentOptions = this.setting.modes != null ? this.setting.modes : List.of();
      if (currentOptions.size() != this.options.size() || !this.options.equals(currentOptions)) {
         this.options.clear();
         this.options.addAll(currentOptions);
         this.initializeOptionAnimators();
         this.recomputePopupBounds();
      }

      this.highlightAnimator.setTarget(this.open ? 1.0F : (this.open ? 0.0F : (this.hovered ? 0.5F : 0.0F)));
      this.openAnimator.setTarget(this.open ? 1.0F : 0.0F);

      for (int i = 0; i < this.optionHoverAnimators.size(); i++) {
         float target = this.open && i == this.hoveredOption ? 1.0F : 0.0F;
         this.optionHoverAnimators.get(i).setTarget(target);
      }

      if (!this.open) {
         this.hoveredOption = -1;
      }
   }

   @Override
   public void layout(float panelX, float top, float panelWidth) {
      this.widgetBounds = new ComboWidget.Rect(panelX, top, panelWidth, 100.0F);
      this.labelX = panelX + 18.0F;
      this.labelBaseline = top + 17.0F + 18.0F;
      float fieldX = panelX + 18.0F;
      float fieldY = this.labelBaseline + 8.0F;
      this.fieldBounds = new ComboWidget.Rect(fieldX, fieldY, 298.0F, 38.0F);
      this.arrowBounds = new ComboWidget.Rect(fieldX + 298.0F - 40.0F, fieldY, 40.0F, 38.0F);
      this.recomputePopupBounds();
   }

   @Override
   public float getHeight() {
      return 100.0F;
   }

   @Override
   public void render(Renderer2D renderer, float alphaMultiplier, float expansionProgress, float scrollOffset) {
      float expansion = clamp01(expansionProgress);
      float effectiveAlpha = alphaMultiplier * expansion;
      if (!(effectiveAlpha <= 1.0E-4F)) {
         float highlightProgress = clamp01(this.highlightAnimator.getValue());
         int labelBaseColor = applyAlpha(-7829368, effectiveAlpha);
         int labelHighlightColor = applyAlpha(-1, effectiveAlpha);
         int labelColor = Color.lerp(labelBaseColor, labelHighlightColor, highlightProgress);
         renderer.text(FontRegistry.INTER_SEMIBOLD, this.labelX, this.labelBaseline - 4.0F, 18.0F, this.resolveLabel(), labelColor, "l");
         int fieldColor = applyAlpha(-14408668, effectiveAlpha);
         renderer.rect(this.fieldBounds.x, this.fieldBounds.y, this.fieldBounds.width, this.fieldBounds.height, 6.0F, fieldColor);
         int arrowColor = applyAlpha(Colors.getClientPrimary(), effectiveAlpha);
         renderer.rect(this.arrowBounds.x, this.arrowBounds.y, this.arrowBounds.width, this.arrowBounds.height, 0.0F, 6.0F, 6.0F, 0.0F, arrowColor);
         float valueBaseline = this.fieldBounds.centerY() + 6.0F;
         float valueTextX = this.fieldBounds.x + 16.0F;
         String value = this.valueAccessor.get();
         if (value == null) {
            value = "";
         }

         int valueBaseColor = applyAlpha(-7829368, effectiveAlpha);
         int valueHighlightColor = applyAlpha(-1, effectiveAlpha);
         int valueColor = Color.lerp(valueBaseColor, valueHighlightColor, highlightProgress);
         renderer.text(FontRegistry.INTER_SEMIBOLD, valueTextX, valueBaseline, 16.0F, value, valueColor, "l");
         float arrowRotation = clamp01(this.openAnimator.getValue()) * 180.0F;
         float centerX = this.arrowBounds.centerX();
         float centerY = this.arrowBounds.centerY();
         float baselineOffset = arrowBaselineOffset();
         float baselineY = centerY + baselineOffset;
         renderer.pushTranslation(centerX, baselineY);
         renderer.pushTranslation(0.0F, -baselineOffset);
         renderer.pushRotation(arrowRotation);
         renderer.pushTranslation(0.0F, baselineOffset);
         renderer.pushTranslation(-centerX, -baselineY);

         try {
            renderer.text(FontRegistry.ICONS, centerX, baselineY, 24.0F, "\ue313", applyAlpha(-1, effectiveAlpha), "c");
         } finally {
            renderer.popTransform();
            renderer.popTransform();
            renderer.popRotation();
            renderer.popTransform();
            renderer.popTransform();
         }
      }
   }

   @Override
   public void renderOverlay(Renderer2D renderer, float alphaMultiplier, float expansionProgress) {
      float openProgress = clamp01(this.openAnimator.getValue());
      if (!(openProgress <= 0.001F)) {
         if (!this.options.isEmpty() && !(this.popupBounds.width <= 0.0F) && !(this.popupBounds.height <= 0.0F)) {
            float expansion = clamp01(expansionProgress);
            float effectiveAlpha = alphaMultiplier * expansion * openProgress;
            if (!(effectiveAlpha <= 1.0E-4F)) {
               int popupColor = -14408668;
               renderer.pushScale(1.0F, openProgress, this.popupBounds.x, this.popupBounds.y);

               try {
                  renderer.rect(this.popupBounds.x, this.popupBounds.y, this.popupBounds.width, this.popupBounds.height, 6.0F, 6.0F, 6.0F, 6.0F, popupColor);
                  String currentValue = this.valueAccessor.get();
                  if (currentValue == null) {
                     currentValue = "";
                  }

                  for (int i = 0; i < this.optionBounds.size(); i++) {
                     ComboWidget.Rect bounds = this.optionBounds.get(i);
                     String option = this.options.get(i);
                     boolean isSelected = Objects.equals(option, currentValue);
                     float hoverProgress = i < this.optionHoverAnimators.size() ? clamp01(this.optionHoverAnimators.get(i).getValue()) : 0.0F;
                     if (hoverProgress > 0.001F) {
                        int hoverColor = applyAlpha(-13750738, hoverProgress * effectiveAlpha);
                        float topLeft = i == 0 ? 6.0F : 0.0F;
                        float topRight = i == 0 ? 6.0F : 0.0F;
                        float bottomRight = i == this.optionBounds.size() - 1 ? 6.0F : 0.0F;
                        float bottomLeft = i == this.optionBounds.size() - 1 ? 6.0F : 0.0F;
                        renderer.rect(bounds.x, bounds.y, bounds.width, bounds.height, topLeft, topRight, bottomRight, bottomLeft, hoverColor);
                     }

                     float textX = bounds.x + 16.0F;
                     float textBaseline = bounds.centerY() + 6.0F;
                     int textBaseColor = applyAlpha(-7829368, effectiveAlpha);
                     int textHighlightColor = applyAlpha(-1, effectiveAlpha);
                     float textHighlightFactor;
                     if (isSelected) {
                        textHighlightFactor = 1.0F;
                     } else {
                        textHighlightFactor = hoverProgress * 0.7F;
                     }

                     int textColor = Color.lerp(textBaseColor, textHighlightColor, textHighlightFactor);
                     renderer.text(FontRegistry.INTER_SEMIBOLD, textX, textBaseline, 16.0F, option, textColor, "l");
                     if (Objects.equals(option, currentValue)) {
                        float iconX = bounds.x + bounds.width - 16.0F + 2.0F;
                        float iconY = bounds.centerY() + 6.0F + 3.0F;
                        renderer.text(FontRegistry.ICONS, iconX, iconY, 18.0F, "\ue5ca", applyAlpha(-1, effectiveAlpha), "r");
                     }
                  }
               } finally {
                  renderer.popScale();
               }
            }
         }
      }
   }

   @Override
   public boolean isBlockingInteractions() {
      return (this.open || this.openAnimator.getValue() > 0.001F) && this.popupBounds.width > 0.0F && this.popupBounds.height > 0.0F;
   }

   @Override
   public boolean handleOverlayClick(double mouseX, double mouseY, int button) {
      if (!this.isBlockingInteractions()) {
         return false;
      } else if (!this.open) {
         return false;
      } else if (button != 0) {
         this.closePopup();
         return true;
      } else if (this.popupBounds.contains(mouseX, mouseY)) {
         int index = this.indexAt(mouseY);
         if (index >= 0 && index < this.options.size()) {
            this.selectOption(index);
         }

         this.closePopup();
         return true;
      } else if (!this.fieldBounds.contains(mouseX, mouseY) && !this.arrowBounds.contains(mouseX, mouseY)) {
         this.closePopup();
         return true;
      } else {
         this.closePopup();
         return true;
      }
   }

   @Override
   public boolean handleMouseClick(double mouseX, double mouseY, int button) {
      boolean insidePrimaryRegion = this.fieldBounds.contains(mouseX, mouseY) || this.arrowBounds.contains(mouseX, mouseY);
      if (button == 2) {
         if (!insidePrimaryRegion) {
            return false;
         } else {
            this.closePopup();
            String valueObj = this.valueAccessor.get();
            String initialValue = valueObj != null ? valueObj.toString() : "";
            this.popupContext.openForSetting(this.module, this.setting, mouseX, mouseY, initialValue);
            return true;
         }
      } else if (button != 0) {
         return false;
      } else if (this.open) {
         return this.handleOverlayClick(mouseX, mouseY, button);
      } else if (insidePrimaryRegion) {
         this.openPopup();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean handleOverlayScroll(double mouseX, double mouseY, double horizontal, double vertical) {
      return this.isBlockingInteractions();
   }

   @Override
   public void updateHoverState(double mouseX, double mouseY) {
      boolean baseHover = this.fieldBounds.contains(mouseX, mouseY) || this.arrowBounds.contains(mouseX, mouseY);
      boolean popupHover = this.open && this.popupBounds.contains(mouseX, mouseY);
      this.hovered = this.open ? false : baseHover;
      if (this.open) {
         if (popupHover) {
            this.hoveredOption = this.indexAt(mouseY);
         } else {
            this.hoveredOption = -1;
         }
      }
   }

   @Override
   public void collapse() {
      this.closePopupImmediate();
   }

   @Override
   public Setting getSetting() {
      return this.setting;
   }

   @Override
   public boolean supportsBinding() {
      return true;
   }

   private void openPopup() {
      this.open = true;
      this.openAnimator.setTarget(1.0F);
      this.highlightAnimator.setTarget(1.0F);
   }

   private void closePopup() {
      this.open = false;
      this.openAnimator.setTarget(0.0F);
      this.hoveredOption = -1;
   }

   private void closePopupImmediate() {
      this.open = false;
      this.openAnimator.snapTo(0.0F);
      this.hoveredOption = -1;
   }

   private void selectOption(int index) {
      if (index >= 0 && index < this.options.size()) {
         String selected = this.options.get(index);
         String currentValue = this.valueAccessor.get();
         if (!Objects.equals(selected, currentValue)) {
            this.valueAccessor.set(selected);
         }
      }
   }

   private void recomputePopupBounds() {
      this.optionBounds.clear();
      if (this.options.isEmpty()) {
         this.popupBounds = ComboWidget.Rect.EMPTY;
      } else {
         float popupX = this.fieldBounds.x;
         float popupY = this.fieldBounds.y + this.fieldBounds.height + 6.0F;
         float popupWidth = this.fieldBounds.width;
         float popupHeight = 38.0F * this.options.size();
         this.popupBounds = new ComboWidget.Rect(popupX, popupY, popupWidth, popupHeight);
         float cursor = popupY;

         for (int i = 0; i < this.options.size(); i++) {
            this.optionBounds.add(new ComboWidget.Rect(popupX, cursor, popupWidth, 38.0F));
            cursor += 38.0F;
         }
      }
   }

   private int indexAt(double mouseY) {
      if (!(mouseY < this.popupBounds.y) && !(mouseY > this.popupBounds.y + this.popupBounds.height)) {
         double relative = mouseY - this.popupBounds.y;
         if (relative < 0.0) {
            return -1;
         } else {
            int index = (int)(relative / 38.0);
            return index >= 0 && index < this.options.size() ? index : -1;
         }
      } else {
         return -1;
      }
   }

   private String resolveLabel() {
      return this.labelOverride != null ? this.labelOverride : this.setting.name;
   }

   private static String normalizeLabel(String label) {
      if (label == null) {
         return null;
      } else {
         String trimmed = label.trim();
         return trimmed.isEmpty() ? null : trimmed;
      }
   }

   private static float clamp01(float value) {
      if (value <= 0.0F) {
         return 0.0F;
      } else {
         return value >= 1.0F ? 1.0F : value;
      }
   }

   private void initializeOptionAnimators() {
      this.optionHoverAnimators.clear();

      for (int i = 0; i < this.options.size(); i++) {
         SpringFloatAnimator animator = new SpringFloatAnimator(AnimationSystem.getInstance(), HOVER_SPRING, 0.0F, 0.0F, 1.0F, 5.0E-4F, 5.0E-4F);
         animator.setOutputTransform(Easings.SMOOTH_STEP);
         this.optionHoverAnimators.add(animator);
      }
   }

   private static float arrowBaselineOffset() {
      if (Float.isNaN(ARROW_BASELINE_OFFSET_CACHE)) {
         float offset = FontRegistry.centeredBaselineOffset(FontRegistry.ICONS, 58131, 24.0F);
         ARROW_BASELINE_OFFSET_CACHE = offset;
      }

      return ARROW_BASELINE_OFFSET_CACHE;
   }

   private static int applyAlpha(int rgba, float alpha) {
      int originalAlpha = rgba >>> 24 & 0xFF;
      int modulatedAlpha = Math.round(originalAlpha * alpha);
      int rgb = rgba & 16777215;
      return modulatedAlpha << 24 | rgb;
   }

   @Environment(EnvType.CLIENT)
   private record Rect(float x, float y, float width, float height) {
      private static final ComboWidget.Rect EMPTY = new ComboWidget.Rect(0.0F, 0.0F, 0.0F, 0.0F);

      private boolean contains(double px, double py) {
         return px >= this.x && px <= this.x + this.width && py >= this.y && py <= this.y + this.height;
      }

      private float centerX() {
         return this.x + this.width * 0.5F;
      }

      private float centerY() {
         return this.y + this.height * 0.5F;
      }
   }
}
