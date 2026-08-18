package ru.zero.module.impl.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.render.utils.Color;

@IModule(
   name = "MaceHelper",
   description = "Подсвечивает булаву: цвет готовной при наведении на сущность, цвет неготовной в остальных случаях",
   category = Category.Utils,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class MaceHelper extends Module {

   // Цвет готовной (по умолчанию зеленый) — применяется, когда булава наведена на сущность
   public static final HueSetting readyColor = new HueSetting("Цвет готовной", 0x00FF0A);
   // Цвет неготовной (по умолчанию красный) — применяется, когда булава не подходит под все условия
   public static final HueSetting notReadyColor = new HueSetting("Цвет неготовной", 0xFF0000);

   // Настройки прозрачности
   public static final SliderSetting readyAlpha = new SliderSetting("Прозрачность готовной", 50.0F, 0.0F, 100.0F, 1.0F, true);
   public static final SliderSetting notReadyAlpha = new SliderSetting("Прозрачность неготовной", 50.0F, 0.0F, 100.0F, 1.0F, true);

   private static final MinecraftClient mc = MinecraftClient.getInstance();

   public MaceHelper() {
      this.addSettings(new Setting[] { readyColor, notReadyColor, readyAlpha, notReadyAlpha });
   }

   /**
    * Проверяет, что игрок держит булаву в основной руке.
    */
   private boolean isHoldingMace() {
      if (mc.player == null) {
         return false;
      }

      ItemStack mainHandStack = mc.player.getMainHandStack();
      return mainHandStack != null && mainHandStack.isOf(Items.MACE);
   }

   /**
    * Проверяет, наведена ли булава на живую сущность в пределах досягаемости.
    * Это условие "готовности" — когда выполнены все условия для цвета готовной.
    */
   private boolean isEntityTargeted() {
      if (mc.player == null) {
         return false;
      }

      if (mc.targetedEntity == null) {
         return false;
      }

      if (!isHoldingMace()) {
         return false;
      }

      Entity targetedEntity = mc.targetedEntity;
      if (!(targetedEntity instanceof LivingEntity)) {
         return false;
      }

      double reachDistance = 4.5;
      double distance = mc.player.squaredDistanceTo(targetedEntity.getX(), targetedEntity.getY(), targetedEntity.getZ());
      return distance <= reachDistance * reachDistance;
   }

   /**
    * Получает цвет для подсветки булавы.
    * Цвет готовной — когда все условия выполнены (наведена сущность).
    * Цвет неготовной (с альфой) — когда булава не подходит под все условия.
    */
   public int getMaceColor() {
      boolean isTargeted = isEntityTargeted();

      if (isTargeted) {
         return Color.getRGB(readyColor.getRGB(), readyAlpha.get() / 100.0F);
      }

      return Color.getRGB(notReadyColor.getRGB(), notReadyAlpha.get() / 100.0F);
   }

   /**
    * Проверяет, нужно ли применять подсветку к булаве.
    */
   public boolean shouldApplyHighlight() {
      return this.enable && isHoldingMace();
   }
}
