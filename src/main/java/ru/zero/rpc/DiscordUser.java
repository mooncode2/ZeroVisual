package ru.zero.rpc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class DiscordUser {
   public String userId;
   public String username;
   @Deprecated
   public String discriminator;
   public String avatar;
}
