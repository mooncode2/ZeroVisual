package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
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
   public static final HueSetting color = new HueSetting("Цвет", 0.0F);
   public static final BooleanSetting entityHighlight = new BooleanSetting("Красный на энтити", true);
   public static final HueSetting entityColor = new HueSetting("Цвет на энтити", 0.0F, 1.0F, 1.0F)
      .hidden(() -> !entityHighlight.get());

   public Crosshair() {
      this.addSettings(
         new Setting[] { style, length, thickness, gap, dotSize, circleRadius, color, entityHighlight, entityColor }
      );
   }

   @EventInit
   public void onRender(EventScreen event) {
      if (!this.enable || mc.player == null || event == null) {
         return;
      }

      Renderer2D r2 = event.renderer();
      if (r2 == null) {
         return;
      }

      float centerX = event.viewportWidth() * 0.5F;
      float centerY = event.viewportHeight() * 0.5F;

      int drawColor = ColorUtil.replAlpha(color.getRGB(), 255);
      if (entityHighlight.get()) {
         LivingEntity target = CrosshairTargetUtil.getLivingCrosshairTarget();
         if (target != null) {
            drawColor = ColorUtil.replAlpha(entityColor.getRGB(), 255);
         }
      }

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
