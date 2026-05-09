package ru.zero.module.impl.visuals;

import java.util.ArrayList;
import java.util.List;
import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventScreen;
import ru.zero.event.input.MouseButtonEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.MultiBooleanSetting;
import ru.zero.module.impl.utils.Optimizer;
import ru.zero.module.impl.visuals.ClickGUI;
import ru.zero.module.impl.visuals.HUD.HotBarHUD;
import ru.zero.module.impl.visuals.HUD.HudEditor;
import ru.zero.module.impl.visuals.HUD.InformationHUD;
import ru.zero.module.impl.visuals.HUD.KeyBindHUD;
import ru.zero.module.impl.visuals.HUD.PotionsHUD;
import ru.zero.module.impl.visuals.HUD.TargetHUD;
import ru.zero.module.impl.visuals.HUD.WaterMark;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.animation.util.Animation;
import ru.zero.util.render.animation.util.Easings;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.text.FontRegistry;

@IModule(
   name = "Hud",
   description = " ",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class Hud extends Module {
   public static Animation animC = new Animation();
   public static MultiBooleanSetting element = new MultiBooleanSetting(
      "Элементы интерфейса",
      new BooleanSetting("Ватер марк", true),
      new BooleanSetting("Информация", true),
      new BooleanSetting("Таргет Худ", true),
      new BooleanSetting("Уведомления", true),
      new BooleanSetting("Список биндов", true),
      new BooleanSetting("Список зелий", true),
      new BooleanSetting("Предметы на бинде", true),
      new BooleanSetting("Хот бар", true)
   );
   public static MultiBooleanSetting notify = new MultiBooleanSetting(
      "Настройка уведомлений",
      new BooleanSetting("Модули", true),
      new BooleanSetting("Низкое здоровье", true),
      new BooleanSetting("Низкая прочность брони", true)
   );
   public static BooleanSetting blur = new BooleanSetting("Блюр (Размытие)", true);
   private final List<Hud.Notification> notifications = new ArrayList<>();

   public static enum NotificationType {
      SUCCESS("on"),
      WARNING("warn"),
      INFO("cfg"),
      ERROR("off"),
      CONFIG("cfg");

      private final String legacyIcon;

      private NotificationType(String legacyIcon) {
         this.legacyIcon = legacyIcon;
      }

      public String legacyIcon() {
         return this.legacyIcon;
      }
   }

   public Hud() {
      this.addSettings(new Setting[]{element, notify, blur});
   }

   @EventInit
   public void onRender(EventScreen e) {
      if (mc.player != null && mc.world != null) {
         Renderer2D r2 = e.renderer();
         if (r2 != null) {
            HotBarHUD.tick();
            if (element.get("Уведомления")) {
               this.renderNotifications(r2);
            }

            if (element.get("Ватер марк")) {
               HudEditor.renderElement("watermark", r2, () -> WaterMark.waterMark(r2));
            }

            if (element.get("Информация")) {
               HudEditor.renderElement("information", r2, () -> InformationHUD.information(r2));
            }

            if (element.get("Таргет Худ")) {
               HudEditor.renderElement("targetHUD", r2, () -> TargetHUD.targetHUD(r2, e.drawContext()));
            }

            if (element.get("Список зелий")) {
               HudEditor.renderElement("potions", r2, () -> PotionsHUD.potions(r2, e.drawContext()));
            }

            if (element.get("Хот бар")) {
               HudEditor.renderElement("hotbar", r2, () -> HotBarHUD.hotbar(r2, e.drawContext()));
            }

            if (element.get("Список биндов")) {
               HudEditor.renderElement("keybinds", r2, () -> KeyBindHUD.keybind(r2));
            }

            HudEditor.renderPopup(r2);
         }
      }
   }

   @EventInit
   public void onMouseButton(MouseButtonEvent event) {
      HudEditor.onMouseButton(event);
   }

   public void showNotification(String icon, String text, long duration) {
      Hud.Notification notification = new Hud.Notification(icon, text, duration);
      this.notifications.add(notification);
   }

   public void showNotification(String icon, String text, long duration, int iconColor) {
      Hud.Notification notification = new Hud.Notification(icon, text, duration, iconColor);
      this.notifications.add(notification);
   }

   public void showNotification(String icon, String text) {
      this.showNotification(icon, text, 3000L);
   }

   public void showNotification(String text, Hud.NotificationType type, long duration) {
      String icon = type != null ? type.legacyIcon() : "cfg";
      this.showNotification(icon, text, duration);
   }

   public void showNotification(String text, Hud.NotificationType type) {
      this.showNotification(text, type, 3000L);
   }

   public void renderNotifications(Renderer2D matrix) {
      this.notifications.removeIf(notificationx -> {
         notificationx.animation.update();
         notificationx.yAnimation.update();
         return notificationx.isExpired() && notificationx.animation.getValue() <= 0.01;
      });
      if (!this.notifications.isEmpty()) {
         float margin = 4.0F;
         float notificationHeight = 40.0F;
         float spacing = 6.0F;
         float screenCenterY = mc.getWindow().getHeight() / 2.0F + 140.0F;
         float currentY = screenCenterY;

         int maxNotifications = Optimizer.getMaxNotifications();
         int rendered = 0;
         for (int i = this.notifications.size() - 1; i >= 0 && rendered < maxNotifications; i--) {
            Hud.Notification notification = this.notifications.get(i);
            boolean shouldShow = !notification.isExpired();
            notification.animation.run(shouldShow ? 1.0 : 0.0, 0.16F, Easings.QUAD_OUT, false);
            float animValue = notification.animation.get();
            if (!(animValue <= 0.01F)) {
               float iconWidth = 8.0F;
               float textWidth = matrix.measureText(FontRegistry.INTER_MEDIUM, notification.text, 26.0F).width;
               float notificationWidth = margin * 3.0F + iconWidth + textWidth + 8.0F + 30.0F;
               float screenCenterX = mc.getWindow().getWidth() / 2.0F;
               float targetX = screenCenterX - notificationWidth / 2.0F;
               notification.yAnimation.run(currentY, 0.1F, Easings.QUAD_OUT, false);
               float scale = 0.9F + 0.1F * animValue;
               matrix.pushTranslation(targetX + notificationWidth / 2.0F, currentY + notificationHeight / 2.0F);
               matrix.pushScale(scale, scale, 0.0F);
               matrix.pushTranslation(-(targetX + notificationWidth / 2.0F), -(currentY + notificationHeight / 2.0F));
               drawClientRect(matrix, targetX - 4.0F, currentY, notificationWidth + 8.0F, notificationHeight, 13.0F, animValue, 1.0F);
               matrix.rect(
                  targetX + 35.0F,
                  currentY + notificationHeight / 2.0F + 3.0F - 10.5F,
                  2.0F,
                  11.22F,
                  4.0F,
                  ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), animValue * 0.2F)
               );
               int iconColor = notification.iconColor == -1
                  ? ColorUtil.replAlpha(ColorUtil.fade(), animValue)
                  : ColorUtil.replAlpha(notification.iconColor, animValue);
               boolean vanillaStyle = ClickGUI.isVanillaStyle();
               if (notification.icon.contains("on")) {
                  matrix.text(
                     vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS,
                     targetX + margin + margin + 2.0F,
                     currentY + notificationHeight / 2.0F + 7.5F,
                     37.0F,
                     vanillaStyle ? "+" : "L",
                     ColorUtil.replAlpha(iconColor, animValue)
                  );
               } else if (notification.icon.contains("off")) {
                  matrix.text(
                     vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS,
                     targetX + margin + margin + 2.0F,
                     currentY + notificationHeight / 2.0F + 7.5F,
                     37.0F,
                     vanillaStyle ? "-" : "J",
                     ColorUtil.replAlpha(iconColor, animValue)
                  );
               } else if (notification.icon.contains("warn")) {
                  matrix.text(
                     vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS,
                     targetX + margin + margin + 2.0F,
                     currentY + notificationHeight / 2.0F + 5.5F,
                     28.0F,
                     vanillaStyle ? "!" : "F",
                     ColorUtil.replAlpha(iconColor, animValue)
                  );
               } else if (notification.icon.contains("cfg")) {
                  matrix.text(
                     vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS,
                     targetX + margin + margin + 2.0F,
                     currentY + notificationHeight / 2.0F + 5.5F,
                     28.0F,
                     vanillaStyle ? "*" : "G",
                     ColorUtil.replAlpha(iconColor, animValue)
                  );
               } else {
                  matrix.text(
                     vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS,
                     targetX + margin + margin + 2.0F,
                     currentY + notificationHeight / 2.0F + 7.5F,
                     37.0F,
                     notification.icon,
                     ColorUtil.replAlpha(iconColor, animValue)
                  );
               }

               matrix.text(
                  FontRegistry.INTER_MEDIUM,
                  targetX + notificationWidth / 2.0F + margin - textWidth / 2.0F + 12.0F,
                  currentY + notificationHeight / 2.0F + 3.3F,
                  26.0F,
                  notification.text,
                  ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), animValue)
               );
               matrix.popTransform();
               matrix.popClipRect();
               matrix.popRotation();
               matrix.popScale();
               currentY += (notificationHeight + spacing) * animValue;
               rendered++;
            }
         }
      }
   }

   public static void drawClientRect(Renderer2D r2, float x, float y, float w, float h, float radius, float alpha, float thickness) {
      if (ClickGUI.isVanillaStyle()) {
         float vanillaRadius = 1.25F;
         int outline = ColorUtil.replAlpha(new java.awt.Color(96, 96, 96).getRGB(), alpha);
         int background = ColorUtil.replAlpha(new java.awt.Color(45, 45, 45).getRGB(), alpha * 0.9F);
         r2.rectOutline(x - 1.0F, y - 1.0F, w + 2.0F, h + 2.0F, vanillaRadius, outline, 1.0F);
         r2.rect(x, y, w, h, vanillaRadius, background);
      } else {
         r2.shadow(x, y, w, h, radius, 8.6F, 0.5F, ColorUtil.getColor(0, alpha * 0.15F));
         if (!blur.get()) {
            float blurRadius = Optimizer.optimizeHudBlur(23.0F);
            if (alpha < 0.12F) {
               blurRadius = Math.min(blurRadius, 8.0F);
            }

            r2.prepareBlur(blurRadius);
            r2.blur(x, y, w, h, radius, alpha);
         }

         r2.rectOutline(
            x - 1.0F,
            y - 1.0F,
            w + 2.0F,
            h + 2.0F,
            radius,
            ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), alpha * 0.1F),
            thickness
         );
         r2.rect(x, y, w, h, radius, ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), alpha * 0.7F));
      }
   }

   @Environment(EnvType.CLIENT)
   public static class Notification {
      private String icon;
      private String text;
      private long createTime;
      private long duration;
      private Animation animation = new Animation();
      private Animation yAnimation = new Animation();
      private int iconColor = -1;

      public Notification(String icon, String text, long duration) {
         this.icon = icon;
         this.text = text;
         this.duration = duration;
         this.createTime = System.currentTimeMillis();
      }

      public Notification(String icon, String text, long duration, int iconColor) {
         this.icon = icon;
         this.text = text;
         this.duration = duration;
         this.createTime = System.currentTimeMillis();
         this.iconColor = iconColor;
      }

      public boolean isExpired() {
         return System.currentTimeMillis() - this.createTime > this.duration;
      }

      public float getProgress() {
         return Math.min(1.0F, (float)(System.currentTimeMillis() - this.createTime) / (float)this.duration);
      }

      @Generated
      public String getIcon() {
         return this.icon;
      }

      @Generated
      public String getText() {
         return this.text;
      }

      @Generated
      public long getCreateTime() {
         return this.createTime;
      }

      @Generated
      public long getDuration() {
         return this.duration;
      }

      @Generated
      public Animation getAnimation() {
         return this.animation;
      }

      @Generated
      public Animation getYAnimation() {
         return this.yAnimation;
      }

      @Generated
      public int getIconColor() {
         return this.iconColor;
      }

      @Generated
      public void setIcon(String icon) {
         this.icon = icon;
      }

      @Generated
      public void setText(String text) {
         this.text = text;
      }

      @Generated
      public void setCreateTime(long createTime) {
         this.createTime = createTime;
      }

      @Generated
      public void setDuration(long duration) {
         this.duration = duration;
      }

      @Generated
      public void setAnimation(Animation animation) {
         this.animation = animation;
      }

      @Generated
      public void setYAnimation(Animation yAnimation) {
         this.yAnimation = yAnimation;
      }

      @Generated
      public void setIconColor(int iconColor) {
         this.iconColor = iconColor;
      }

      @Generated
      @Override
      public boolean equals(Object o) {
         if (o == this) {
            return true;
         } else if (!(o instanceof Hud.Notification other)) {
            return false;
         } else if (!other.canEqual(this)) {
            return false;
         } else if (this.getCreateTime() != other.getCreateTime()) {
            return false;
         } else if (this.getDuration() != other.getDuration()) {
            return false;
         } else if (this.getIconColor() != other.getIconColor()) {
            return false;
         } else {
            Object this$icon = this.getIcon();
            Object other$icon = other.getIcon();
            if (this$icon == null ? other$icon == null : this$icon.equals(other$icon)) {
               Object this$text = this.getText();
               Object other$text = other.getText();
               if (this$text == null ? other$text == null : this$text.equals(other$text)) {
                  Object this$animation = this.getAnimation();
                  Object other$animation = other.getAnimation();
                  if (this$animation == null ? other$animation == null : this$animation.equals(other$animation)) {
                     Object this$yAnimation = this.getYAnimation();
                     Object other$yAnimation = other.getYAnimation();
                     return this$yAnimation == null ? other$yAnimation == null : this$yAnimation.equals(other$yAnimation);
                  } else {
                     return false;
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         }
      }

      @Generated
      protected boolean canEqual(Object other) {
         return other instanceof Hud.Notification;
      }

      @Generated
      @Override
      public int hashCode() {
         int PRIME = 59;
         int result = 1;
         long $createTime = this.getCreateTime();
         result = result * 59 + (int)($createTime >>> 32 ^ $createTime);
         long $duration = this.getDuration();
         result = result * 59 + (int)($duration >>> 32 ^ $duration);
         result = result * 59 + this.getIconColor();
         Object $icon = this.getIcon();
         result = result * 59 + ($icon == null ? 43 : $icon.hashCode());
         Object $text = this.getText();
         result = result * 59 + ($text == null ? 43 : $text.hashCode());
         Object $animation = this.getAnimation();
         result = result * 59 + ($animation == null ? 43 : $animation.hashCode());
         Object $yAnimation = this.getYAnimation();
         return result * 59 + ($yAnimation == null ? 43 : $yAnimation.hashCode());
      }

      @Generated
      @Override
      public String toString() {
         return "Hud.Notification(icon="
            + this.getIcon()
            + ", text="
            + this.getText()
            + ", createTime="
            + this.getCreateTime()
            + ", duration="
            + this.getDuration()
            + ", animation="
            + this.getAnimation()
            + ", yAnimation="
            + this.getYAnimation()
            + ", iconColor="
            + this.getIconColor()
            + ")";
      }
   }
}
