package ru.zero.module.impl.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.Priority;
import ru.zero.event.player.AttackEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;

@IModule(
   name = "NoFriendDamage",
   description = "Не наносит урон игрокам из списка друзей",
   category = Category.Utils,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class NoFriendDamage extends Module {
   @EventInit(Priority.HIGHEST)
   public void onAttack(AttackEvent event) {
      if (!this.enable || Zero.get == null || Zero.get.friendManager == null) {
         return;
      }

      Entity target = event.getTarget();
      if (!(target instanceof PlayerEntity)) {
         return;
      }

      String name = resolveFriendName(target);
      if (name != null && Zero.get.friendManager.isFriend(name)) {
         event.cancel();
      }
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
