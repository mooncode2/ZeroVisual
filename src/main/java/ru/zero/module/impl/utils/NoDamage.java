package ru.zero.module.impl.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.Priority;
import ru.zero.event.player.AttackEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.MultiBooleanSetting;

@IModule(
   name = "NoDamage",
   description = "Отменяет урон по выбранным целям",
   category = Category.Utils,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class NoDamage extends Module {
   public static MultiBooleanSetting targets = new MultiBooleanSetting(
      "Цели",
      new BooleanSetting("Друзья", true),
      new BooleanSetting("Жители", true),
      new BooleanSetting("Аксолотли", true));

   public NoDamage() {
      this.addSettings(new Setting[] { targets });
   }

   @EventInit(Priority.HIGHEST)
   public void onAttack(AttackEvent event) {
      if (!this.enable) {
         return;
      }

      Entity target = event.getTarget();
      if (target == null) {
         return;
      }

      if (this.isProtected(target)) {
         event.cancel();
      }
   }

   private boolean isProtected(Entity target) {
      if (targets.get("Аксолотли") && target instanceof AxolotlEntity) {
         return true;
      }

      if (targets.get("Жители") && isVillager(target)) {
         return true;
      }

      return targets.get("Друзья") && isFriend(target);
   }

   private static boolean isVillager(Entity target) {
      return target instanceof VillagerEntity
            || target instanceof WanderingTraderEntity
            || target instanceof MerchantEntity;
   }

   private static boolean isFriend(Entity target) {
      if (!(target instanceof PlayerEntity)) {
         return false;
      }

      if (Zero.get == null || Zero.get.friendManager == null) {
         return false;
      }

      String name = resolveFriendName(target);
      return name != null && Zero.get.friendManager.isFriend(name);
   }

   private static String resolveFriendName(Entity entity) {
      if (entity instanceof AbstractClientPlayerEntity client) {
         return client.getNameForScoreboard();
      }

      if (entity instanceof PlayerEntity player) {
         return player.getName().getString();
      }

      return null;
   }
}
