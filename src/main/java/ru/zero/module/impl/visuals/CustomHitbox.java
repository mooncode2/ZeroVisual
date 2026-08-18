package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import ru.zero.Zero;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.module.api.setting.impl.MultiBooleanSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.color.ThemeColorUtil;

@IModule(
   name = "Custom Hitbox",
   description = "Заменяет хитбоксы F3+B на настраиваемые",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class CustomHitbox extends Module {
   public static final MultiBooleanSetting targets = new MultiBooleanSetting(
      "Отображать",
      new BooleanSetting("Игроки", true),
      new BooleanSetting("Мобы", false)
   );
   public static final BooleanSetting syncWithTheme = new BooleanSetting("Синхронизировать с темой", true);
   public static final HueSetting color = new HueSetting("Цвет", 120.0F).hidden(() -> syncWithTheme.get());
   public static final SliderSetting lineWidth = new SliderSetting("Толщина линий", 1.5F, 0.5F, 4.0F, 0.1F, false);
   public static final BooleanSetting showEyeLine = new BooleanSetting("Линия головы", true);
   public static final BooleanSetting showLookVector = new BooleanSetting("Направление взгляда", true);
   public static final SliderSetting lookLength = new SliderSetting("Длина стрелки", 1.2F, 0.3F, 3.0F, 0.1F, false)
      .hidden(() -> !showLookVector.get());

   public CustomHitbox() {
      this.addSettings(new Setting[] { targets, syncWithTheme, color, lineWidth, showEyeLine, showLookVector, lookLength });
   }

   public static int resolveColor() {
      return ThemeColorUtil.resolveRgb(syncWithTheme, color);
   }

   public static boolean isActive() {
      CustomHitbox module = getModule();
      return module != null && module.enable;
   }

   public static CustomHitbox getModule() {
      if (Zero.get == null || Zero.get.manager == null) {
         return null;
      }

      return Zero.get.manager.get(CustomHitbox.class);
   }

   public static boolean shouldRenderEntity(Entity entity) {
      if (entity == null || !entity.isAlive()) {
         return false;
      }

      if (entity instanceof PlayerEntity) {
         return targets.get("Игроки");
      }

      return entity instanceof LivingEntity && targets.get("Мобы");
   }
}
