package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import ru.zero.Zero;
import ru.zero.compat.LunarCompat;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventScreen;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.color.ThemeColorUtil;
import ru.zero.util.player.CrosshairTargetUtil;
import ru.zero.util.render.core.Renderer2D;

@IModule(
   name = "Crosshair",
   description = "Настраиваемый прицел",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class Crosshair extends Module {
   public static final ModeSetting style = new ModeSetting("Стиль", "Крест", "Крест", "Точка", "Круг");
   public static final SliderSetting length = new SliderSetting("Длина", 6.0F, 2.0F, 20.0F, 0.5F, false);
   public static final SliderSetting thickness = new SliderSetting("Толщина", 1.5F, 0.5F, 5.0F, 0.1F, false);
   public static final SliderSetting gap = new SliderSetting("Зазор", 3.0F, 0.0F, 12.0F, 0.5F, false);
   public static final SliderSetting dotSize = new SliderSetting("Размер точки", 2.0F, 1.0F, 6.0F, 0.5F, false)
      .hidden(() -> !style.is("Точка"));
   public static final SliderSetting circleRadius = new SliderSetting("Радиус круга", 5.0F, 2.0F, 16.0F, 0.5F, false)
      .hidden(() -> !style.is("Круг"));
   public static final BooleanSetting syncWithTheme = new BooleanSetting("Синхронизировать с темой", true);
   public static final HueSetting color = new HueSetting("Цвет", 0.0F).hidden(() -> syncWithTheme.get());
   public static final BooleanSetting entityHighlight = new BooleanSetting("Красный на энтити", true);
   public static final HueSetting entityColor = new HueSetting("Цвет на энтити", 0.0F, 1.0F, 1.0F)
      .hidden(() -> !entityHighlight.get());

   public Crosshair() {
      this.addSettings(
         new Setting[] { style, length, thickness, gap, dotSize, circleRadius, syncWithTheme, color, entityHighlight, entityColor }
      );
   }

   /** Lunar: drawn from {@code InGameHud.renderCrosshair} RETURN (same layer as vanilla HUD). */
   public static void renderInHudPass(DrawContext context) {
      if (context == null || Zero.get == null || Zero.get.manager == null) {
         return;
      }

      Crosshair module = Zero.get.manager.get(Crosshair.class);
      if (module == null || !module.enable || module.mc.player == null) {
         return;
      }

      module.drawCrosshair(context);
   }

   @EventInit
   public void onRender(EventScreen event) {
      if (!this.enable || mc.player == null || event == null) {
         return;
      }

      if (LunarCompat.isLunarClient()) {
         return;
      }

      Renderer2D r2 = event.renderer();
      if (r2 == null) {
         return;
      }

      this.drawCrosshair(r2, event.viewportWidth(), event.viewportHeight());
   }

   private int resolveDrawColor() {
      int drawColor = ColorUtil.replAlpha(ThemeColorUtil.resolveRgb(syncWithTheme, color), 255);
      if (entityHighlight.get()) {
         LivingEntity target = CrosshairTargetUtil.getLivingCrosshairTarget();
         if (target != null) {
            drawColor = ColorUtil.replAlpha(entityColor.getRGB(), 255);
         }
      }

      return drawColor;
   }

   /** Lunar: draw in the same GUI layer as vanilla HUD via {@link DrawContext}. */
   private void drawCrosshair(DrawContext context) {
      if (mc.getWindow() == null) {
         return;
      }

      int drawColor = this.resolveDrawColor();
      float centerX = mc.getWindow().getScaledWidth() * 0.5F;
      float centerY = mc.getWindow().getScaledHeight() * 0.5F;
      float len = length.get();
      float thick = Math.max(1.0F, thickness.get());
      float g = gap.get();

      switch (style.get()) {
         case "Точка" -> {
            float d = dotSize.get();
            context.fill((int) (centerX - d), (int) (centerY - d), (int) (centerX + d), (int) (centerY + d), drawColor);
         }
         case "Круг" -> {
            Renderer2D r2 = ru.zero.Zero.getRenderer();
            if (r2 != null) {
               int fbW = mc.getWindow().getFramebufferWidth();
               int fbH = mc.getWindow().getFramebufferHeight();
               this.drawCrosshair(r2, fbW, fbH);
            }
         }
         default -> {
            int t = (int) thick;
            int l = (int) len;
            int gi = (int) g;
            int cx = (int) centerX;
            int cy = (int) centerY;
            context.fill(cx - t / 2, cy - gi - l, cx + (t + 1) / 2, cy - gi, drawColor);
            context.fill(cx - t / 2, cy + gi, cx + (t + 1) / 2, cy + gi + l, drawColor);
            context.fill(cx - gi - l, cy - t / 2, cx - gi, cy + (t + 1) / 2, drawColor);
            context.fill(cx + gi, cy - t / 2, cx + gi + l, cy + (t + 1) / 2, drawColor);
         }
      }
   }

   private void drawCrosshair(Renderer2D r2, int viewportWidth, int viewportHeight) {
      float centerX = viewportWidth * 0.5F;
      float centerY = viewportHeight * 0.5F;
      int drawColor = this.resolveDrawColor();
      float len = length.get();
      float thick = thickness.get();
      float g = gap.get();

      switch (style.get()) {
         case "Точка" -> r2.rect(centerX - dotSize.get(), centerY - dotSize.get(), dotSize.get() * 2.0F, dotSize.get() * 2.0F, drawColor);
         case "Круг" -> r2.circle(centerX, centerY, circleRadius.get(), 0.0F, 1.0F, drawColor);
         default -> {
            r2.rect(centerX - thick * 0.5F, centerY - g - len, thick, len, drawColor);
            r2.rect(centerX - thick * 0.5F, centerY + g, thick, len, drawColor);
            r2.rect(centerX - g - len, centerY - thick * 0.5F, len, thick, drawColor);
            r2.rect(centerX + g, centerY - thick * 0.5F, len, thick, drawColor);
         }
      }
   }
}
