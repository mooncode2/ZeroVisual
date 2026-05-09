package ru.zero.rpc;

import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class DiscordUser extends Structure {
   public String userId;
   public String username;
   @Deprecated
   public String discriminator;
   public String avatar;

   protected List<String> getFieldOrder() {
      return Arrays.asList("userId", "username", "discriminator", "avatar");
   }
}
