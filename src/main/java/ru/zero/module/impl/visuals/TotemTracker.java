package ru.zero.module.impl.visuals;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.BlockPos;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventPacket;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.SliderSetting;

@IModule(
   name = "Totem Tracker",
   description = "Считает потерянные тотемы игроков рядом",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class TotemTracker extends Module {
   private static final byte TOTEM_POP_STATUS = 35;
   private static final Map<UUID, Integer> POP_COUNTS = new ConcurrentHashMap<>();

   public static final SliderSetting radius = new SliderSetting("Радиус", 50.0F, 10.0F, 80.0F, 1.0F, false);

   public TotemTracker() {
      this.addSettings(new Setting[] { radius });
   }

   @Override
   public void onDisable() {
      POP_COUNTS.clear();
   }

   @EventInit
   public void onPacket(EventPacket event) {
      if (!this.enable || event.getType() != EventPacket.Type.RECEIVE) {
         return;
      }

      if (!(event.getPacket() instanceof EntityStatusS2CPacket statusPacket)) {
         return;
      }

      if (statusPacket.getStatus() != TOTEM_POP_STATUS) {
         return;
      }

      MinecraftClient client = mc;
      if (client.world == null || client.player == null) {
         return;
      }

      Entity entity = statusPacket.getEntity(client.world);
      if (!(entity instanceof PlayerEntity player) || player == client.player) {
         return;
      }

      if (!isTrackable(player)) {
         return;
      }

      POP_COUNTS.merge(player.getUuid(), 1, Integer::sum);
   }

   public static int getPopCount(PlayerEntity player) {
      if (player == null || Zero.get == null || Zero.get.manager == null) {
         return 0;
      }

      TotemTracker module = Zero.get.manager.get(TotemTracker.class);
      if (module == null || !module.enable) {
         return 0;
      }

      return POP_COUNTS.getOrDefault(player.getUuid(), 0);
   }

   private boolean isTrackable(PlayerEntity player) {
      MinecraftClient client = mc;
      if (client.player == null || client.world == null) {
         return false;
      }

      if (!client.player.canSee(player)) {
         return false;
      }

      if (player.isInvisible() || player.isSpectator()) {
         return false;
      }

      double radiusSq = radius.get() * radius.get();
      if (client.player.squaredDistanceTo(player) > radiusSq) {
         return false;
      }

      BlockPos playerFeet = client.player.getBlockPos();
      BlockPos targetFeet = player.getBlockPos();
      if (targetFeet.getY() < playerFeet.getY() - 2) {
         return false;
      }

      return true;
   }
}
