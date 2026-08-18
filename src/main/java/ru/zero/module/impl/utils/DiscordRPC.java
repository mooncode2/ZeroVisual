package ru.zero.module.impl.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;

@IModule(name = "Discord RPC", description = "Shows your game status in Discord", category = Category.Utils, bind = -1)
@Environment(EnvType.CLIENT)
public class DiscordRPC extends Module {
   public DiscordRPC() {
      this.addSettings(new Setting[0]);
   }

   public void onEnable() {
      super.onEnable();
      if (Zero.get != null && Zero.get.getRpc() != null) {
         Zero.get.getRpc().startRpc();
      }
   }

   public void onDisable() {
      super.onDisable();
      if (Zero.get != null && Zero.get.getRpc() != null) {
         Zero.get.getRpc().stopRpc();
      }
   }

   protected void onConfigLoadEnable() {
      if (Zero.get != null && Zero.get.getRpc() != null) {
         Zero.get.getRpc().startRpc();
      }
   }

   protected void onConfigLoadDisable() {
      if (Zero.get != null && Zero.get.getRpc() != null) {
         Zero.get.getRpc().stopRpc();
      }
   }
}
